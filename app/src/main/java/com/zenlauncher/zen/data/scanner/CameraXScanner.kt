package com.zenlauncher.zen.data.scanner

import android.content.Context
import android.graphics.ImageFormat
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.zenlauncher.zen.domain.scanner.GrayFrame
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Lo que devuelve una captura: el JPEG y cuanto hay que girarlo para verlo derecho. */
class CapturedPhoto(val jpeg: ByteArray, val rotationDegrees: Int)

/**
 * La camara del escaner, con CameraX.
 *
 * CameraX y no Camera2 a pelo porque lo que Camera2 obliga a escribir a mano —el ciclo de
 * vida de la sesion, la cola de peticiones, la orientacion del sensor y la de la
 * pantalla, el emparejado de casos de uso por dispositivo— es exactamente lo que hace
 * falta aqui y nada mas. Son tres casos de uso a la vez: vista previa, analisis de frames
 * y captura a resolucion completa.
 *
 * ### Dos resoluciones a proposito
 *
 * El analisis pide **640x480** y la captura pide **la mayor disponible**. Es la decision
 * de rendimiento que sostiene todo lo demas: detectar sobre el frame de la vista previa a
 * 12 megapixeles seria mover 48 MB por frame para tirarlos, y capturar a 640x480 daria un
 * documento ilegible. Lo caro se hace una vez, cuando se dispara.
 *
 * El analisis ademas se limita por tiempo a unos quince frames por segundo: la mano no se
 * mueve mas rapido que eso y cada frame de mas es bateria del telefono que sostiene la
 * pantalla de inicio.
 */
