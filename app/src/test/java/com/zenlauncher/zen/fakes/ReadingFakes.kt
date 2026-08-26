package com.zenlauncher.zen.fakes

import com.zenlauncher.zen.domain.reading.Book
import com.zenlauncher.zen.domain.reading.BookBlock
import com.zenlauncher.zen.domain.reading.BookChapter
import com.zenlauncher.zen.domain.reading.BookCoverStore
import com.zenlauncher.zen.domain.reading.BookRepository
import com.zenlauncher.zen.domain.reading.Bookmark
import com.zenlauncher.zen.domain.reading.Highlight
import com.zenlauncher.zen.domain.reading.ReadingPosition
import com.zenlauncher.zen.domain.reading.PdfDocumentText
import com.zenlauncher.zen.domain.reading.PdfPageText
import com.zenlauncher.zen.domain.reading.PdfTextSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Los dobles de Lectura, aparte de `Fakes.kt` por la misma razon que `FakeUsageRepository`:
 * la mitad de lo que hay que probar aqui es el camino en el que el telefono **no puede**
 * extraer texto o el fichero no se deja leer.
 */
class FakeBookRepository(initial: List<Book> = emptyList()) : BookRepository {

    private val books = MutableStateFlow(initial)
    private val blocks = mutableMapOf<String, List<BookBlock>>()
    private val chapters = mutableMapOf<String, List<BookChapter>>()
    private val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    private val highlights = MutableStateFlow<List<Highlight>>(emptyList())

    /** Cuantas veces se ha escrito el progreso. Es lo que fija que no se escriba de mas. */
    var progressWrites = 0
        private set

    override fun observeBooks(): Flow<List<Book>> = books

    override fun observeBook(id: String): Flow<Book?> = books.map { list -> list.find { it.id == id } }

    override suspend fun book(id: String): Book? = books.value.find { it.id == id }

    override suspend fun save(
        book: Book,
        blocks: List<BookBlock>,
        chapters: List<BookChapter>,
    ) {
        books.value = books.value.filterNot { it.id == book.id } + book
        this.blocks[book.id] = blocks
        this.chapters[book.id] = chapters
    }

    override suspend fun blocks(bookId: String): List<BookBlock> = blocks[bookId].orEmpty()

    override suspend fun chapters(bookId: String): List<BookChapter> = chapters[bookId].orEmpty()

    override suspend fun updateProgress(
        bookId: String,
        position: ReadingPosition,
        readAtMillis: Long,
    ) {
        progressWrites++
        books.value = books.value.map { book ->
            if (book.id == bookId) {
                book.copy(lastPosition = position, lastReadAtMillis = readAtMillis)
            } else {
                book
            }
        }
    }

    override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> =
        bookmarks.map { list -> list.filter { it.bookId == bookId }.sortedBy { it.position } }

    override suspend fun addBookmark(bookmark: Bookmark) {
        bookmarks.value = bookmarks.value.filterNot { it.id == bookmark.id } + bookmark
    }

    override suspend fun deleteBookmark(id: String) {
        bookmarks.value = bookmarks.value.filterNot { it.id == id }
    }

    override fun observeHighlights(bookId: String): Flow<List<Highlight>> =
        highlights.map { list ->
            list.filter { it.bookId == bookId }.sortedBy { it.position }
        }

    override suspend fun putHighlight(highlight: Highlight) {
        highlights.value = highlights.value.filterNot { it.id == highlight.id } + highlight
    }

    override suspend fun deleteHighlight(id: String) {
        highlights.value = highlights.value.filterNot { it.id == id }
    }

    override suspend fun delete(id: String) {
        books.value = books.value.filterNot { it.id == id }
        blocks -= id
        chapters -= id
        // Como la clave ajena en cascada de SQLite: borrar el libro se lleva lo que el
        // usuario dejo escrito encima.
        bookmarks.value = bookmarks.value.filterNot { it.bookId == id }
        highlights.value = highlights.value.filterNot { it.bookId == id }
    }
}

class FakeBookCoverStore : BookCoverStore {

    val deleted = mutableListOf<String>()
    var stored = mutableMapOf<String, ByteArray>()

    override suspend fun store(bookId: String, jpeg: ByteArray): String? {
        stored[bookId] = jpeg
        return "lectura/$bookId/portada.jpg"
    }

    override suspend fun deleteFor(bookId: String) {
        deleted += bookId
        stored -= bookId
    }

    override fun absolutePath(relativePath: String): String = "/datos/$relativePath"
}

/**
 * Un lector de PDF de mentira.
 *
 * [available] en false es el telefono con Android 14, donde `getTextContents` todavia no
 * existe; `pages` vacio o sin letras es el PDF escaneado; `document` a null es el fichero
 * que no se deja abrir.
 */
class FakePdfTextSource(
    private val document: PdfDocumentText? = null,
    override val available: Boolean = true,
    private val cover: ByteArray? = byteArrayOf(1, 2, 3),
) : PdfTextSource {

    var reads = 0
        private set

    override suspend fun read(
        uri: String,
        onProgress: (page: Int, total: Int) -> Unit,
    ): PdfDocumentText? {
        reads++
        document?.pages?.forEachIndexed { index, _ ->
            onProgress(index + 1, document.pages.size)
        }
        return document
    }

    override suspend fun renderCover(uri: String, maxEdgePx: Int): ByteArray? = cover

    companion object {
        /** Un librito con portada, un titulo y un parrafo: lo minimo que es un libro. */
        fun libro(fileName: String = "libro.pdf") = PdfDocumentText(
            pages = listOf(
                PdfPageText(0, "El ser y la nada\nJean-Paul Sartre"),
                PdfPageText(
                    1,
                    "1. El problema del ser\n" +
                        "El ser es y el no ser no es, decia Parmenides, y con esa frase se\n" +
                        "abrio un camino que todavia recorremos hoy en dia sin descanso.\n" +
                        "Fin.",
                ),
            ),
            fileName = fileName,
        )
    }
}
