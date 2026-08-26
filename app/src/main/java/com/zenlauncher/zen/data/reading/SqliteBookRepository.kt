package com.zenlauncher.zen.data.reading

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.zenlauncher.zen.data.db.ZenDatabaseHelper
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_AUTHOR
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_BLOCK_COUNT
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_BLOCK_INDEX
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_BOOK_ID
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_CHAR_OFFSET
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_CREATED_AT
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_END
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_LAST_OFFSET
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_NOTE
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_START
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_COVER_PATH
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_ID
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_IMPORTED_AT
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_KIND
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_LAST_BLOCK
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_LAST_READ_AT
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_LEVEL
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_PAGE
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_PAGE_COUNT
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_SOURCE_URI
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_TITLE
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_VALUE
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.TABLE_BOOKS
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.TABLE_BOOK_BLOCKS
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.TABLE_BOOKMARKS
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.TABLE_BOOK_CHAPTERS
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.TABLE_HIGHLIGHTS
import com.zenlauncher.zen.domain.reading.BlockKind
import com.zenlauncher.zen.domain.reading.Book
import com.zenlauncher.zen.domain.reading.BookBlock
import com.zenlauncher.zen.domain.reading.BookChapter
import com.zenlauncher.zen.domain.reading.Bookmark
import com.zenlauncher.zen.domain.reading.Highlight
import com.zenlauncher.zen.domain.reading.ReadingPosition
import com.zenlauncher.zen.domain.reading.BookRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Los libros, sobre la misma base de datos que las sesiones y las notas.
 *
 * Mismo patron que [com.zenlauncher.zen.data.notes.SqliteNotesRepository]: las lecturas
 * en vivo se reemiten al recibir una invalidacion en lugar de montar observadores de
 * SQLite.
 *
 * Diferencia importante con las notas: **el texto de un libro no se observa**. La
 * biblioteca —cuatro fichas— si; los bloques no, porque son miles y se leen una sola vez
 * al abrir el libro. Un flujo que reemite veinte mil parrafos cada vez que se guarda el
 * progreso seria releer el libro entero cada pocos segundos mientras se lee.
 */
class SqliteBookRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : BookRepository {

    private val helper = ZenDatabaseHelper(context.applicationContext)

    private val invalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    override fun observeBooks(): Flow<List<Book>> = reemitting { books() }

    override fun observeBook(id: String): Flow<Book?> = reemitting { readBook(id) }

    override suspend fun book(id: String): Book? = withContext(io) { readBook(id) }

