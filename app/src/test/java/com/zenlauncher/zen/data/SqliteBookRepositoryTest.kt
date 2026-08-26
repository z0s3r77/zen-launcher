package com.zenlauncher.zen.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zenlauncher.zen.data.reading.SqliteBookRepository
import com.zenlauncher.zen.domain.reading.BlockKind
import com.zenlauncher.zen.domain.reading.Book
import com.zenlauncher.zen.domain.reading.BookBlock
import com.zenlauncher.zen.domain.reading.BookChapter
import com.zenlauncher.zen.domain.reading.Bookmark
import com.zenlauncher.zen.domain.reading.Highlight
import com.zenlauncher.zen.domain.reading.ReadingPosition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SqliteBookRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var books: SqliteBookRepository

    @Before
    fun setUp() {
        books = SqliteBookRepository(context, UnconfinedTestDispatcher())
    }

    private fun book(
        id: String,
        title: String = "El ser y la nada",
        importedAt: Long = 1_000L,
        lastReadAt: Long? = null,
        blockCount: Int = 3,
    ) = Book(
        id = id,
        title = title,
        author = "Jean-Paul Sartre",
        sourceUri = "content://documentos/$id",
        coverPath = "lectura/$id/portada.jpg",
        pageCount = 342,
        blockCount = blockCount,
        importedAtMillis = importedAt,
        lastReadAtMillis = lastReadAt,
    )

    private val bloques = listOf(
        BookBlock(0, BlockKind.HEADING, "1. El problema del ser", page = 12, level = 1),
        BookBlock(1, BlockKind.PARAGRAPH, "El ser es y el no ser no es.", page = 12),
        BookBlock(2, BlockKind.PARAGRAPH, "Decia Parmenides.", page = 13),
    )

    private val capitulos = listOf(
        BookChapter("1. El problema del ser", level = 1, blockIndex = 0, page = 12),
    )

    @Test
    fun `guarda y recupera un libro con su texto y su indice`() = runTest {
        books.save(book("uno"), bloques, capitulos)

        val recuperado = books.book("uno")!!
        assertEquals("El ser y la nada", recuperado.title)
        assertEquals("Jean-Paul Sartre", recuperado.author)
        assertEquals(342, recuperado.pageCount)
        assertEquals(bloques, books.blocks("uno"))
        assertEquals(capitulos, books.chapters("uno"))
    }

    @Test
    fun `el texto vuelve en orden de lectura`() = runTest {
        books.save(book("uno"), bloques.reversed(), capitulos)

        assertEquals(listOf(0, 1, 2), books.blocks("uno").map { it.index })
    }

    @Test
    fun `un libro sin autor ni portada se guarda igual`() = runTest {
        books.save(book("uno").copy(author = null, coverPath = null), bloques, emptyList())

        val recuperado = books.book("uno")!!
        assertNull(recuperado.author)
        assertNull(recuperado.coverPath)
        assertNull(recuperado.lastReadAtMillis)
    }

    @Test
    fun `guardar el progreso solo toca el sitio y la fecha`() = runTest {
        books.save(book("uno"), bloques, capitulos)

        books.updateProgress("uno", ReadingPosition(blockIndex = 2, charOffset = 140), 5_000L)

        val recuperado = books.book("uno")!!
        // El desplazamiento dentro del parrafo se guarda igual que el bloque: pasando
        // pagina, un parrafo largo se parte y "por donde ibas" es un punto dentro de el.
        assertEquals(ReadingPosition(2, 140), recuperado.lastPosition)
        assertEquals(5_000L, recuperado.lastReadAtMillis)
        assertEquals("El ser y la nada", recuperado.title)
        assertEquals(3, books.blocks("uno").size)
    }

    /**
     * Un libro recien importado no tiene fecha de lectura, y en SQL un null se ordena
     * antes que cualquier numero con DESC: sin el COALESCE, importar un libro lo mandaria
     * al fondo de la biblioteca justo cuando es lo que el usuario acaba de anadir.
     */
    @Test
    fun `lo ultimo leido va primero, y lo recien importado cuenta como reciente`() = runTest {
        books.save(book("viejo", importedAt = 100L, lastReadAt = 200L), emptyList(), emptyList())
        books.save(book("nuevo", importedAt = 900L, lastReadAt = null), emptyList(), emptyList())

        assertEquals(listOf("nuevo", "viejo"), books.observeBooks().first().map { it.id })

        books.updateProgress("viejo", ReadingPosition(1), 1_500L)

        assertEquals(listOf("viejo", "nuevo"), books.observeBooks().first().map { it.id })
    }

    /**
     * Reimportar el mismo libro sustituye su contenido en lugar de sumarlo. Sin esto, el
     * texto quedaria duplicado y el progreso apuntando a la mitad del libro anterior.
     */
    @Test
    fun `volver a guardar un libro reemplaza su texto en lugar de anadirlo`() = runTest {
        books.save(book("uno"), bloques, capitulos)

        books.save(book("uno"), bloques.take(1), emptyList())

        assertEquals(1, books.blocks("uno").size)
        assertTrue(books.chapters("uno").isEmpty())
    }

    /** Borrar el libro se lleva su texto: la clave ajena esta en cascada por eso. */
    @Test
    fun `borrar un libro se lleva su texto y su indice`() = runTest {
        books.save(book("uno"), bloques, capitulos)

        books.delete("uno")

        assertNull(books.book("uno"))
        assertTrue(books.blocks("uno").isEmpty())
        assertTrue(books.chapters("uno").isEmpty())
    }

    // --- Marcas y subrayados ---

    private fun marca(id: String, block: Int, offset: Int = 0) = Bookmark(
        id = id,
        bookId = "uno",
        position = ReadingPosition(block, offset),
        snippet = "El ser es y el no ser no es.",
        page = 12,
        createdAtMillis = 1_000L,
    )

    private fun subrayado(id: String, block: Int, note: String? = null) = Highlight(
        id = id,
        bookId = "uno",
        blockIndex = block,
        start = 0,
        end = 28,
        text = "El ser es y el no ser no es.",
        note = note,
        page = 12,
        createdAtMillis = 1_000L,
    )

    @Test
    fun `una marca se guarda con su trozo de texto y vuelve entera`() = runTest {
        books.save(book("uno"), bloques, capitulos)

        books.addBookmark(marca("m1", block = 1, offset = 14))

        val recuperada = books.observeBookmarks("uno").first().single()
        assertEquals(ReadingPosition(1, 14), recuperada.position)
        assertEquals("El ser es y el no ser no es.", recuperada.snippet)
        assertEquals(12, recuperada.page)
    }

    /**
     * En orden de lectura, no por fecha: una lista de marcas es un recorrido del libro, y
     * ordenarla por cuando se puso obliga a reconstruir mentalmente por donde caia cada
     * una.
     */
    @Test
    fun `las marcas vuelven en orden de lectura`() = runTest {
        books.save(book("uno"), bloques, capitulos)

        books.addBookmark(marca("m2", block = 2))
        books.addBookmark(marca("m1", block = 0))

        assertEquals(listOf("m1", "m2"), books.observeBookmarks("uno").first().map { it.id })
    }

    @Test
    fun `un subrayado sin nota y otro con ella se distinguen al volver`() = runTest {
        books.save(book("uno"), bloques, capitulos)

        books.putHighlight(subrayado("s1", block = 0))
        books.putHighlight(subrayado("s2", block = 1, note = "Esto es Parménides"))

        val recuperados = books.observeHighlights("uno").first()
        assertEquals(listOf(false, true), recuperados.map { it.hasNote })
        assertEquals("Esto es Parménides", recuperados[1].note)
    }

    /**
     * Escribirle una nota a algo ya subrayado es **editarlo**, no crear otro. Sin esto,
     * anotar dejaria dos subrayados encima del mismo fragmento.
     */
    @Test
    fun `anotar un subrayado que ya existe lo edita en vez de duplicarlo`() = runTest {
        books.save(book("uno"), bloques, capitulos)
        books.putHighlight(subrayado("s1", block = 0))

        books.putHighlight(subrayado("s1", block = 0, note = "Una nota"))

        val recuperados = books.observeHighlights("uno").first()
        assertEquals(1, recuperados.size)
        assertEquals("Una nota", recuperados.single().note)
    }

    /** Una nota en blanco no es una nota: tiene que quedar como "solo subrayado". */
    @Test
    fun `una nota en blanco se guarda como sin nota`() = runTest {
        books.save(book("uno"), bloques, capitulos)

        books.putHighlight(subrayado("s1", block = 0, note = "   "))

        assertNull(books.observeHighlights("uno").first().single().note)
    }

    @Test
    fun `quitar una marca o un subrayado los borra`() = runTest {
        books.save(book("uno"), bloques, capitulos)
        books.addBookmark(marca("m1", block = 0))
        books.putHighlight(subrayado("s1", block = 0))

        books.deleteBookmark("m1")
        books.deleteHighlight("s1")

        assertTrue(books.observeBookmarks("uno").first().isEmpty())
        assertTrue(books.observeHighlights("uno").first().isEmpty())
    }

    /** Borrar el libro se lleva lo que el usuario dejo escrito encima: clave en cascada. */
    @Test
    fun `borrar el libro se lleva sus marcas y sus subrayados`() = runTest {
        books.save(book("uno"), bloques, capitulos)
        books.addBookmark(marca("m1", block = 0))
        books.putHighlight(subrayado("s1", block = 0))

        books.delete("uno")

        assertTrue(books.observeBookmarks("uno").first().isEmpty())
        assertTrue(books.observeHighlights("uno").first().isEmpty())
    }

    @Test
    fun `las marcas de un libro no salen en las de otro`() = runTest {
        books.save(book("uno"), bloques, capitulos)
        books.save(book("dos"), emptyList(), emptyList())
        books.addBookmark(marca("m1", block = 0))

        assertTrue(books.observeBookmarks("dos").first().isEmpty())
        assertEquals(1, books.observeBookmarks("uno").first().size)
    }

    @Test
    fun `la biblioteca se reemite al guardar un libro`() = runTest {
        assertTrue(books.observeBooks().first().isEmpty())

        books.save(book("uno"), bloques, capitulos)

        assertEquals(1, books.observeBooks().first().size)
    }
}
