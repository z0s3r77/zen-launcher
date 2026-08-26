package com.zenlauncher.zen.domain.reading

import kotlinx.coroutines.flow.Flow

/** La frontera de persistencia de Lectura. Nada de esto sabe que hay un PDF detras. */
interface BookRepository {

    /** La biblioteca, de lo leido mas recientemente a lo mas antiguo. */
    fun observeBooks(): Flow<List<Book>>

    fun observeBook(id: String): Flow<Book?>

    suspend fun book(id: String): Book?

    /**
     * Guarda un libro entero: la ficha, sus bloques y su indice, de una vez.
     *
     * En una sola transaccion a proposito: un libro a medio escribir —con ficha pero sin
     * texto— apareceria en la biblioteca y al abrirlo estaria en blanco, y no habria
     * forma de distinguirlo de uno bien importado para reintentarlo.
     */
    suspend fun save(book: Book, blocks: List<BookBlock>, chapters: List<BookChapter>)

    /** Todo el texto del libro, en orden. El lector lo pide una vez al abrirlo. */
    suspend fun blocks(bookId: String): List<BookBlock>

    suspend fun chapters(bookId: String): List<BookChapter>

    /** Guardar el sitio de lectura. Se llama a menudo, asi que escribe solo tres campos. */
    suspend fun updateProgress(bookId: String, position: ReadingPosition, readAtMillis: Long)

    suspend fun delete(id: String)

    // --- Lo que el usuario deja escrito encima del libro ---

    /** Las marcas de pagina, en orden de lectura. */
    fun observeBookmarks(bookId: String): Flow<List<Bookmark>>

    suspend fun addBookmark(bookmark: Bookmark)

    suspend fun deleteBookmark(id: String)

    /**
     * Los subrayados, en orden de lectura.
     *
     * Se observan enteros y no por pagina: son unas decenas por libro, y el lector los
     * necesita todos a la vez para poder pintar los que caen en la pagina que se esta
     * mirando sin consultar nada al pasar de hoja.
     */
    fun observeHighlights(bookId: String): Flow<List<Highlight>>

    /** Alta y edicion en la misma puerta: escribir la nota de un subrayado es editarlo. */
    suspend fun putHighlight(highlight: Highlight)

    suspend fun deleteHighlight(id: String)
}
