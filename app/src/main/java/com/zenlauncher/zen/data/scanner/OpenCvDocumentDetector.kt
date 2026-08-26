package com.zenlauncher.zen.data.scanner

import android.util.Log
import com.zenlauncher.zen.domain.scanner.Corners
import com.zenlauncher.zen.domain.scanner.Detection
import com.zenlauncher.zen.domain.scanner.DocumentDetector
import com.zenlauncher.zen.domain.scanner.GrayFrame
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.domain.scanner.ScanPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/**
 * Encontrar la hoja con vision por computador clasica.
 *
 * Sin modelo, sin red y sin nada aprendido: gris, ruido fuera, bordes, contornos,
 * poligonos de cuatro vertices y una validacion de forma. Todo lo que decide **si** un
 * cuadrilatero vale vive en el dominio ([Corners]); aqui solo esta lo que mueve pixeles.
 *
 * ### Dos estrategias, y el orden importa
 *
 * 1. **Bordes (Canny).** Es la buena con una hoja sobre una mesa de otro color: el borde
 *    del papel es un salto de brillo y sale limpio.
 * 2. **Umbral de Otsu.** Entra solo si la primera no encuentra nada, y es la que salva el
 *    caso contrario: folio blanco sobre mesa clara, donde no hay salto de brillo pero si
 *    dos poblaciones de gris que Otsu separa sola.
 *
 * Se prueba la barata primero y la segunda solo cuando hace falta porque esto corre
 * quince veces por segundo: hacer siempre las dos seria doblar el gasto para el caso
 * facil, que es la mayoria.
 *
 * ### Umbrales que no son numeros magicos
 *
 * Los dos limites de Canny salen del umbral que calcula Otsu sobre la propia imagen, no
 * de dos constantes. Es lo que hace que funcione igual en una cocina de noche y junto a
 * una ventana: fijar 50 y 150 obliga a elegir una iluminacion y fallar en las demas.
 */
