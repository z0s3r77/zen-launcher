package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.domain.reading.Book
import com.zenlauncher.zen.domain.reading.BookImporter
import com.zenlauncher.zen.domain.reading.ImportFailure
import com.zenlauncher.zen.domain.reading.ImportState
import com.zenlauncher.zen.domain.reading.ReadingPosition
import com.zenlauncher.zen.fakes.FakeBookCoverStore
import com.zenlauncher.zen.fakes.FakeBookRepository
import com.zenlauncher.zen.fakes.FakePdfTextSource
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.presentation.reading.LibraryUiState
import com.zenlauncher.zen.presentation.reading.LibraryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun book(id: String, lastBlock: Int = 0) = Book(
        id = id,
        title = "El ser y la nada",
        author = "Jean-Paul Sartre",
        sourceUri = "content://documentos/$id",
        coverPath = "lectura/$id/portada.jpg",
        pageCount = 342,
        blockCount = 100,
        importedAtMillis = 1_000L,
        lastReadAtMillis = null,
        lastPosition = ReadingPosition(lastBlock),
    )

    private fun model(
        books: FakeBookRepository = FakeBookRepository(),
        covers: FakeBookCoverStore = FakeBookCoverStore(),
        pdf: FakePdfTextSource = FakePdfTextSource(FakePdfTextSource.libro()),
        scope: TestScope,
    ) = LibraryViewModel(
        books = books,
        importer = BookImporter(pdf, books, covers, FakeZenClock(), scope),
        covers = covers,
        appScope = scope,
    )

    @Test
    fun `sin libros la biblioteca se declara vacia`() = runTest {
        val model = model(scope = this)

        model.state.test {
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            assertTrue(estado.empty)
            assertTrue(estado.books.isEmpty())
        }
    }

    @Test
    fun `los libros guardados aparecen en la lista`() = runTest {
        val model = model(FakeBookRepository(listOf(book("uno"))), scope = this)

        model.state.test {
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            assertEquals(listOf("uno"), estado.books.map { it.id })
            assertFalse(estado.empty)
        }
    }

    /**
     * Importar en marcha **no** es una biblioteca vacia: durante el minuto que tarda un
     * libro grande, la pantalla tiene que ensenar por donde va y no "todavia no hay
     * libros", que se lee como que la importacion no ha hecho nada.
     *
     * Se comprueba sobre el estado y no arrancando una importacion de verdad: con un
     * lector de mentira, la importacion entera termina en el mismo instante en que el
     * test se suspende para mirarla, asi que el momento "en marcha" no existiria nunca.
     */
    @Test
    fun `mientras se importa la biblioteca no se declara vacia`() {
        val importando = LibraryUiState(import = ImportState.Building, loading = false)
        val leyendo = LibraryUiState(
            import = ImportState.Reading(page = 12, total = 342),
            loading = false,
        )

        assertFalse(importando.empty)
        assertFalse(leyendo.empty)
        assertTrue(LibraryUiState(loading = false).empty)
    }

    @Test
    fun `un fichero ilegible llega a la pantalla como fallo`() = runTest {
        val model = model(pdf = FakePdfTextSource(document = null), scope = this)

        model.state.test {
            awaitItem()
            model.import("content://documentos/roto")

            var estado = awaitItem()
            while (estado.import !is ImportState.Failed) estado = awaitItem()

            assertEquals(ImportState.Failed(ImportFailure.UNREADABLE), estado.import)
        }
    }

    @Test
    fun `un telefono sin extraccion de texto no ofrece importar`() = runTest {
        val model = model(
            pdf = FakePdfTextSource(FakePdfTextSource.libro(), available = false),
            scope = this,
        )

        model.state.test {
            assertFalse(awaitItem().available)
        }
    }

    /**
     * El texto del libro se va solo por la clave ajena en cascada, pero la portada es un
     * fichero: sin borrarla queda una carpeta que ya no aparece en ninguna biblioteca.
     */
    @Test
    fun `quitar un libro se lleva tambien su portada`() = runTest {
        val books = FakeBookRepository(listOf(book("uno")))
        val covers = FakeBookCoverStore()
        val model = model(books, covers, scope = this)

        model.delete("uno")
        advanceUntilIdle()

        books.observeBooks().test { assertTrue(awaitItem().isEmpty()) }
        assertEquals(listOf("uno"), covers.deleted)
    }

    @Test
    fun `dar por visto el resultado lo quita de la pantalla`() = runTest {
        val model = model(pdf = FakePdfTextSource(document = null), scope = this)

        model.state.test {
            awaitItem()
            model.import("content://documentos/roto")
            var estado = awaitItem()
            while (estado.import !is ImportState.Failed) estado = awaitItem()

            model.acknowledgeImport()

            assertEquals(ImportState.Idle, awaitItem().import)
        }
    }

    @Test
    fun `la ruta de la portada se resuelve a una absoluta`() = runTest {
        val model = model(scope = this)

        assertEquals("/datos/lectura/uno/portada.jpg", model.coverPath("lectura/uno/portada.jpg"))
    }
}
