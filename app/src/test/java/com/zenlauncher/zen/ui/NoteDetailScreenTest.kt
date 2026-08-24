package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.notes.AttachmentKind
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.NoteAttachment
import com.zenlauncher.zen.domain.notes.NoteLink
import com.zenlauncher.zen.domain.notes.LinkOrigin
import com.zenlauncher.zen.domain.notes.LinkState
import com.zenlauncher.zen.presentation.notes.ConnectedNote
import com.zenlauncher.zen.presentation.notes.NoteDetailScreen
import com.zenlauncher.zen.presentation.notes.NoteDetailUiState
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class NoteDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ahora = 1_700_000_000_000
    private val opened = mutableListOf<String>()
    private val openedNotes = mutableListOf<String>()
    private val accepted = mutableListOf<NoteLink>()
    private val ignored = mutableListOf<NoteLink>()
    private var deletes = 0

    private fun enlace(url: String) = NoteAttachment(
        id = url,
        noteId = "a",
        kind = AttachmentKind.LINK,
        value = url,
        createdAtMillis = 1_000L,
    )

    private fun render(state: NoteDetailUiState) {
        composeRule.setContent {
            ZenTheme {
                NoteDetailScreen(
                    state = state,
                    nowMillis = ahora,
                    onOpenLink = { opened += it },
                    onOpenNote = { openedNotes += it },
                    onAccept = { accepted += it },
                    onIgnore = { ignored += it },
                    onDelete = { deletes++ },
                    onBack = {},
                    locale = Locale("es", "ES"),
                )
            }
        }
    }

    private fun nota(
        body: String = "Hemos perdido la capacidad de aburrirnos",
        title: String? = null,
        tags: List<String> = emptyList(),
        attachments: List<NoteAttachment> = emptyList(),
    ) = Note(
        id = "a",
        createdAtMillis = ahora,
        updatedAtMillis = ahora,
        body = body,
        attachments = attachments,
        title = title,
        tags = tags,
    )

    @Test
    fun `ensena el texto de la nota`() {
        render(NoteDetailUiState(note = nota(), loading = false))

        composeRule.onNodeWithText("Hemos perdido la capacidad de aburrirnos").assertIsDisplayed()
    }

    @Test
    fun `sin titulo generado no pinta un hueco esperando a la IA`() {
        // Un marcador de ausencia solo informa de que a la aplicacion le falta algo.
        render(NoteDetailUiState(note = nota(), loading = false))

        composeRule.onNodeWithText("Sin título").assertDoesNotExist()
        composeRule.onNodeWithText("ETIQUETAS").assertDoesNotExist()
    }

    @Test
    fun `con titulo generado lo ensena encima del texto`() {
        render(NoteDetailUiState(note = nota(title = "El aburrimiento"), loading = false))

        composeRule.onNodeWithText("El aburrimiento").assertIsDisplayed()
        composeRule.onNodeWithText("Hemos perdido la capacidad de aburrirnos").assertIsDisplayed()
    }

    @Test
    fun `los enlaces se abren al tocarlos`() {
        render(
            NoteDetailUiState(
                note = nota(attachments = listOf(enlace("https://ejemplo.es/a"))),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("ENLACES").assertIsDisplayed()
        composeRule.onNodeWithText("https://ejemplo.es/a").performClick()

        assertEquals(listOf("https://ejemplo.es/a"), opened)
    }

    @Test
    fun `sin enlaces no aparece el rotulo de enlaces`() {
        render(NoteDetailUiState(note = nota(), loading = false))

        composeRule.onNodeWithText("ENLACES").assertDoesNotExist()
    }

    @Test
    fun `las etiquetas solo se pintan si las hay`() {
        render(NoteDetailUiState(note = nota(tags = listOf("atencion", "moviles")), loading = false))

        composeRule.onNodeWithText("ETIQUETAS").assertIsDisplayed()
        composeRule.onNodeWithText("ATENCION  ·  MOVILES").assertIsDisplayed()
    }

    @Test
    fun `borrar esta a un toque y avisa`() {
        render(NoteDetailUiState(note = nota(), loading = false))

        composeRule.onNodeWithText("Borrar la nota").performClick()

        assertEquals(1, deletes)
    }

    private fun conectada(id: String, titulo: String, state: LinkState) = ConnectedNote(
        note = Note(
            id = id,
            createdAtMillis = ahora,
            updatedAtMillis = ahora,
            body = titulo,
        ),
        link = NoteLink("a", id, 0.5f, LinkOrigin.SUGGESTED, state, ahora),
    )

    @Test
    fun `sin conexiones no aparece ninguna de las dos secciones`() {
        render(NoteDetailUiState(note = nota(), loading = false))

        composeRule.onNodeWithText("CONECTADA CON").assertDoesNotExist()
        composeRule.onNodeWithText("¿SE PARECE A ESTAS?").assertDoesNotExist()
    }

    @Test
    fun `una propuesta se pregunta y ofrece las dos respuestas`() {
        // El indice no sabe si dos ideas son la misma, solo que se parecen. Quien lo
        // sabe es quien las escribio, asi que el rotulo pregunta en vez de afirmar.
        render(
            NoteDetailUiState(
                note = nota(),
                suggestions = listOf(conectada("b", "La gente ya no sabe aburrirse", LinkState.PENDING)),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("¿SE PARECE A ESTAS?").assertIsDisplayed()
        composeRule.onNodeWithText("La gente ya no sabe aburrirse").assertIsDisplayed()
        composeRule.onNodeWithText("Conectar").assertIsDisplayed()
        composeRule.onNodeWithText("Ignorar").assertIsDisplayed()
    }

    @Test
    fun `aceptar una propuesta avisa con el enlace`() {
        val propuesta = conectada("b", "Otra idea", LinkState.PENDING)
        render(NoteDetailUiState(note = nota(), suggestions = listOf(propuesta), loading = false))

        composeRule.onNodeWithText("Conectar").performClick()

        assertEquals(listOf(propuesta.link), accepted)
    }

    @Test
    fun `ignorar una propuesta avisa con el enlace`() {
        val propuesta = conectada("b", "Otra idea", LinkState.PENDING)
        render(NoteDetailUiState(note = nota(), suggestions = listOf(propuesta), loading = false))

        composeRule.onNodeWithText("Ignorar").performClick()

        assertEquals(listOf(propuesta.link), ignored)
    }

    @Test
    fun `una conexion aceptada ya no pregunta nada`() {
        // Ya esta respondida: es parte de la nota, no algo pendiente.
        render(
            NoteDetailUiState(
                note = nota(),
                connections = listOf(conectada("b", "Idea conectada", LinkState.ACCEPTED)),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("CONECTADA CON").assertIsDisplayed()
        composeRule.onNodeWithText("Conectar").assertDoesNotExist()
        composeRule.onNodeWithText("Ignorar").assertDoesNotExist()
    }

    @Test
    fun `tocar una nota conectada la abre`() {
        render(
            NoteDetailUiState(
                note = nota(),
                connections = listOf(conectada("b", "Idea conectada", LinkState.ACCEPTED)),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("Idea conectada").performClick()

        assertEquals(listOf("b"), openedNotes)
    }

    @Test
    fun `una nota que desaparece bajo los pies no revienta la pantalla`() {
        // Al borrarla, el flujo reemite null antes de que la navegacion cierre la
        // pantalla: ese fotograma intermedio existe y hay que dibujarlo.
        render(NoteDetailUiState(note = null, loading = false))

        composeRule.onNodeWithText("NOTA").assertIsDisplayed()
        composeRule.onNodeWithText("Borrar la nota").assertDoesNotExist()
    }
}
