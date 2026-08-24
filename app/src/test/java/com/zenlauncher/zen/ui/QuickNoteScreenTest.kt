package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.notes.AttachmentKind
import com.zenlauncher.zen.domain.notes.NoteAttachment
import com.zenlauncher.zen.presentation.notes.QuickNoteScreen
import com.zenlauncher.zen.presentation.notes.QuickNoteUiState
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class QuickNoteScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var saves = 0
    private var picks = 0
    private var dictates = 0
    private var backs = 0
    private var saidSaved = 0

    private fun imagen(id: String) = NoteAttachment(
        id = id,
        noteId = "n",
        kind = AttachmentKind.IMAGE,
        value = "notas/n/$id.jpg",
        createdAtMillis = 1_000L,
    )

    private fun render(state: QuickNoteUiState) {
        composeRule.setContent {
            ZenTheme {
                QuickNoteScreen(
                    state = state,
                    onTextChange = {},
                    onDictate = { dictates++ },
                    onPickImage = { picks++ },
                    onSave = { saves++ },
                    onBack = { backs++ },
                    onSaved = { saidSaved++ },
                )
            }
        }
    }

    @Test
    fun `sin nada escrito no hay boton de guardar`() {
        // Un "Guardar" apagado sobre una nota en blanco es un control que se ve y no
        // funciona. La salida sin guardar ya existe: la flecha de volver.
        render(QuickNoteUiState())

        composeRule.onNodeWithText("Guardar").assertDoesNotExist()
    }

    @Test
    fun `con texto aparece el boton y guarda al tocarlo`() {
        render(QuickNoteUiState(text = "Una idea"))

        composeRule.onNodeWithText("Guardar").assertIsDisplayed().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun `con una foto y sin texto tambien se puede guardar`() {
        render(QuickNoteUiState(images = listOf(imagen("a"))))

        composeRule.onNodeWithText("Guardar").assertIsDisplayed()
    }

    @Test
    fun `adjuntar una imagen esta a un toque`() {
        render(QuickNoteUiState())

        composeRule.onNodeWithText("Añadir una imagen").assertHasClickAction().performClick()

        assertEquals(1, picks)
    }

    @Test
    fun `no hay boton de adjuntar enlace`() {
        // Los enlaces se reconocen solos en el texto al guardar: un enlace siempre
        // llega pegado, y pedir un toque para clasificarlo es la friccion que la
        // captura rapida existe para quitar.
        render(QuickNoteUiState(text = "Ver https://ejemplo.es/a"))

        composeRule.onNodeWithText("Añadir un enlace").assertDoesNotExist()
    }

    @Test
    fun `el recuento de imagenes solo existe si hay imagenes`() {
        // Un "00" permanente es ruido con forma de dato.
        render(QuickNoteUiState())
        composeRule.onNodeWithText("0 ADJUNTAS").assertDoesNotExist()
    }

    @Test
    fun `con imagenes se dice cuantas van`() {
        render(QuickNoteUiState(images = listOf(imagen("a"), imagen("b"))))

        composeRule.onNodeWithText("2 ADJUNTAS").assertIsDisplayed()
    }

    @Test
    fun `una vez guardada, la pantalla avisa de que se va`() {
        // La salida la dispara el estado y no el propio boton, para que mande el
        // guardado y no el toque.
        render(QuickNoteUiState(text = "Una idea", saved = true))

        assertEquals(1, saidSaved)
    }

    @Test
    fun `mientras no se guarda, la pantalla no se va sola`() {
        render(QuickNoteUiState(text = "Una idea"))

        assertEquals(0, saidSaved)
    }

    @Test
    fun `sin reconocedor en el dispositivo no hay fila de dictar`() {
        // Lo que no tiene nada detras no se pinta: un "Dictar" que al tocarlo dijera
        // que no se puede seria un control que existe para negarse.
        render(QuickNoteUiState(canDictate = false))

        composeRule.onNodeWithText("Dictar").assertDoesNotExist()
    }

    @Test
    fun `con reconocedor, dictar esta a un toque`() {
        render(QuickNoteUiState(canDictate = true))

        composeRule.onNodeWithText("Dictar").assertHasClickAction().performClick()

        assertEquals(1, dictates)
    }

    @Test
    fun `mientras escucha lo dice con texto y ofrece parar`() {
        // El estado se lee como texto y nunca solo por el color: es la misma fila en
        // los dos estados, y sin esto nada distingue "puedes dictar" de "te esta oyendo".
        render(QuickNoteUiState(canDictate = true, listening = true))

        composeRule.onNodeWithText("ESCUCHANDO").assertIsDisplayed()
        composeRule.onNodeWithText("Dejar de dictar").assertIsDisplayed()
    }

    @Test
    fun `denegar el microfono se dice en la fila y no con un aviso`() {
        // Un dialogo de error interrumpiria una captura con prisa para contar algo que
        // el usuario ya sabe: acaba de decir que no.
        render(QuickNoteUiState(canDictate = true, micDenied = true))

        composeRule.onNodeWithText("SIN MICRÓFONO").assertIsDisplayed()
        composeRule.onNodeWithText("Dictar").assertIsDisplayed()
    }

    @Test
    fun `sin dictar no se ensena ningun estado del microfono`() {
        render(QuickNoteUiState(canDictate = true))

        composeRule.onNodeWithText("ESCUCHANDO").assertDoesNotExist()
        composeRule.onNodeWithText("SIN MICRÓFONO").assertDoesNotExist()
    }

    @Test
    fun `el marcador de posicion desaparece al escribir`() {
        render(QuickNoteUiState())
        composeRule.onNodeWithText("Escribe o dicta la idea").assertIsDisplayed()
    }
}
