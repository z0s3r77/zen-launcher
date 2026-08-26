package com.zenlauncher.zen.domain.scanner

/**
 * Un frame de la camara reducido a luminancia.
 *
 * Es lo que sale del plano Y de un YUV_420_888 sin convertir nada: la deteccion de
 * bordes solo mira brillo, asi que pasar por RGB seria decodificar color para tirarlo
 * a la linea siguiente. `rowStride` viaja aparte porque el plano casi nunca es compacto
 * —la camara alinea cada fila— y leerlo como si lo fuera da una imagen inclinada.
 */
class GrayFrame(
    val luma: ByteArray,
    val width: Int,
    val height: Int,
    val rowStride: Int,
    /** Grados a girar en sentido horario para que el frame se vea derecho. */
    val rotationDegrees: Int,
)

/**
 * Una hoja encontrada, ya en el sistema de coordenadas de lo que se ve en pantalla.
 *
 * @param imageAspect ancho entre alto **despues** de girar. Va aqui y no se calcula
 *   fuera porque con el movil en vertical la camara entrega el frame tumbado, y usar la
 *   proporcion sin girar deforma la estimacion de [DocumentAspect].
 */
data class Detection(val quad: Quad, val imageAspect: Float)

/**
 * Encontrar la hoja. Es la frontera con OpenCV.
 *
 * La interfaz vive en el dominio y la implementacion en `data/scanner`, igual que
 * `PdfTextSource` y `AndroidPdfTextSource`: aqui no entra nada nativo, y la politica de
 * cuando disparar ([CaptureDecision]) habla con esta interfaz, no con OpenCV.
 *
 * **Ninguno de los dos metodos lanza.** Devuelven null cuando no hay hoja o cuando la
 * libreria nativa no esta: esto corre en el proceso del launcher y una excepcion desde
 * un hilo de analisis deja el telefono sin pantalla de inicio.
 */
interface DocumentDetector {

    /** Si la vision por computador esta disponible en este telefono. */
    val available: Boolean

    /**
     * Busca la hoja en un frame de la vista previa. Se llama muchas veces por segundo,
     * asi que la implementacion reduce el frame antes de mirarlo.
     */
    fun detect(frame: GrayFrame): Detection?

    /**
     * Busca la hoja en una foto ya tomada, a resolucion completa.
     *
     * Es un camino distinto del anterior y no una variante: aqui se puede gastar tiempo,
     * porque solo pasa una vez por captura y el usuario ya esta esperando.
     */
    suspend fun detectInPhoto(jpeg: ByteArray): Detection?

    /** Suelta lo nativo. Se llama al salir de la pantalla. */
    fun close()
}
