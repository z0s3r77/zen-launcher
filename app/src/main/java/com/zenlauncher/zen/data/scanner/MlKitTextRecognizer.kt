package com.zenlauncher.zen.data.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.zenlauncher.zen.domain.scanner.RecognizedText
import com.zenlauncher.zen.domain.scanner.RecognizedWord
import com.zenlauncher.zen.domain.scanner.TextRecognizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Reconocimiento de texto con ML Kit y el modelo **dentro del APK**.
 *
 * Escritura latina, que es la del castellano: ML Kit no separa por idioma en este modelo
 * sino por sistema de escritura, asi que el mismo reconocedor lee castellano, gallego,
 * catalan e ingles sin elegir nada ni descargar nada.
 *
 * ### Sin Google Play Services por medio
 *
 * `com.google.mlkit:text-recognition` empaqueta el modelo en los assets. Los artefactos
 * de `play-services` que arrastra son la biblioteca de tareas y el envoltorio de la API,
 * no el modelo: en un telefono recien estrenado y sin red esto funciona igual. Es la
 * misma regla que el dictado de Notas, que usa el reconocedor de voz del dispositivo, y
 * la razon de que Zen siga teniendo **dos** consumidores de `INTERNET` y no tres.
 *
 * ### Por que no hay `kotlinx-coroutines-play-services`
 *
 * Seria una dependencia mas para traducir un `Task` a una corrutina, y eso son quince
 * lineas de `suspendCancellableCoroutine`. Es la misma cuenta que llevo a no traer un
 * framework de inyeccion: ver `ZenContainer`.
 */
class MlKitTextRecognizer(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : TextRecognizer {

    private val appContext = context.applicationContext

    /**
     * El cliente de ML Kit, **reconstruible**.
     *
     * Era un `by lazy`, y eso lo hacia irrecuperable: este objeto vive en el contenedor,
     * o sea que es uno solo para todo el proceso, y `ScannerViewModel.onCleared` lo
     * cierra al salir del escaner. Con `by lazy`, cerrado una vez quedaba cerrado para
     * siempre pero seguia siendo no nulo, asi que [available] respondia `true`, el boton
     * de OCR se pintaba y la **segunda** visita al escaner llamaba a `process` sobre un
     * detector cerrado: `IllegalStateException` sincrona, sin capturar, y el proceso del
     * launcher muerto. Un launcher no puede morir por pedir OCR dos veces.
     *
     * Ahora [close] lo pone a null y la siguiente lectura lo vuelve a crear. Sigue siendo
     * perezoso —quien escanea sin pedir OCR no carga el modelo— y ademas es reversible.
     *
     * `@Volatile` mas `synchronized`: se crea desde `Dispatchers.Default` (ver [read]) y
     * se cierra desde el hilo principal (`onCleared`).
     */
    @Volatile
    private var client: com.google.mlkit.vision.text.TextRecognizer? = null

    private fun client(): com.google.mlkit.vision.text.TextRecognizer? =
        client ?: synchronized(this) {
            client ?: runCatching {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
                .onFailure { Log.w(TAG, "ML Kit no pudo crear el reconocedor", it) }
                .getOrNull()
                .also { client = it }
        }

    /**
     * Si el dispositivo puede reconocer texto.
     *
     * Ya no construye el cliente para responder. Antes lo hacia —era `client != null`— y
     * `ScannerViewModel` lo consulta al construirse, asi que abrir el escaner cargaba el
     * modelo de OCR aunque nadie fuera a pedirlo, justo lo contrario de lo que decia el
     * comentario de arriba. ML Kit con el modelo empaquetado esta siempre disponible; lo
     * que puede fallar es crear el cliente, y eso lo maneja [read] devolviendo null.
     */
    override val available: Boolean get() = true

    override suspend fun read(imagePath: String): RecognizedText? = withContext(io) {
        val recognizer = client() ?: return@withContext null
        val image = runCatching {
            InputImage.fromFilePath(appContext, Uri.fromFile(File(imagePath)))
        }.getOrElse {
            Log.w(TAG, "No se pudo leer la imagen para el OCR", it)
            return@withContext null
        }

        val recognized = suspendCancellableCoroutine<Text?> { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { error ->
                    // Quedarse sin texto no puede llevarse por delante el escaneo: el
                    // documento ya esta guardado y esto era un extra.
                    Log.w(TAG, "El OCR fallo", error)
                    continuation.resume(null)
                }
        } ?: return@withContext null

        // Las cajas vienen en pixeles de la imagen leida. Se normalizan aqui, contra el
        // tamano que la propia InputImage reporta, para que el PDF pueda colocarlas sobre
        // una hoja medida en puntos sin arrastrar la resolucion de la foto.
        RecognizedText(
            text = recognized.text,
            words = recognized.toWords(image.width, image.height),
        )
    }

    /**
     * Suelta el modelo **y la referencia**. Sin poner el campo a null, el objeto quedaba
     * inservible para siempre: ver [client].
     */
    override fun close() {
        synchronized(this) {
            runCatching { client?.close() }
            client = null
        }
    }

    /**
     * Se baja hasta el **elemento**, que en escritura latina es la palabra.
     *
     * Ni el bloque ni la linea valen para la capa seleccionable: una linea entera en una
     * sola caja hace que al arrastrar el dedo se seleccione la frase completa en lugar de
     * las tres palabras que se querian, y en un parrafo justificado la caja de la linea
     * ni siquiera coincide con donde estan las letras.
     */
    private fun Text.toWords(width: Int, height: Int): List<RecognizedWord> {
        if (width <= 0 || height <= 0) return emptyList()
        val words = ArrayList<RecognizedWord>()
        for (block in textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    val text = element.text
                    if (text.isBlank()) continue
                    words += RecognizedWord(
                        text = text,
                        left = box.left.toFloat() / width,
                        top = box.top.toFloat() / height,
                        right = box.right.toFloat() / width,
                        bottom = box.bottom.toFloat() / height,
                    )
                }
            }
        }
        return words
    }

    private companion object {
        const val TAG = "ZenScanner"
    }
}
