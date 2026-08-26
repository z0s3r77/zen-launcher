package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.reading.Book
import com.zenlauncher.zen.domain.reading.ImportFailure
import com.zenlauncher.zen.domain.reading.ImportState
import com.zenlauncher.zen.domain.reading.ReadingPosition
import com.zenlauncher.zen.presentation.reading.LibraryScreen
import com.zenlauncher.zen.presentation.reading.LibraryUiState
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var added = 0
    private val opened = mutableListOf<Book>()
    private var dismissed = 0
    private var backs = 0

    private fun libro(
        id: String,
        title: String = "El ser y la nada",
        author: String? = "Jean-Paul Sartre",
        lastBlock: Int = 0,
        blockCount: Int = 100,
    ) = Book(
        id = id,
        title = title,
        author = author,
        sourceUri = "content://documentos/$id",
        coverPath = null,
        pageCount = 342,
        blockCount = blockCount,
        importedAtMillis = 1_000L,
        lastReadAtMillis = null,
        lastPosition = ReadingPosition(lastBlock),
    )

    private fun render(state: LibraryUiState) {
        composeRule.setContent {
            ZenTheme {
                LibraryScreen(
                    state = state,
                    onAddBook = { added++ },
                    onOpenBook = { opened += it },
                    onDismissImport = { dismissed++ },
                    coverPath = { "/datos/$it" },
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun `sin libros lo dice en lugar de ensenar una lista vacia`() {
        render(LibraryUiState(loading = false))

        composeRule.onNodeWithText("TODAVÍA NO HAY LIBROS").assertIsDisplayed()
        composeRule.onNodeWithText("AÑADIR LIBRO").assertIsDisplayed()
    }

    @Test
    fun `cada libro ensena titulo, autor y por donde va`() {
        render(
            LibraryUiState(
                books = listOf(libro("uno", lastBlock = 42, blockCount = 101)),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("El ser y la nada").assertIsDisplayed()
        composeRule.onNodeWithText("Jean-Paul Sartre").assertIsDisplayed()
        composeRule.onNodeWithText("42%").assertIsDisplayed()
    }

    /**
     * Un "autor desconocido" es texto que ocupa sitio para no decir nada: la linea
     * simplemente no existe. Es la misma regla del mando del reproductor en la home.
     */
    @Test
    fun `un libro sin autor no pinta la linea del autor`() {
        render(LibraryUiState(books = listOf(libro("uno", author = null)), loading = false))

        composeRule.onNodeWithText("El ser y la nada").assertIsDisplayed()
        composeRule.onNodeWithText("Jean-Paul Sartre").assertDoesNotExist()
    }

    @Test
    fun `tocar un libro lo abre`() {
        render(LibraryUiState(books = listOf(libro("uno")), loading = false))

        composeRule.onNodeWithText("El ser y la nada").performClick()

        assertEquals(listOf("uno"), opened.map { it.id })
    }

    @Test
    fun `anadir un libro abre el selector`() {
        render(LibraryUiState(loading = false))

        composeRule.onNodeWithText("AÑADIR LIBRO").performClick()

        assertEquals(1, added)
    }

    /**
     * Dos libros con dos progresos distintos: con una sola ficha, un error de clave o de
     * indice en la lista no se veria.
     */
    @Test
    fun `dos libros se pintan por separado`() {
        render(
            LibraryUiState(
                books = listOf(
                    libro("uno", title = "El ser y la nada"),
                    libro("dos", title = "Meditaciones metafísicas", author = "René Descartes"),
                ),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("El ser y la nada").assertIsDisplayed()
        composeRule.onNodeWithText("Meditaciones metafísicas").assertIsDisplayed()
        composeRule.onNodeWithText("René Descartes").assertIsDisplayed()
    }

    @Test
    fun `mientras se importa se dice por donde va`() {
        render(
            LibraryUiState(
                import = ImportState.Reading(page = 128, total = 342),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("LEYENDO 128 / 342 PÁGINAS").assertIsDisplayed()
        // Y no se declara vacia: la importacion en marcha ya es algo que esta pasando.
        composeRule.onNodeWithText("TODAVÍA NO HAY LIBROS").assertDoesNotExist()
    }

    /**
     * Un escaneo se distingue de un fichero roto: al usuario se le puede decir
     * exactamente que le pasa a su PDF, y es ademas lo que resolveria el OCR.
     */
    @Test
    fun `un PDF escaneado lo dice con sus palabras`() {
        render(
            LibraryUiState(import = ImportState.Failed(ImportFailure.NO_TEXT), loading = false),
        )

        composeRule.onNodeWithText(
            "Ese PDF no lleva texto dentro: son páginas escaneadas. " +
                "Zen todavía no sabe leer imágenes.",
        ).assertIsDisplayed()
    }

    @Test
    fun `el aviso de la importacion se puede descartar tocandolo`() {
        render(
            LibraryUiState(
                import = ImportState.Failed(ImportFailure.UNREADABLE),
                loading = false,
            ),
        )

        composeRule.onNodeWithText(
            "Ese fichero no se pudo abrir. Puede que no sea un PDF, que esté dañado " +
                "o que lleve contraseña.",
        ).performClick()

        assertEquals(1, dismissed)
    }

    /**
     * Hacer elegir un fichero para luego decir que no se puede abrir es peor que no
     * ofrecerlo: en un telefono con Android 14 el boton no existe.
     */
    @Test
    fun `un telefono que no puede extraer texto no ofrece anadir libros`() {
        render(LibraryUiState(available = false, loading = false))

        composeRule.onNodeWithText("AÑADIR LIBRO").assertDoesNotExist()
        composeRule.onNodeWithText(
            "Este teléfono no puede sacar el texto de un PDF: hace falta Android 15 o posterior.",
        ).assertIsDisplayed()
    }
}