class OpenCvDocumentDetector(
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : DocumentDetector {

    override val available: Boolean get() = OpenCvVision.available

    override fun detect(frame: GrayFrame): Detection? {
        if (!available) return null
        // Se atrapa Throwable: si algo nativo revienta durante el analisis, el hilo de
        // CameraX se lleva por delante el proceso, y el proceso es la pantalla de inicio.
        return runCatchingNative {
            withMats { scope ->
                val upright = frame.toUprightMat()?.let(scope::keep) ?: return@withMats null
                findDocument(scope, upright, PREVIEW_WORKING_EDGE)
            }
        }
    }

    override suspend fun detectInPhoto(jpeg: ByteArray): Detection? = withContext(io) {
        if (!available) return@withContext null
        runCatchingNative {
            withMats { scope ->
                val encoded = scope.keep(MatOfByte(*jpeg))
                // La foto ya viene derecha (ver `DocumentProcessor.upright`), asi que se
                // ignora la etiqueta EXIF a proposito: girarla dos veces la dejaria de
                // lado sin que nada avise.
                val gray = scope.keep(
                    Imgcodecs.imdecode(
                        encoded,
                        Imgcodecs.IMREAD_GRAYSCALE or Imgcodecs.IMREAD_IGNORE_ORIENTATION,
                    ),
                )
                if (gray.empty()) return@withMats null
                findDocument(scope, gray, PHOTO_WORKING_EDGE)
            }
        }
    }

    override fun close() = Unit

    /**
     * El camino entero sobre una imagen en gris ya derecha.
     *
     * Se reduce **antes** de mirar nada. No es solo velocidad: a resolucion completa el
     * grano del papel y la trama del texto son bordes tan validos como el canto de la
     * hoja, y el trazado de contornos se pierde entre ellos. Reducir es el filtro paso
     * bajo mas barato que hay.
     */
    private fun findDocument(scope: MatScope, gray: Mat, workingEdge: Int): Detection? {
        val imageAspect = gray.cols().toFloat() / gray.rows()

        val small = scope.mat()
        val longest = maxOf(gray.cols(), gray.rows())
        if (longest > workingEdge) {
            val scale = workingEdge.toDouble() / longest
            // INTER_AREA y no INTER_LINEAR: al reducir es el unico que promedia todos los
            // pixeles de origen, y por eso no deja escalones que luego pasan por bordes.
            Imgproc.resize(gray, small, Size(), scale, scale, Imgproc.INTER_AREA)
        } else {
            gray.copyTo(small)
        }

        val blurred = scope.mat()
        // Bilateral y no gaussiano: suaviza el ruido del sensor **conservando** el canto
        // de la hoja, que es justo lo unico que hay que encontrar. Un gaussiano bastante
        // ancho para quitar el grano se lleva por delante el borde de un folio blanco
        // sobre una mesa clara, que es el caso dificil.
        Imgproc.bilateralFilter(small, blurred, BILATERAL_DIAMETER, BILATERAL_SIGMA, BILATERAL_SIGMA)

        val quad = detectByEdges(scope, blurred) ?: detectByThreshold(scope, blurred)
        return quad?.let { Detection(quad = it, imageAspect = imageAspect) }
    }

    /** Estrategia 1: el canto de la hoja como salto de brillo. */
    private fun detectByEdges(scope: MatScope, blurred: Mat): Quad? {
        val discard = scope.mat()
        // Otsu no se usa para binarizar aqui: se usa por el **valor** que devuelve, que
        // es el corte optimo entre las dos poblaciones de gris de esta imagen concreta.
        val otsu = Imgproc.threshold(
            blurred,
            discard,
            0.0,
            255.0,
            Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU,
        )

        val edges = scope.mat()
        Imgproc.Canny(blurred, edges, otsu * CANNY_LOW_RATIO, otsu, CANNY_APERTURE, false)

        // Un borde real se corta donde la sombra lo tapa o donde el papel se arruga. Sin
        // cerrar esos huecos, el trazado devuelve cuatro trozos de linea en lugar de un
        // contorno, y ningun trozo tiene cuatro vertices.
        val kernel = scope.keep(
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(CLOSE_KERNEL, CLOSE_KERNEL)),
        )
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)

        return largestQuad(scope, edges)
    }

    /**
     * Estrategia 2: separar papel y fondo por brillo.
     *
     * Para el folio blanco sobre la mesa clara, donde el canto casi no marca borde. Se
     * binariza con Otsu y se busca el contorno de la mancha clara.
     */
    private fun detectByThreshold(scope: MatScope, blurred: Mat): Quad? {
        val binary = scope.mat()
        Imgproc.threshold(blurred, binary, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        // El papel puede salir como la mancha oscura si el fondo es mas claro que el:
        // se prueban las dos polaridades y se queda la que da un cuadrilatero mayor.
        val inverted = scope.mat()
        Core.bitwise_not(binary, inverted)

        val kernel = scope.keep(
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(CLOSE_KERNEL, CLOSE_KERNEL)),
        )
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.morphologyEx(inverted, inverted, Imgproc.MORPH_CLOSE, kernel)

        val fromBright = largestQuad(scope, binary)
        val fromDark = largestQuad(scope, inverted)

        return listOfNotNull(fromBright, fromDark).maxByOrNull { it.areaFraction }
    }

    /**
     * De una imagen binaria al mejor cuadrilatero que haya dentro.
     *
     * Se miran los contornos mas grandes y **no solo el mayor**: el mayor es a menudo el
     * marco de la propia imagen cuando el cierre morfologico unio los bordes del frame,
     * y el documento es el segundo. Se quedan los que se parecen a una hoja y de esos, el
     * que mas ocupa.
     */
    private fun largestQuad(scope: MatScope, binary: Mat): Quad? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = scope.mat()
        Imgproc.findContours(
            binary,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE,
        )
        if (contours.isEmpty()) return null

        val width = binary.cols()
        val height = binary.rows()

        val best = try {
            contours
                .sortedByDescending { Imgproc.contourArea(it) }
                .take(CANDIDATE_CONTOURS)
                .mapNotNull { contour -> approximateToQuad(contour, width, height) }
                .filter { Corners.plausible(it) }
                .maxByOrNull { it.areaFraction }
        } finally {
            contours.forEach { runCatching { it.release() } }
        }
        return best
    }

    /**
     * Reduce un contorno a cuatro vertices, subiendo la tolerancia hasta conseguirlo.
     *
     * Un solo epsilon no vale: con 0,02 del perimetro, una hoja con una esquina doblada
     * sale con cinco o seis vertices y se descarta; con 0,08 fijo, un contorno redondeado
     * pasa por cuadrilatero. Se empieza fino y se va aflojando, y se toma **el primero**
     * que da cuatro, que es el mas parecido al contorno real.
     */
    private fun approximateToQuad(contour: MatOfPoint, width: Int, height: Int): Quad? {
        val curve = MatOfPoint2f(*contour.toArray())
        val approximation = MatOfPoint2f()
        try {
            val perimeter = Imgproc.arcLength(curve, true)
            if (perimeter <= 0.0) return null

            var epsilon = EPSILON_START
            while (epsilon <= EPSILON_LIMIT) {
                Imgproc.approxPolyDP(curve, approximation, epsilon * perimeter, true)
                val points = approximation.toArray()
                if (points.size == 4) {
                    return Corners.order(points.map { it.normalized(width, height) })
                }
                epsilon += EPSILON_STEP
            }
            return null
        } finally {
            curve.release()
            approximation.release()
        }
    }

    /**
     * El plano de luminancia, ya girado para que se vea derecho.
     *
     * Se gira **aqui** y no fuera para que el cuadrilatero que sale de la deteccion este
     * en las mismas coordenadas que lo que el usuario ve en la pantalla: dibujar el marco
     * sobre la vista previa no tiene entonces ninguna conversion, y una conversion de
     * orientacion que se olvida en un sitio da un marco girado noventa grados.
     */
    private fun GrayFrame.toUprightMat(): Mat? {
        if (width <= 0 || height <= 0) return null
        val raw = Mat(height, width, CvType.CV_8UC1)
        // El plano casi nunca es compacto: la camara alinea cada fila a un multiplo, asi
        // que hay que copiar fila a fila. Volcarlo del tiron da una imagen inclinada,
        // porque cada fila empieza unos bytes mas alla de donde deberia.
        if (rowStride == width && luma.size >= width * height) {
            raw.put(0, 0, luma)
        } else {
            for (row in 0 until height) {
                val offset = row * rowStride
                if (offset + width > luma.size) break
                raw.put(row, 0, luma, offset, width)
            }
        }

        val rotation = when (((rotationDegrees % 360) + 360) % 360) {
            90 -> Core.ROTATE_90_CLOCKWISE
            180 -> Core.ROTATE_180
            270 -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> return raw
        }
        val rotated = Mat()
        Core.rotate(raw, rotated, rotation)
        raw.release()
        return rotated
    }

    private fun Point.normalized(width: Int, height: Int): ScanPoint =
        ScanPoint.fromPixels(x.toFloat(), y.toFloat(), width, height)

    private inline fun <T> runCatchingNative(block: () -> T?): T? = try {
        block()
    } catch (error: Throwable) {
        Log.w(TAG, "La deteccion fallo; se sigue sin marco", error)
        null
    }

    private companion object {
        const val TAG = "ZenScanner"

        /**
         * Lado largo al que se reduce un frame de la vista previa.
         *
         * 480 px es donde el canto de un folio a medio metro sigue midiendo varios pixeles
         * y el grano del papel ya no. Subirlo no encuentra mas hojas: encuentra mas
         * bordes que no son la hoja, y ademas cuesta el cuadrado.
         */
        const val PREVIEW_WORKING_EDGE = 480

        /** En la foto ya tomada se puede gastar mas: solo pasa una vez por captura. */
        const val PHOTO_WORKING_EDGE = 1024

        const val BILATERAL_DIAMETER = 7
        const val BILATERAL_SIGMA = 45.0

        /** El limite bajo de Canny como fraccion del alto. Proporcion clasica 1:2. */
        const val CANNY_LOW_RATIO = 0.5
        const val CANNY_APERTURE = 3

        const val CLOSE_KERNEL = 5.0

        /** Cuantos contornos se examinan. Ver [largestQuad]. */
        const val CANDIDATE_CONTOURS = 6

        const val EPSILON_START = 0.015
        const val EPSILON_STEP = 0.010
        const val EPSILON_LIMIT = 0.085
    }
}
