package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.zenlauncher.zen.domain.notes.IdeaPrompts
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.presentation.notes.DevelopIdeaScreen
import com.zenlauncher.zen.presentation.notes.DevelopIdeaUiState
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
class DevelopIdeaScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ahora = 1_700_000_000_000L
    private val opened = mutableListOf<String>()
    private var saves = 0
    private var projects = mutableListOf<String>()
    private var backs = 0
    private var savedNavigations = 0
    private var ideaEscrita = ""

    private fun render(state: DevelopIdeaUiState, ideaText: String = "") {
        ideaEscrita = ideaText
        composeRule.setContent {
            ZenTheme {
                DevelopIdeaScreen(
                    state = state,
                    ideaText = ideaEscrita,
                    nowMillis = ahora,
                    onIdeaChange = { ideaEscrita = it },
                    onDictate = {},
                    onOpenNote = { opened += it },
                    onSave = { saves++ },
                    onConvertToProject = { projects += it },
                    onBack = { backs++ },
                    onSaved = { savedNavigations++ },
                    locale = Locale("es", "ES"),
                )
            }
        }
    }

    private fun nota(id: String, body: String) = Note(
        id = id,
        createdAtMillis = ahora,
        updatedAtMillis = ahora,
        body = body,
    )

    @Test
    fun `sin ninguna seccion con datos no se pinta ninguna`() {
        render(DevelopIdeaUiState(loading = false))

        composeRule.onNodeWithText("PREGUNTA CENTRAL").assertDoesNotExist()
        composeRule.onNodeWithText("ENFOQUES").assertDoesNotExist()
        composeRule.onNodeWithText("PREGUNTAS").assertDoesNotExist()
    }

    @Test
    fun `la pregunta central solo se pinta si existe`() {
        render(
            DevelopIdeaUiState(
                prompts = IdeaPrompts(centralQuestion = "¿Qué papel juega «escrib» en esta idea?"),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("PREGUNTA CENTRAL").assertIsDisplayed()
        composeRule.onNodeWithText("¿Qué papel juega «escrib» en esta idea?").assertIsDisplayed()
    }

    @Test
    fun `los enfoques solo se pintan si hay alguno`() {
        render(
            DevelopIdeaUiState(prompts = IdeaPrompts(approaches = listOf("Tecnológico")), loading = false),
        )

        composeRule.onNodeWithText("ENFOQUES").assertIsDisplayed()
        composeRule.onNodeWithText("Tecnológico").assertIsDisplayed()
    }

    @Test
    fun `las conexiones solo se pintan si hay notas relacionadas`() {
        render(
            DevelopIdeaUiState(related = listOf(nota("a", "Una idea parecida")), loading = false),
        )

        composeRule.onNodeWithText("Se relaciona con 1 nota anterior").assertIsDisplayed()
        composeRule.onNodeWithText("Una idea parecida").performClick()

        assertEquals(listOf("a"), opened)
    }

    @Test
    fun `guardar esta siempre disponible con texto escrito`() {
        render(DevelopIdeaUiState(loading = false), ideaText = "Una idea")

        composeRule.onNodeWithText("Guardar").performClick()

        assertEquals(1, saves)
    }

    @Test
    fun `sin texto no aparece guardar`() {
        render(DevelopIdeaUiState(loading = false), ideaText = "")

        composeRule.onNodeWithText("Guardar").assertDoesNotExist()
    }

    @Test
    fun `convertir en proyecto solo aparece con tres o mas relacionadas`() {
        render(
            DevelopIdeaUiState(
                related = listOf(nota("a", "Una"), nota("b", "Otra"), nota("c", "Tercera")),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("Convertir en proyecto").assertIsDisplayed()
    }

    @Test
    fun `con menos de tres relacionadas no aparece convertir en proyecto`() {
        render(
            DevelopIdeaUiState(related = listOf(nota("a", "Una"), nota("b", "Otra")), loading = false),
        )

        composeRule.onNodeWithText("Convertir en proyecto").assertDoesNotExist()
    }

    @Test
    fun `convertir en proyecto pide un titulo y lo manda al confirmar`() {
        render(
            DevelopIdeaUiState(
                related = listOf(nota("a", "Una"), nota("b", "Otra"), nota("c", "Tercera")),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("Convertir en proyecto").performClick()
        composeRule.onNodeWithText("Título del proyecto").performTextInput("Mi proyecto")
        composeRule.onNodeWithText("Crear proyecto").performClick()

        assertEquals(listOf("Mi proyecto"), projects)
    }

    @Test
    fun `guardar navega fuera de la pantalla`() {
        render(DevelopIdeaUiState(saved = true, loading = false))

        assertEquals(1, savedNavigations)
    }
}
