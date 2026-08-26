package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.domain.reading.BlockKind
import com.zenlauncher.zen.domain.reading.Book
import com.zenlauncher.zen.domain.reading.BookBlock
import com.zenlauncher.zen.domain.reading.BookChapter
import com.zenlauncher.zen.domain.reading.ReadingPosition
import com.zenlauncher.zen.domain.reading.ReadingSettings
import com.zenlauncher.zen.fakes.FakeBookCoverStore
import com.zenlauncher.zen.fakes.FakeBookRepository
import com.zenlauncher.zen.fakes.FakePreferencesRepository
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.presentation.reading.ReaderViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeZenClock(wall = 1_700_000_000_000)

    private val bloques = listOf(
        BookBlock(0, BlockKind.HEADING, "1. El problema del ser", page = 12, level = 1),
        BookBlock(1, BlockKind.PARAGRAPH, "El ser es y el no ser no es.", page = 12),
        BookBlock(2, BlockKind.HEADING, "2. La conciencia", page = 34, level = 1),
        BookBlock(3, BlockKind.PARAGRAPH, "La conciencia constituye un problema.", page = 34),
    )

    private val capitulos = listOf(
        BookChapter("1. El problema del ser", 1, blockIndex = 0, page = 12),
        BookChapter("2. La conciencia", 1, blockIndex = 2, page = 34),
    )

    private fun book(lastBlock: Int = 0) = Book(
        id = "uno",
        title = "El ser y la nada",
        author = "Jean-Paul Sartre",
        sourceUri = "content://documentos/uno",
        coverPath = null,
        pageCount = 342,
        blockCount = bloques.size,
        importedAtMillis = 1_000L,
        lastReadAtMillis = null,
        lastPosition = ReadingPosition(lastBlock),
    )

    private suspend fun repository(lastBlock: Int = 0) = FakeBookRepository().apply {
        save(book(lastBlock), bloques, capitulos)
    }

    private fun model(
        books: FakeBookRepository,
        preferences: FakePreferencesRepository = FakePreferencesRepository(),
        covers: FakeBookCoverStore = FakeBookCoverStore(),
        scope: TestScope,
    ) = ReaderViewModel(
        books = books,
        covers = covers,
        preferences = preferences,
        clock = clock,
        appScope = scope,
    )

    @Test
    fun `al abrir un libro llegan su texto y su indice`() = runTest {
        val model = model(repository(), scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            assertEquals("El ser y la nada", estado.book?.title)
            assertEquals(bloques, estado.blocks)
            assertEquals(capitulos, estado.chapters)
        }
    }

    @Test
    fun `un libro que ya no esta se dice, no se queda cargando`() = runTest {
        val model = model(FakeBookRepository(), scope = this)

        model.state.test {
            model.open("fantasma")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            assertTrue(estado.missing)
        }
    }

    /**
     * El caso de aceptacion: cerrar la aplicacion y volver tiene que devolver al mismo
     * sitio. El sitio se lleva en bloques y no en paginas porque el texto es reflowable
     * (ver `ReadingProgress`).
     */
    @Test
    fun `el sitio de lectura se guarda cuando el desplazamiento para`() = runTest {
        val books = repository()
        val model = model(books, scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            model.onPositionVisible(ReadingPosition(3))
            advanceTimeBy(600)
            advanceUntilIdle()

            assertEquals(ReadingPosition(3), books.book("uno")?.lastPosition)
            assertEquals(1_700_000_000_000, books.book("uno")?.lastReadAtMillis)
        }
    }

    /**
     * Regresion: arrastrar el pulgar por un libro emite un indice nuevo por fotograma.
     * Sin el retardo, eso son decenas de escrituras en SQLite por segundo dentro del
     * proceso del launcher.
     */
    @Test
    fun `desplazarse deprisa escribe una sola vez, no una por bloque`() = runTest {
        val books = repository()
        val model = model(books, scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()
            val antes = books.progressWrites

            model.onPositionVisible(ReadingPosition(1))
            model.onPositionVisible(ReadingPosition(2))
            model.onPositionVisible(ReadingPosition(3))
            advanceTimeBy(600)
            advanceUntilIdle()

            assertEquals(1, books.progressWrites - antes)
            assertEquals(ReadingPosition(3), books.book("uno")?.lastPosition)
        }
    }

    /**
     * Abrir un libro no puede escribir el sitio que acaba de leer: seria escribir lo
     * mismo que se acaba de leer en cada apertura, y en un libro sin abrir marcaria como
     * "leido" el momento de mirarlo por encima.
     */
    @Test
    fun `abrir un libro no guarda progreso por si solo`() = runTest {
        val books = repository(lastBlock = 2)
        val model = model(books, scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            advanceTimeBy(2_000)
            advanceUntilIdle()

            assertEquals(0, books.progressWrites)
        }
    }

    @Test
    fun `buscar dentro del libro devuelve el sitio y un trozo alrededor`() = runTest {
        val model = model(repository(), scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            model.onQueryChange("conciencia")
            while (estado.hits.isEmpty()) estado = awaitItem()

            assertEquals(2, estado.hits.size)
            assertTrue(estado.hits.any { it.blockIndex == 3 })
        }
    }

    @Test
    fun `lo escrito en el buscador viaja aparte del estado`() = runTest {
        val model = model(repository(), scope = this)

        model.onQueryChange("libertad")

        assertEquals("libertad", model.query.value)
    }

    @Test
    fun `los ajustes de lectura se guardan en las preferencias`() = runTest {
        val preferences = FakePreferencesRepository()
        val model = model(repository(), preferences, scope = this)

        model.state.test {
            awaitItem()
            model.setTextStep(ReadingSettings.TEXT_STEPS)
            advanceUntilIdle()

            var estado = awaitItem()
            while (estado.settings.textStep != ReadingSettings.TEXT_STEPS) estado = awaitItem()

            assertEquals(ReadingSettings.TEXT_STEPS, estado.settings.textStep)
        }
    }

    @Test
    fun `la tipografia se puede cambiar a la del launcher y volver`() = runTest {
        val model = model(repository(), scope = this)

        model.state.test {
            assertTrue(awaitItem().settings.serif)

            model.toggleSerif()
            advanceUntilIdle()

            assertFalse(awaitItem().settings.serif)
        }
    }

    @Test
    fun `quitar el libro se lleva su portada`() = runTest {
        val books = repository()
        val covers = FakeBookCoverStore()
        val model = model(books, covers = covers, scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            model.delete()
            advanceUntilIdle()

            assertEquals(null, books.book("uno"))
            assertEquals(listOf("uno"), covers.deleted)
        }
    }

    /**
     * Un libro sin indice tiene que poder recorrerse igual: es la alternativa prometida
     * cuando la deteccion no encuentra nada.
     */
    @Test
    fun `sin indice se ofrecen saltos por pagina`() = runTest {
        val muchos = (0 until 200).map {
            BookBlock(it, BlockKind.PARAGRAPH, "Parrafo $it", page = it / 3)
        }
        val books = FakeBookRepository().apply {
            save(book().copy(blockCount = muchos.size), muchos, emptyList())
        }
        val model = model(books, scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            assertTrue(estado.chapters.isEmpty())
            assertTrue(estado.pageStops.isNotEmpty())
            assertEquals(0, estado.pageStops.first().index)
        }
    }

    // --- Marcas y subrayados ---

    @Test
    fun `marcar una pagina la deja en la lista con su texto`() = runTest {
        val books = repository()
        val model = model(books, scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            model.addBookmark(ReadingPosition(1, 12), "El ser es y el no ser no es.", page = 12)
            while (estado.bookmarks.isEmpty()) estado = awaitItem()

            val marca = estado.bookmarks.single()
            assertEquals(ReadingPosition(1, 12), marca.position)
            assertEquals("El ser es y el no ser no es.", marca.snippet)
            assertEquals(1_700_000_000_000, marca.createdAtMillis)
        }
    }

    @Test
    fun `subrayar sin nota y con nota son la misma cosa`() = runTest {
        val model = model(repository(), scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            model.putHighlight(1, 0, 28, "El ser es y el no ser no es.", page = 12)
            while (estado.highlights.isEmpty()) estado = awaitItem()

            val subrayado = estado.highlights.single()
            assertFalse(subrayado.hasNote)

            model.setNote(subrayado, "Esto es Parménides")
            while (!estado.highlights.first().hasNote) estado = awaitItem()

            assertEquals(1, estado.highlights.size)
            assertEquals("Esto es Parménides", estado.highlights.single().note)
        }
    }

    /** Una nota en blanco no es una nota: tiene que volver a ser "solo subrayado". */
    @Test
    fun `borrar el texto de una nota la deja en solo subrayado`() = runTest {
        val model = model(repository(), scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            model.putHighlight(1, 0, 28, "El ser es y el no ser no es.", page = 12, note = "Algo")
            while (estado.highlights.isEmpty()) estado = awaitItem()

            model.setNote(estado.highlights.single(), "   ")
            while (estado.highlights.single().hasNote) estado = awaitItem()

            assertNull(estado.highlights.single().note)
        }
    }

    /**
     * Un subrayado de longitud cero no es nada, y guardarlo dejaria una entrada en la
     * lista de marcas que no lleva a ningun texto.
     */
    @Test
    fun `no se subraya un fragmento vacio`() = runTest {
        val model = model(repository(), scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            model.putHighlight(1, 10, 10, "", page = 12)
            advanceUntilIdle()

            assertTrue(model.state.value.highlights.isEmpty())
        }
    }

    @Test
    fun `quitar una marca o un subrayado los saca de la lista`() = runTest {
        val model = model(repository(), scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            model.addBookmark(ReadingPosition(1), "Un trozo", page = 12)
            model.putHighlight(1, 0, 28, "El ser es y el no ser no es.", page = 12)
            while (estado.bookmarks.isEmpty() || estado.highlights.isEmpty()) estado = awaitItem()

            model.deleteBookmark(estado.bookmarks.single().id)
            model.deleteHighlight(estado.highlights.single().id)
            while (estado.bookmarks.isNotEmpty() || estado.highlights.isNotEmpty()) {
                estado = awaitItem()
            }

            assertTrue(estado.bookmarks.isEmpty())
            assertTrue(estado.highlights.isEmpty())
        }
    }

    /** Las marcas son de un libro, no de la aplicacion: abrir otro no las arrastra. */
    @Test
    fun `las marcas son del libro que se abrio`() = runTest {
        val books = repository()
        books.save(book().copy(id = "dos"), bloques, capitulos)
        val model = model(books, scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()
            model.addBookmark(ReadingPosition(1), "Un trozo", page = 12)
            while (estado.bookmarks.isEmpty()) estado = awaitItem()

            model.open("dos")
            while (estado.bookmarks.isNotEmpty()) estado = awaitItem()

            assertTrue(estado.bookmarks.isEmpty())
        }
    }

    @Test
    fun `con indice no se ofrecen saltos por pagina`() = runTest {
        val model = model(repository(), scope = this)

        model.state.test {
            model.open("uno")
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            assertTrue(estado.pageStops.isEmpty())
        }
    }
}
