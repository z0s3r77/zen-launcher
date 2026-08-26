package com.zenlauncher.zen.domain.scanner

/**
 * Una palabra reconocida, con su sitio en la pagina en coordenadas normalizadas.
 *
 * Se guarda la posicion y no solo el texto porque es lo que permite el PDF con capa de
 * texto seleccionable: el visor pinta la imagen y, encima y en tinta invisible, cada
 * palabra en su recuadro. Sin las posiciones solo se podria adjuntar el texto suelto al
 * final, que no se puede seleccionar sobre lo que se esta mirando.
 */
data class RecognizedWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** El texto de una pagina: corrido para copiar, y por palabras para el PDF. */
data class RecognizedText(
    val text: String,
    val words: List<RecognizedWord>,
) {
    val empty: Boolean get() = text.isBlank()
}

/**
 * Leer lo que pone en la hoja. Frontera con ML Kit.
 *
 * El modelo va **dentro del APK**, no descargado por Google Play Services: en un
 * telefono recien estrenado y sin red el OCR funciona igual, que es la misma regla por la
 * que el dictado usa el reconocedor del propio dispositivo y Lectura no necesita
 * conexion. Zen no gana un tercer consumidor de `INTERNET` por esto.
 *
 * Se ejecuta **despues** de enderezar y filtrar, no sobre la foto original: el
 * reconocimiento mejora mucho con el texto ya recto y el fondo ya blanco, y hacerlo antes
 * seria pedirle al modelo que resuelva la perspectiva por su cuenta.
 */
interface TextRecognizer {

    /** Si este telefono trae el reconocedor. Sin el, la fila de OCR no se pinta. */
    val available: Boolean

    /**
     * Lee la imagen de un fichero. Devuelve null si falla, nunca lanza: el documento ya
     * esta guardado y quedarse sin texto no puede llevarse por delante el escaneo.
     */
    suspend fun read(imagePath: String): RecognizedText?

    fun close()
}
