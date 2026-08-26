package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.reading.BookImporter
import com.zenlauncher.zen.domain.reading.ImportFailure
import com.zenlauncher.zen.domain.reading.ImportState
import com.zenlauncher.zen.domain.reading.PdfDocumentText
import com.zenlauncher.zen.domain.reading.PdfPageText
import com.zenlauncher.zen.fakes.FakeBookCoverStore
import com.zenlauncher.zen.fakes.FakeBookRepository
import com.zenlauncher.zen.fakes.FakePdfTextSource
import com.zenlauncher.zen.fakes.FakeZenClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookImporterTest {

    private val clock = FakeZenClock(wall = 1_700_000_000_000)

    private fun importer(
        pdf: FakePdfTextSource,
        books: FakeBookRepository = FakeBookRepository(),
        covers: FakeBookCoverStore = FakeBookCoverStore(),
        scope: TestScope,
    ) = BookImporter(pdf = pdf, books = books, covers = covers, clock = clock, scope = scope)

    @Test
    fun `un PDF con texto acaba siendo un libro en la biblioteca`() = runTest {
        val books = FakeBookRepository()
        val importer = importer(FakePdfTextSource(FakePdfTextSource.libro()), books, scope = this)

        importer.start("content://documentos/1")
        advanceUntilIdle()

        val done = importer.state.value
        assertTrue(done is ImportState.Done)
        assertEquals("El ser y la nada", (done as ImportState.Done).title)

        val saved = books.observeBooks().first().single()
        assertEquals("El ser y la nada", saved.title)
        assertEquals("Jean-Paul Sartre", saved.author)
        assertEquals("content://documentos/1", saved.sourceUri)
        assertEquals(1_700_000_000_000, saved.importedAtMillis)
        // Recien importado, sin abrir: por el principio y sin fecha de lectura.
        assertEquals(0, saved.lastBlockIndex)
        assertEquals(null, saved.lastReadAtMillis)
        assertNotNull(saved.coverPath)
        assertTrue(books.blocks(saved.id).isNotEmpty())
    }

    @Test
    fun `un telefono sin extraccion de texto lo dice sin abrir nada`() = runTest {
        val pdf = FakePdfTextSource(FakePdfTextSource.libro(), available = false)
        val importer = importer(pdf, scope = this)

        importer.start("content://documentos/1")
        advanceUntilIdle()

        assertEquals(ImportState.Failed(ImportFailure.UNSUPPORTED), importer.state.value)
        assertEquals("No debe llegar a leer el fichero", 0, pdf.reads)
    }

    @Test
    fun `un fichero que no se puede abrir se distingue de uno escaneado`() = runTest {
        val importer = importer(FakePdfTextSource(document = null), scope = this)

        importer.start("content://documentos/roto")
        advanceUntilIdle()

        assertEquals(ImportState.Failed(ImportFailure.UNREADABLE), importer.state.value)
    }

    /**
     * Un escaneo abre bien y no tiene ni una letra. Se dice tal cual en lugar de crear un
     * libro de cero parrafos que al abrirlo parece un fallo de Zen. Es ademas el caso que
     * resolveria el OCR el dia que lo haya.
     */
    @Test
    fun `un PDF escaneado no crea un libro vacio`() = runTest {
        val books = FakeBookRepository()
        val escaneo = PdfDocumentText(
            pages = listOf(PdfPageText(0, ""), PdfPageText(1, "  ")),
            fileName = "escaneo.pdf",
        )
        val importer = importer(FakePdfTextSource(escaneo), books, scope = this)

        importer.start("content://documentos/escaneo")
        advanceUntilIdle()

        assertEquals(ImportState.Failed(ImportFailure.NO_TEXT), importer.state.value)
        assertTrue(books.observeBooks().first().isEmpty())
    }

    /**
     * Dos importaciones a la vez son dos PDF enteros en memoria dentro del proceso del
     * launcher, que es el primero al que el sistema mata cuando falta memoria.
     */
    @Test
    fun `una segunda importacion mientras hay una en marcha no hace nada`() = runTest {
        val pdf = FakePdfTextSource(FakePdfTextSource.libro())
        val importer = importer(pdf, scope = this)

        importer.start("content://documentos/1")
        importer.start("content://documentos/2")
        advanceUntilIdle()

        assertEquals(1, pdf.reads)
    }

    @Test
    fun `el resultado se descarta al darlo por visto`() = runTest {
        val importer = importer(FakePdfTextSource(document = null), scope = this)

        importer.start("content://documentos/roto")
        advanceUntilIdle()
        importer.acknowledge()

        assertEquals(ImportState.Idle, importer.state.value)
    }

    /** Descartar mientras trabaja dejaria al usuario sin la unica senal de que pasa algo. */
    @Test
    fun `no se puede descartar una importacion en marcha`() = runTest {
        val importer = importer(FakePdfTextSource(FakePdfTextSource.libro()), scope = this)

        importer.start("content://documentos/1")
        importer.acknowledge()

        assertTrue(importer.state.value.busy)
    }
}
