package com.zenlauncher.zen.domain.reading

/**
 * Una marca de pagina.
 *
 * Guarda una [ReadingPosition] y **un trozo del texto que hay ahi**. El trozo no es
 * decoracion: una lista de marcas que dijera solo "página 87" obliga a saltar a cada una
 * para saber cual era la que buscabas, que es exactamente el trabajo que la marca venia
 * a ahorrar.
 */
data class Bookmark(
    val id: String,
    val bookId: String,
    val position: ReadingPosition,
    val snippet: String,
    /** La pagina del PDF, para poder decir de donde es. */
    val page: Int,
    val createdAtMillis: Long,
)

/**
 * Que marca cae en una pagina. Puro.
 *
 * Existe para que el boton de marcar tenga **un solo estado verdadero**: si en lo que se
 * esta viendo ya hay una marca, el boton la quita; si no, la pone. Sin esto, marcar dos
 * veces la misma pagina dejaria dos entradas identicas en la lista.
 */
object Bookmarks {

    fun on(page: ReaderPage, bookmarks: List<Bookmark>): Bookmark? {
        if (page.empty) return null
        return bookmarks.firstOrNull { it.position >= page.start && it.position < page.end }
    }
}