    override suspend fun save(
        book: Book,
        blocks: List<BookBlock>,
        chapters: List<BookChapter>,
    ) {
        withContext(io) {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                db.insertWithOnConflict(
                    TABLE_BOOKS,
                    null,
                    book.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                // Reimportar el mismo libro sustituye su contenido en lugar de sumarlo:
                // sin esto, volver a importarlo dejaria el texto duplicado y el progreso
                // apuntando a la mitad del libro anterior.
                db.delete(TABLE_BOOK_BLOCKS, "$COLUMN_BOOK_ID = ?", arrayOf(book.id))
                db.delete(TABLE_BOOK_CHAPTERS, "$COLUMN_BOOK_ID = ?", arrayOf(book.id))

                blocks.forEach { block ->
                    db.insertWithOnConflict(
                        TABLE_BOOK_BLOCKS,
                        null,
                        block.toContentValues(book.id),
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
                chapters.forEach { chapter ->
                    db.insertWithOnConflict(
                        TABLE_BOOK_CHAPTERS,
                        null,
                        chapter.toContentValues(book.id),
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            invalidations.tryEmit(Unit)
        }
    }

    override suspend fun blocks(bookId: String): List<BookBlock> = withContext(io) {
        helper.readableDatabase.query(
            TABLE_BOOK_BLOCKS,
            null,
            "$COLUMN_BOOK_ID = ?",
            arrayOf(bookId),
            null,
            null,
            "$COLUMN_BLOCK_INDEX ASC",
        ).use { cursor -> cursor.map { it.toBlock() } }
    }

    override suspend fun chapters(bookId: String): List<BookChapter> = withContext(io) {
        helper.readableDatabase.query(
            TABLE_BOOK_CHAPTERS,
            null,
            "$COLUMN_BOOK_ID = ?",
            arrayOf(bookId),
            null,
            null,
            "$COLUMN_BLOCK_INDEX ASC",
        ).use { cursor -> cursor.map { it.toChapter() } }
    }

    override suspend fun updateProgress(
        bookId: String,
        position: ReadingPosition,
        readAtMillis: Long,
    ) {
        withContext(io) {
            val values = ContentValues().apply {
                put(COLUMN_LAST_BLOCK, position.blockIndex)
                put(COLUMN_LAST_OFFSET, position.charOffset)
                put(COLUMN_LAST_READ_AT, readAtMillis)
            }
            helper.writableDatabase.update(TABLE_BOOKS, values, "$COLUMN_ID = ?", arrayOf(bookId))
            invalidations.tryEmit(Unit)
        }
    }

    override suspend fun delete(id: String) {
        withContext(io) {
            helper.writableDatabase.delete(TABLE_BOOKS, "$COLUMN_ID = ?", arrayOf(id))
            invalidations.tryEmit(Unit)
        }
    }

    // --- Marcas y subrayados ---

    override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> =
        reemitting { bookmarks(bookId) }

    override suspend fun addBookmark(bookmark: Bookmark) {
        withContext(io) {
            helper.writableDatabase.insertWithOnConflict(
                TABLE_BOOKMARKS,
                null,
                bookmark.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            invalidations.tryEmit(Unit)
        }
    }

    override suspend fun deleteBookmark(id: String) {
        withContext(io) {
            helper.writableDatabase.delete(TABLE_BOOKMARKS, "$COLUMN_ID = ?", arrayOf(id))
            invalidations.tryEmit(Unit)
        }
    }

    override fun observeHighlights(bookId: String): Flow<List<Highlight>> =
        reemitting { highlights(bookId) }

    /**
     * Alta y edicion en la misma puerta: escribir la nota de un subrayado que ya existe
     * es editarlo, y separar "crear" de "anotar" obligaria a la pantalla a saber cual de
     * las dos cosas esta haciendo.
     */
    override suspend fun putHighlight(highlight: Highlight) {
        withContext(io) {
            helper.writableDatabase.insertWithOnConflict(
                TABLE_HIGHLIGHTS,
                null,
                highlight.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            invalidations.tryEmit(Unit)
        }
    }

    override suspend fun deleteHighlight(id: String) {
        withContext(io) {
            helper.writableDatabase.delete(TABLE_HIGHLIGHTS, "$COLUMN_ID = ?", arrayOf(id))
            invalidations.tryEmit(Unit)
        }
    }

    private fun bookmarks(bookId: String): List<Bookmark> =
        helper.readableDatabase.query(
            TABLE_BOOKMARKS,
            null,
            "$COLUMN_BOOK_ID = ?",
            arrayOf(bookId),
            null,
            null,
            // En orden de lectura, no por fecha: una lista de marcas es un recorrido del
            // libro, y ordenarla por cuando se puso obliga a reconstruir mentalmente por
            // donde caia cada una.
            "$COLUMN_BLOCK_INDEX ASC, $COLUMN_CHAR_OFFSET ASC",
        ).use { cursor -> cursor.map { it.toBookmark() } }

    private fun highlights(bookId: String): List<Highlight> =
        helper.readableDatabase.query(
            TABLE_HIGHLIGHTS,
            null,
            "$COLUMN_BOOK_ID = ?",
            arrayOf(bookId),
            null,
            null,
            "$COLUMN_BLOCK_INDEX ASC, $COLUMN_START ASC",
        ).use { cursor -> cursor.map { it.toHighlight() } }

    /**
     * Lo ultimo leido arriba, y lo que nunca se abrio detras por fecha de importacion.
     *
     * `last_read_at` es null en un libro recien importado, y en SQL un null se ordena
     * antes que cualquier numero con DESC: sin el COALESCE, importar un libro y no
     * abrirlo lo mandaria al fondo de la biblioteca justo cuando es lo que el usuario
     * acaba de anadir.
     */
    private fun books(): List<Book> =
        helper.readableDatabase.query(
            TABLE_BOOKS,
            null,
            null,
            null,
            null,
            null,
            "COALESCE($COLUMN_LAST_READ_AT, $COLUMN_IMPORTED_AT) DESC",
        ).use { cursor -> cursor.map { it.toBook() } }

    private fun readBook(id: String): Book? =
        helper.readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        ).use { if (it.moveToFirst()) it.toBook() else null }

    private fun <T> reemitting(read: suspend () -> T): Flow<T> =
        invalidations
            .onStart { emit(Unit) }
            .let { source -> flow { source.collect { emit(read()) } } }
            .flowOn(io)

    private fun <T> Cursor.map(read: (Cursor) -> T): List<T> = buildList {
        while (moveToNext()) add(read(this@map))
    }

    private fun Cursor.toBook() = Book(
        id = getString(getColumnIndexOrThrow(COLUMN_ID)),
        title = getString(getColumnIndexOrThrow(COLUMN_TITLE)),
        author = getStringOrNull(COLUMN_AUTHOR),
        sourceUri = getString(getColumnIndexOrThrow(COLUMN_SOURCE_URI)),
        coverPath = getStringOrNull(COLUMN_COVER_PATH),
        pageCount = getInt(getColumnIndexOrThrow(COLUMN_PAGE_COUNT)),
        blockCount = getInt(getColumnIndexOrThrow(COLUMN_BLOCK_COUNT)),
        importedAtMillis = getLong(getColumnIndexOrThrow(COLUMN_IMPORTED_AT)),
        lastReadAtMillis = getColumnIndexOrThrow(COLUMN_LAST_READ_AT).let {
            if (isNull(it)) null else getLong(it)
        },
        lastPosition = ReadingPosition(
            blockIndex = getInt(getColumnIndexOrThrow(COLUMN_LAST_BLOCK)),
            charOffset = getInt(getColumnIndexOrThrow(COLUMN_LAST_OFFSET)),
        ),
    )

    private fun Cursor.toBookmark() = Bookmark(
        id = getString(getColumnIndexOrThrow(COLUMN_ID)),
        bookId = getString(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
        position = ReadingPosition(
            blockIndex = getInt(getColumnIndexOrThrow(COLUMN_BLOCK_INDEX)),
            charOffset = getInt(getColumnIndexOrThrow(COLUMN_CHAR_OFFSET)),
        ),
        snippet = getString(getColumnIndexOrThrow(COLUMN_VALUE)),
        page = getInt(getColumnIndexOrThrow(COLUMN_PAGE)),
        createdAtMillis = getLong(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
    )

    private fun Cursor.toHighlight() = Highlight(
        id = getString(getColumnIndexOrThrow(COLUMN_ID)),
        bookId = getString(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
        blockIndex = getInt(getColumnIndexOrThrow(COLUMN_BLOCK_INDEX)),
        start = getInt(getColumnIndexOrThrow(COLUMN_START)),
        end = getInt(getColumnIndexOrThrow(COLUMN_END)),
        text = getString(getColumnIndexOrThrow(COLUMN_VALUE)),
        note = getStringOrNull(COLUMN_NOTE),
        page = getInt(getColumnIndexOrThrow(COLUMN_PAGE)),
        createdAtMillis = getLong(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
    )

    private fun Cursor.toBlock() = BookBlock(
        index = getInt(getColumnIndexOrThrow(COLUMN_BLOCK_INDEX)),
        // Un tipo desconocido se lee como parrafo en lugar de reventar: si algun dia se
        // anade una clase de bloque y el usuario vuelve a una version anterior, el libro
        // se sigue leyendo entero.
        kind = runCatching {
            BlockKind.valueOf(getString(getColumnIndexOrThrow(COLUMN_KIND)))
        }.getOrDefault(BlockKind.PARAGRAPH),
        text = getString(getColumnIndexOrThrow(COLUMN_VALUE)),
        page = getInt(getColumnIndexOrThrow(COLUMN_PAGE)),
        level = getInt(getColumnIndexOrThrow(COLUMN_LEVEL)),
    )

    private fun Cursor.toChapter() = BookChapter(
        title = getString(getColumnIndexOrThrow(COLUMN_TITLE)),
        level = getInt(getColumnIndexOrThrow(COLUMN_LEVEL)),
        blockIndex = getInt(getColumnIndexOrThrow(COLUMN_BLOCK_INDEX)),
        page = getInt(getColumnIndexOrThrow(COLUMN_PAGE)),
    )

    private fun Cursor.getStringOrNull(column: String): String? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

    private fun Book.toContentValues() = ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_TITLE, title)
        put(COLUMN_AUTHOR, author)
        put(COLUMN_SOURCE_URI, sourceUri)
        put(COLUMN_COVER_PATH, coverPath)
        put(COLUMN_PAGE_COUNT, pageCount)
        put(COLUMN_BLOCK_COUNT, blockCount)
        put(COLUMN_IMPORTED_AT, importedAtMillis)
        put(COLUMN_LAST_READ_AT, lastReadAtMillis)
        put(COLUMN_LAST_BLOCK, lastPosition.blockIndex)
        put(COLUMN_LAST_OFFSET, lastPosition.charOffset)
    }

    private fun Bookmark.toContentValues() = ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_BOOK_ID, bookId)
        put(COLUMN_BLOCK_INDEX, position.blockIndex)
        put(COLUMN_CHAR_OFFSET, position.charOffset)
        put(COLUMN_VALUE, snippet)
        put(COLUMN_PAGE, page)
        put(COLUMN_CREATED_AT, createdAtMillis)
    }

    private fun Highlight.toContentValues() = ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_BOOK_ID, bookId)
        put(COLUMN_BLOCK_INDEX, blockIndex)
        put(COLUMN_START, start)
        put(COLUMN_END, end)
        put(COLUMN_VALUE, text)
        // Una nota en blanco se guarda como null: "solo subrayado" tiene que ser un
        // unico estado, o la pagina pintaria dos cosas distintas para lo mismo.
        put(COLUMN_NOTE, note?.takeIf { it.isNotBlank() })
        put(COLUMN_PAGE, page)
        put(COLUMN_CREATED_AT, createdAtMillis)
    }

    private fun BookBlock.toContentValues(bookId: String) = ContentValues().apply {
        put(COLUMN_BOOK_ID, bookId)
        put(COLUMN_BLOCK_INDEX, index)
        put(COLUMN_KIND, kind.name)
        put(COLUMN_LEVEL, level)
        put(COLUMN_PAGE, page)
        put(COLUMN_VALUE, text)
    }

    private fun BookChapter.toContentValues(bookId: String) = ContentValues().apply {
        put(COLUMN_BOOK_ID, bookId)
        put(COLUMN_BLOCK_INDEX, blockIndex)
        put(COLUMN_TITLE, title)
        put(COLUMN_LEVEL, level)
        put(COLUMN_PAGE, page)
    }
}
