package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.centerLeft
import androidx.compose.ui.test.click
import androidx.compose.ui.test.centerRight
import androidx.compose.ui.test.longClick
import com.zenlauncher.zen.domain.reading.BlockKind
import com.zenlauncher.zen.domain.reading.Book
import com.zenlauncher.zen.domain.reading.BookBlock
import com.zenlauncher.zen.domain.reading.BookChapter
import com.zenlauncher.zen.domain.reading.Bookmark
import com.zenlauncher.zen.domain.reading.Highlight
import com.zenlauncher.zen.domain.reading.ReadingHit
import com.zenlauncher.zen.domain.reading.ReadingPosition
import com.zenlauncher.zen.domain.reading.ReadingSettings
import com.zenlauncher.zen.presentation.reading.ReaderScreen
import com.zenlauncher.zen.presentation.reading.ReaderUiState
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El lector paginado.
 *
 * Cuidado con lo que se puede probar aqui: la fuente de relleno de Robolectric mide ~1 px
 * por glifo, asi que **el reparto en paginas de verdad no se puede comprobar en JVM** —un
 * parrafo de cien caracteres ocupa cien pixeles y cabe entero en cualquier linea—. Lo que
 * si se comprueba es que el mecanismo funciona: con muchos parrafos cortos se llena mas de
 * una hoja, y pasar pagina cambia lo que se ve. El corte fino de texto vive en
 * `PaginatorTest`, contra un medidor de mentira con cuentas exactas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ReaderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val textSteps = mutableListOf<Int>()
    private val marginSteps = mutableListOf<Int>()
    private var serifToggles = 0
    private var deletes = 0
    private var backs = 0
    private var query = ""
    private val positions = mutableListOf<ReadingPosition>()
    private val addedBookmarks = mutableListOf<Triple<ReadingPosition, String, Int>>()
    private val deletedBookmarks = mutableListOf<String>()
    private val highlighted = mutableListOf<Highlight>()
    private val deletedHighlights = mutableListOf<String>()

    /** El parrafo del que se subraya. Lleva dos frases para poder ampliar la seleccion. */
    private val parrafoLibertad =
        "El hombre está condenado a ser libre. No hay excusa ninguna."

    private val bloques = listOf(
        BookBlock(0, BlockKind.HEADING, "LA CONCIENCIA", page = 33, level = 1),
        BookBlock(1, BlockKind.PARAGRAPH, parrafoLibertad, page = 33),
        BookBlock(2, BlockKind.HEADING, "3. Libertad", page = 66, level = 1),
    ) + (3 until 120).map {
        BookBlock(it, BlockKind.PARAGRAPH, "Párrafo número $it del libro.", page = 66 + it / 10)
    }

    private val capitulos = listOf(
        BookChapter("2. La conciencia", level = 1, blockIndex = 0, page = 33),
        BookChapter("3. Libertad", level = 1, blockIndex = 2, page = 66),
        BookChapter("3.1 Determinismo", level = 2, blockIndex = 3, page = 70),
    )

    private val libro = Book(
        id = "uno",
        title = "El ser y la nada",
        author = "Jean-Paul Sartre",
        sourceUri = "content://documentos/uno",
        coverPath = null,
        pageCount = 342,
        blockCount = bloques.size,
        importedAtMillis = 1_000L,
        lastReadAtMillis = null,
    )

    private fun estado(
        blocks: List<BookBlock> = bloques,
        chapters: List<BookChapter> = capitulos,
        book: Book? = libro,
        hits: List<ReadingHit> = emptyList(),
        bookmarks: List<Bookmark> = emptyList(),
        highlights: List<Highlight> = emptyList(),
        loading: Boolean = false,
    ) = ReaderUiState(
        book = book,
        blocks = blocks,
        chapters = chapters,
        hits = hits,
        bookmarks = bookmarks,
        highlights = highlights,
        loading = loading,
    )

    private fun render(state: ReaderUiState = estado()) {
        composeRule.setContent {
            ZenTheme {
                ReaderScreen(
                    state = state,
                    query = query,
                    onQueryChange = { query = it },
                    onPositionVisible = { positions += it },
                    onTextStep = { textSteps += it },
                    onLeadingStep = {},
                    onMarginStep = { marginSteps += it },
                    onToggleSerif = { serifToggles++ },
                    onAddBookmark = { position, snippet, page ->
                        addedBookmarks += Triple(position, snippet, page)
                    },
                    onDeleteBookmark = { deletedBookmarks += it },
                    onHighlight = { block, start, end, text, page, note ->
                        highlighted += Highlight(
                            id = "nuevo",
                            bookId = "uno",
                            blockIndex = block,
                            start = start,
                            end = end,
                            text = text,
                            note = note,
                            page = page,
                            createdAtMillis = 0L,
                        )
                    },
                    onSetNote = { highlight, note -> highlighted += highlight.copy(note = note) },
                    onDeleteHighlight = { deletedHighlights += it },
                    onDelete = { deletes++ },
                    onBack = { backs++ },
                )
            }
        }
    }

    private fun titulo() = composeRule.onNodeWithText("LA CONCIENCIA")

    /**
     * Mantener pulsado **por el borde izquierdo**, no por el centro.
     *
     * La fuente de relleno de Robolectric mide ~1 px por glifo mientras que el nodo ocupa
     * el ancho entero, asi que un toque en el centro cae mas alla del texto y
     * `getOffsetForPosition` devuelve el ultimo caracter: se seleccionaria siempre la
     * ultima frase del parrafo. Por la izquierda cae en el primero.
     */
    private fun pulsarLargoEn(texto: String) =
        composeRule.onNodeWithText(texto).performTouchInput { longClick(centerLeft) }

    /**
     * Tocar el centro despierta la pantalla; volver a tocarlo la duerme.
     *
     * Se toca un parrafo de **mitad de hoja** y no el titulo de arriba: con la pantalla
     * despierta, la franja de cabecera se dibuja encima del principio de la pagina y se
     * traga los toques que caen sobre ella, asi que el titulo dejaria de servir para
     * volver a dormirla.
     */
    private fun despertar() =
        composeRule.onNodeWithText("Párrafo número 10 del libro.").performClick()

    // --- La pantalla limpia ---

    /**
     * Leer a pagina completa: mientras se lee **no hay nada mas que el libro**. Es la
     * unica pantalla de Zen que esconde hasta su propia salida, y lo hace porque aqui el
     * contenido es la pantalla.
     */
    @Test
    fun `mientras se lee no hay nada en pantalla salvo el texto`() {
        render()

        composeRule.onNodeWithText("LA CONCIENCIA").assertIsDisplayed()
        composeRule.onNodeWithText("LECTURA").assertDoesNotExist()
        composeRule.onNodeWithText("MARCAR").assertDoesNotExist()
        composeRule.onNodeWithText("AJUSTAR").assertDoesNotExist()
        composeRule.onNodeWithText("ÍNDICE").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Página siguiente").assertDoesNotExist()
    }

    @Test
    fun `tocar el centro saca todo, y volver a tocarlo lo esconde`() {
        render()

        despertar()
        composeRule.onNodeWithText("LECTURA").assertIsDisplayed()
        composeRule.onNodeWithText("MARCAR").assertIsDisplayed()
        composeRule.onNodeWithText("AJUSTAR").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Página siguiente").assertIsDisplayed()

        despertar()
        composeRule.onNodeWithText("LECTURA").assertDoesNotExist()
        composeRule.onNodeWithText("AJUSTAR").assertDoesNotExist()
    }

    /**
     * La salida esconderse no es lo mismo que no existir: al despertar la pantalla vuelve
     * el boton de volver, y ademas sigue funcionando el arrastre desde el borde.
     */
    @Test
    fun `la salida aparece al despertar la pantalla`() {
        render()
        despertar()

        composeRule.onNodeWithContentDescription("Volver").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `un libro que ya no esta lo dice en lugar de quedarse en blanco`() {
        render(estado(book = null, blocks = emptyList(), chapters = emptyList()))

        composeRule.onNodeWithText("Este libro ya no está en la biblioteca.").assertIsDisplayed()
    }

    // --- Pasar pagina ---

    @Test
    fun `tocar el tercio derecho pasa a la pagina siguiente`() {
        render()

        val primeraHoja = titulo()
        primeraHoja.performTouchInput { click(centerRight) }

        // La primera hoja se ha ido: el titulo de arriba del libro ya no esta.
        composeRule.onNodeWithText("LA CONCIENCIA").assertDoesNotExist()
    }

    @Test
    fun `tocar el tercio izquierdo vuelve a la pagina anterior`() {
        render()

        titulo().performTouchInput { click(centerRight) }
        composeRule.onNodeWithText("LA CONCIENCIA").assertDoesNotExist()

        // Un `pointerInput` con `detectTapGestures` NO anade accion de tocar a la
        // semantica, asi que no se puede filtrar por ella: se coge el primer parrafo que
        // haya en la hoja nueva, sea cual sea.
        composeRule.onAllNodesWithText("Párrafo número", substring = true)[0]
            .performTouchInput { click(centerLeft) }

        composeRule.onNodeWithText("LA CONCIENCIA").assertIsDisplayed()
    }

    @Test
    fun `los botones de pasar hoja hacen lo mismo que los tercios`() {
        render()
        despertar()

        composeRule.onNodeWithContentDescription("Página siguiente").performClick()

        composeRule.onNodeWithText("LA CONCIENCIA").assertDoesNotExist()
    }

    /** En la primera hoja no hay hoja anterior: el boton se apaga en vez de irse. */
    @Test
    fun `en la primera pagina no se puede retroceder`() {
        render()
        despertar()

        composeRule.onNodeWithContentDescription("Página anterior").performClick()

        composeRule.onNodeWithText("LA CONCIENCIA").assertIsDisplayed()
    }

    @Test
    fun `pasar pagina avisa de la nueva posicion para poder guardarla`() {
        render()
        positions.clear()

        titulo().performTouchInput { click(centerRight) }
        // `assertTrue` sobre una lista de Kotlin no sincroniza con Compose, al contrario
        // que las aserciones sobre nodos: sin esto se mira antes de que corra el efecto.
        composeRule.waitForIdle()

        assertTrue("tiene que avisar de por donde va", positions.isNotEmpty())
        assertTrue(positions.last() > ReadingPosition.Start)
    }

    @Test
    fun `se abre por donde se dejo`() {
        render(estado().copy(book = libro.copy(lastPosition = ReadingPosition(2, 0))))

        // El bloque 2 es "3. Libertad": la primera hoja empieza ahi, no en el principio.
        composeRule.onNodeWithText("3. Libertad").assertIsDisplayed()
        composeRule.onNodeWithText("LA CONCIENCIA").assertDoesNotExist()
    }

    // --- Marcar ---

    @Test
    fun `marcar una pagina cuesta un toque y guarda un trozo del texto`() {
        render()
        despertar()

        composeRule.onNodeWithText("MARCAR").performClick()

        val marca = addedBookmarks.single()
        assertEquals(ReadingPosition.Start, marca.first)
        assertEquals("LA CONCIENCIA", marca.second)
        assertEquals(33, marca.third)
    }

    /**
     * El boton tiene **un solo estado verdadero**: si en la hoja ya hay marca, la quita.
     * Sin esto, marcar dos veces la misma pagina dejaria dos entradas identicas.
     */
    @Test
    fun `sobre una pagina ya marcada el mismo boton la quita`() {
        render(
            estado(
                bookmarks = listOf(
                    Bookmark("m1", "uno", ReadingPosition.Start, "LA CONCIENCIA", 33, 1_000L),
                ),
            ),
        )
        despertar()

        composeRule.onNodeWithText("MARCADA").performClick()

        assertEquals(listOf("m1"), deletedBookmarks)
        assertTrue(addedBookmarks.isEmpty())
    }

    // --- Subrayar y anotar ---

    @Test
    fun `mantener pulsado senala la frase y ofrece que hacer con ella`() {
        render()

        pulsarLargoEn(parrafoLibertad)

        composeRule.onNodeWithText("SUBRAYAR").assertIsDisplayed()
        composeRule.onNodeWithText("NOTA").assertIsDisplayed()
        composeRule.onNodeWithText("MÁS").assertIsDisplayed()
        composeRule.onNodeWithText("CANCELAR").assertIsDisplayed()
    }

    @Test
    fun `subrayar guarda la frase entera, no el parrafo`() {
        render()

        pulsarLargoEn(parrafoLibertad)
        composeRule.onNodeWithText("SUBRAYAR").performClick()

        val subrayado = highlighted.single()
        assertEquals(1, subrayado.blockIndex)
        assertEquals("El hombre está condenado a ser libre.", subrayado.text)
        assertEquals(null, subrayado.note)
    }

    @Test
    fun `ampliar anade la frase siguiente antes de subrayar`() {
        render()

        pulsarLargoEn(parrafoLibertad)
        composeRule.onNodeWithText("MÁS").performClick()
        composeRule.onNodeWithText("SUBRAYAR").performClick()

        assertEquals(parrafoLibertad, highlighted.single().text)
    }

    @Test
    fun `escribir una nota subraya y anota de una vez`() {
        render()

        pulsarLargoEn(parrafoLibertad)
        composeRule.onNodeWithText("NOTA").performClick()
        composeRule.onNodeWithText("Escribe tu nota").performTextInput("Esto es el existencialismo")
        composeRule.onNodeWithText("GUARDAR").performClick()

        val subrayado = highlighted.single()
        assertEquals("El hombre está condenado a ser libre.", subrayado.text)
        assertEquals("Esto es el existencialismo", subrayado.note)
    }

    /**
     * Regresion encontrada en el dispositivo: un fondo opaco tapa el texto pero **no para
     * el dedo**. Tocar el campo de escribir la nota atravesaba hasta la hoja de debajo,
     * que se llevaba la seleccion por delante, y la nota no se podia escribir. Los mandos
     * tienen que tragarse sus propios toques.
     */
    @Test
    fun `tocar dentro de los mandos no atraviesa hasta la hoja`() {
        render()

        pulsarLargoEn(parrafoLibertad)
        composeRule.onNodeWithText("NOTA").performClick()
        composeRule.onNodeWithText("Escribe tu nota").performClick()

        // Si el toque hubiera atravesado, la seleccion estaria cancelada y con ella el
        // editor de la nota.
        composeRule.onNodeWithText("GUARDAR").assertIsDisplayed()
    }

    /** Sobre algo ya subrayado el mismo sitio quita: subrayar dos veces no significa nada. */
    @Test
    fun `mantener pulsado sobre un subrayado ofrece quitarlo`() {
        render(
            estado(
                highlights = listOf(
                    Highlight(
                        id = "s1",
                        bookId = "uno",
                        blockIndex = 1,
                        start = 0,
                        end = 37,
                        text = "El hombre está condenado a ser libre.",
                        note = null,
                        page = 33,
                        createdAtMillis = 1_000L,
                    ),
                ),
            ),
        )

        pulsarLargoEn(parrafoLibertad)
        composeRule.onNodeWithText("QUITAR").performClick()

        assertEquals(listOf("s1"), deletedHighlights)
    }

    @Test
    fun `cancelar deja el parrafo como estaba`() {
        render()

        pulsarLargoEn(parrafoLibertad)
        composeRule.onNodeWithText("CANCELAR").performClick()

        composeRule.onNodeWithText("SUBRAYAR").assertDoesNotExist()
        assertTrue(highlighted.isEmpty())
    }

    // --- Los paneles ---

    @Test
    fun `el indice ensena los capitulos y salta al elegido`() {
        render()
        despertar()

        composeRule.onNodeWithText("ÍNDICE").performClick()
        composeRule.onNodeWithText("3.1 Determinismo").assertIsDisplayed()

        // Se filtra por la accion de tocar: el titulo tambien existe **dentro del libro**,
        // y ese no lleva a ninguna parte.
        composeRule.onAllNodesWithText("3. Libertad")
            .filterToOne(hasClickAction())
            .performClick()

        // Saltar duerme la pantalla: quien elige un capitulo quiere verlo, no verlo
        // tapado por el indice del que salio.
        composeRule.onNodeWithText("AJUSTAR").assertDoesNotExist()
    }

    /**
     * Las dos secciones de la lista de marcas, con dos elementos en cada una: con claves
     * repetidas Compose lanza excepcion, y aqui eso deja el telefono sin pantalla de
     * inicio si el libro se abrio desde la home.
     */
    @Test
    fun `las marcas y los subrayados conviven en la misma lista`() {
        render(
            estado(
                bookmarks = listOf(
                    Bookmark("m1", "uno", ReadingPosition(0), "Primera marca", 33, 1_000L),
                    Bookmark("m2", "uno", ReadingPosition(2), "Segunda marca", 66, 2_000L),
                ),
                highlights = listOf(
                    Highlight("s1", "uno", 1, 0, 37, "Primer subrayado", null, 33, 1_000L),
                    Highlight("s2", "uno", 3, 0, 10, "Segundo subrayado", "Con nota", 70, 2_000L),
                ),
            ),
        )
        despertar()

        composeRule.onNodeWithText("MARCAS").performClick()

        composeRule.onNodeWithText("MARCAS DE PÁGINA").assertIsDisplayed()
        composeRule.onNodeWithText("Primera marca").assertIsDisplayed()
        composeRule.onNodeWithText("Segunda marca").assertIsDisplayed()
        // El panel esta acotado en alto a proposito —un indice de doscientas entradas no
        // puede comerse la pantalla—, asi que lo de abajo se trae a la vista.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Segundo subrayado"))
        composeRule.onNodeWithText("SUBRAYADO").assertExists()
        composeRule.onNodeWithText("Primer subrayado").assertExists()
        // La nota se lee debajo del fragmento, no en lugar de el.
        composeRule.onNodeWithText("Segundo subrayado").assertIsDisplayed()
        composeRule.onNodeWithText("Con nota").assertIsDisplayed()
    }

    @Test
    fun `sin nada marcado la lista lo dice`() {
        render()
        despertar()

        composeRule.onNodeWithText("MARCAS").performClick()

        composeRule.onNodeWithText(
            "Todavía no has marcado ni subrayado nada de este libro.",
        ).assertIsDisplayed()
    }

    @Test
    fun `los ajustes de forma llegan al ViewModel`() {
        render()
        despertar()

        composeRule.onNodeWithText("AJUSTAR").performClick()
        composeRule.onNodeWithContentDescription("Aumentar LETRA").performClick()
        composeRule.onNodeWithContentDescription("Reducir MÁRGENES").performClick()
        composeRule.onNodeWithText("SERIF").performClick()

        assertEquals(listOf(ReadingSettings.DEFAULT_TEXT + 1), textSteps)
        assertEquals(listOf(ReadingSettings.DEFAULT_MARGIN - 1), marginSteps)
        assertEquals(1, serifToggles)
    }

    @Test
    fun `los resultados de buscar llevan a su sitio del libro`() {
        render(
            estado(
                hits = listOf(
                    ReadingHit(blockIndex = 2, page = 66, snippet = "…condenado a ser libre…"),
                ),
            ),
        )
        despertar()

        composeRule.onNodeWithText("BUSCAR").performClick()

        composeRule.onNodeWithText("…condenado a ser libre…").assertIsDisplayed()
        composeRule.onNodeWithText("PÁGINA 67").assertIsDisplayed()
    }

    @Test
    fun `la barra de abajo dice el capitulo y la pagina`() {
        render()
        despertar()

        composeRule.onNodeWithText("PÁG. 34 / 342 · 0%").assertIsDisplayed()
        composeRule.onNodeWithText("2. La conciencia").assertIsDisplayed()
    }

    @Test
    fun `quitar el libro sale de la pantalla`() {
        render()
        despertar()

        composeRule.onNodeWithText("AJUSTAR").performClick()
        composeRule.onNodeWithText("QUITAR EL LIBRO").performClick()

        assertEquals(1, deletes)
    }
}