class CameraXScanner(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Un hilo propio para el analisis, no el principal.
     *
     * Es lo unico que evita que la deteccion —bordes, contornos, cuadrilateros— corra
     * donde se compone la interfaz. En un launcher eso no es un tiron: es la pantalla de
     * inicio que deja de responder al dedo.
     */
    private var analysisExecutor: ExecutorService? = null

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var analysis: ImageAnalysis? = null

    private var lastAnalysisMillis = 0L

    val hasFlash: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    /**
     * Enciende la camara y engancha el analisis.
     *
     * @return false si no hay camara, esta ocupada o el proveedor no arranco. Nunca lanza:
     *   la pantalla lo traduce a un rotulo y ofrece salir.
     */
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        onFrame: (GrayFrame) -> Unit,
    ): Boolean {
        val cameraProvider = awaitProvider() ?: return false
        provider = cameraProvider

        return try {
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(surfaceProvider)
            }

            val analysisSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(analysisSelector)
                // Quedarse con el ultimo y tirar los de en medio. La alternativa
                // (BLOCK_PRODUCER) encola frames viejos: el marco se dibujaria donde
                // estaba la hoja hace medio segundo, que es peor que no dibujarlo.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            val executor = Executors.newSingleThreadExecutor()
            analysisExecutor = executor
            imageAnalysis.setAnalyzer(executor) { image -> analyze(image, onFrame) }

            val capture = ImageCapture.Builder()
                // Calidad y no latencia: se dispara solo cuando el movil ya esta quieto,
                // asi que los milisegundos de mas no se notan y el detalle si.
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()

            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
                capture,
            )
            imageCapture = capture
            analysis = imageAnalysis
            true
        } catch (error: Throwable) {
            Log.w(TAG, "No se pudo abrir la camara", error)
            unbind()
            false
        }
    }

    /**
     * Toma la foto. Devuelve null si falla, nunca lanza.
     *
     * El JPEG se lee del `ImageProxy` y **el proxy se cierra siempre**: cada uno retiene un
     * buffer del sistema, y dejarse uno sin cerrar deja la camara sin sitio para la
     * siguiente foto sin ningun error visible.
     */
    suspend fun capture(): CapturedPhoto? {
        val capture = imageCapture ?: return null
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                ContextCompat.getMainExecutor(appContext),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val photo = try {
                            val buffer = image.planes.firstOrNull()?.buffer
                            if (buffer == null) {
                                null
                            } else {
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                CapturedPhoto(bytes, image.imageInfo.rotationDegrees)
                            }
                        } catch (error: Throwable) {
                            Log.w(TAG, "No se pudo leer la foto", error)
                            null
                        } finally {
                            image.close()
                        }
                        if (continuation.isActive) continuation.resume(photo)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w(TAG, "La captura fallo", exception)
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }
    }

    /**
     * Enfoca y mide la luz donde se ha tocado.
     *
     * Los dos a la vez, no solo el enfoque: sobre una hoja blanca la medicion automatica
     * de toda la escena deja el papel gris, y tocar la hoja es la forma de decirle a la
     * camara que lo que importa es eso. Se deja que caduque sola para que el automatico
     * vuelva a mandar en cuanto el usuario mueva el movil.
     */
    fun focusAt(x: Float, y: Float, width: Float, height: Float) {
        val control = camera?.cameraControl ?: return
        if (width <= 0f || height <= 0f) return
        runCatching {
            val factory = SurfaceOrientedMeteringPointFactory(width, height)
            val point = factory.createPoint(x, y)
            control.startFocusAndMetering(
                FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(FOCUS_HOLD_SECONDS, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }

    /** La linterna, para escanear de noche. Solo se ofrece si el telefono la tiene. */
    fun setTorch(enabled: Boolean) {
        runCatching { camera?.cameraControl?.enableTorch(enabled) }
    }

    /**
     * Sube o baja la exposicion, en pasos del propio dispositivo.
     *
     * Existe por un caso concreto: un folio blanco llena el encuadre y la camara lo mide
     * como si fuera gris medio, dejando el documento oscuro y con la tinta apagada.
     */
    fun nudgeExposure(steps: Int) {
        val control = camera?.cameraControl ?: return
        val range = camera?.cameraInfo?.exposureState?.exposureCompensationRange ?: return
        val current = camera?.cameraInfo?.exposureState?.exposureCompensationIndex ?: 0
        val target = (current + steps).coerceIn(range.lower, range.upper)
        runCatching { control.setExposureCompensationIndex(target) }
    }

    fun unbind() {
        runCatching { analysis?.clearAnalyzer() }
        runCatching { provider?.unbindAll() }
        analysisExecutor?.shutdown()
        analysisExecutor = null
        analysis = null
        imageCapture = null
        camera = null
        provider = null
    }

    /**
     * Un frame: se limita por tiempo, se copia la luminancia y **se cierra siempre**.
     *
     * Cerrar el proxy es obligatorio incluso cuando el frame se descarta por el limite de
     * tiempo: con `STRATEGY_KEEP_ONLY_LATEST` la camara no entrega el siguiente hasta que
     * se suelta el anterior, asi que un `return` sin cerrar congela la vista previa.
     */
    private fun analyze(image: ImageProxy, onFrame: (GrayFrame) -> Unit) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastAnalysisMillis < MIN_FRAME_INTERVAL_MILLIS) return
            lastAnalysisMillis = now

            if (image.format != ImageFormat.YUV_420_888) return
            val plane = image.planes.firstOrNull() ?: return

            val buffer = plane.buffer
            val luma = ByteArray(buffer.remaining())
            buffer.get(luma)

            onFrame(
                GrayFrame(
                    luma = luma,
                    width = image.width,
                    height = image.height,
                    rowStride = plane.rowStride,
                    rotationDegrees = image.imageInfo.rotationDegrees,
                ),
            )
        } catch (error: Throwable) {
            // Este hilo es de CameraX: una excepcion que se escape mata el proceso, y el
            // proceso es la pantalla de inicio del telefono.
            Log.w(TAG, "Un frame no se pudo analizar", error)
        } finally {
            image.close()
        }
    }

    private suspend fun awaitProvider(): ProcessCameraProvider? =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(appContext)
            future.addListener(
                {
                    val result = runCatching { future.get() }
                        .onFailure { Log.w(TAG, "No hay proveedor de camara", it) }
                        .getOrNull()
                    if (continuation.isActive) continuation.resume(result)
                },
                ContextCompat.getMainExecutor(appContext),
            )
        }

    private companion object {
        const val TAG = "ZenScanner"

        const val ANALYSIS_WIDTH = 640
        const val ANALYSIS_HEIGHT = 480

        /** Unos quince frames por segundo. Ver la nota de la clase. */
        const val MIN_FRAME_INTERVAL_MILLIS = 66L

        const val FOCUS_HOLD_SECONDS = 4L
    }
}
