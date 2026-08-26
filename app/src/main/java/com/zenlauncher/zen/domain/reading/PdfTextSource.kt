package com.zenlauncher.zen.domain.reading

/** El texto crudo de una pagina, tal y como lo entrega el lector de PDF. */
data class PdfPageText(val page: Int, val text: String)

/**
 * Todo lo que se puede sacar de un PDF antes de entender nada.
 *
 * @param fileName el nombre del fichero elegido. Es el ultimo recurso para titular un
 *   libro cuyo texto no diga como se llama, y en la practica acierta a menudo porque el
 *   usuario ya renombro sus apuntes.
 */
data class PdfDocumentText(
    val pages: List<PdfPageText>,
    val fileName: String,
)

/**
 * La frontera con Android para leer un PDF. **Aqui es donde entraria el OCR.**
 *
 * La implementacion de v1 (`AndroidPdfTextSource`) usa la extraccion nativa del sistema
 * y por tanto solo ve los PDF que ya llevan texto dentro: un libro escaneado devuelve
 * paginas vacias y la importacion lo dice con esas palabras en lugar de crear un libro
 * de cero parrafos. Un dia que haya OCR local, es **otra implementacion de esta misma
 * interfaz** —o un envoltorio que rellene las paginas vacias— y ni el analisis
 * estructural ni el lector se enteran: los dos hablan de [PdfPageText], no de PDF.
 *
 * Ningun metodo lanza: un fichero corrupto o un proveedor que no deja abrirlo devuelven
 * null, igual que la lectura del tiempo. Zen es la pantalla de inicio del telefono y
 * una excepcion aqui la deja sin arrancar.
 */
interface PdfTextSource {

    /**
     * null si el documento no se puede abrir o el dispositivo no sabe extraer texto.
     *
     * @param onProgress se llama con la pagina que se acaba de leer y el total. Un libro
     *   de 400 paginas tarda, y una pantalla que solo dice "procesando" durante medio
     *   minuto no se distingue de una que se ha colgado.
     */
    suspend fun read(
        uri: String,
        onProgress: (page: Int, total: Int) -> Unit = { _, _ -> },
    ): PdfDocumentText?

    /**
     * La primera pagina rasterizada en JPEG, para la portada de la biblioteca.
     *
     * Devuelve los bytes y no un fichero: quien decide donde se guardan las cosas es la
     * capa de datos, no el lector de PDF.
     */
    suspend fun renderCover(uri: String, maxEdgePx: Int): ByteArray?

    /**
     * Si este dispositivo puede extraer texto de un PDF.
     *
     * `PdfRenderer.Page.getTextContents()` llego en Android 15 (API 35) y `minSdk` es
     * 34, asi que hay un escalon en el que Zen instala y Lectura no puede funcionar. Se
     * pregunta **antes** de abrir el selector de documentos: hacer elegir un fichero
     * para luego decir que no, es peor que no ofrecerlo.
     */
    val available: Boolean
}
