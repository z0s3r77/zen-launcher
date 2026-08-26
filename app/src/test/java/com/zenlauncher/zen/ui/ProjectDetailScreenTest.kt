package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.Project
import com.zenlauncher.zen.presentation.notes.ProjectDetailScreen
import com.zenlauncher.zen.presentation.notes.ProjectDetailUiState
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
class ProjectDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ahora = 1_700_000_000_000L
    private val opened = mutableListOf<String>()
    private var marked = 0

    private fun render(state: ProjectDetailUiState) {
        composeRule.setContent {
            ZenTheme {
                ProjectDetailScreen(
                    state = state,
                    nowMillis = ahora,
                    onOpenNote = { opened += it },
                    onMarkDone = { marked++ },
                    onBack = {},
                    locale = Locale("es", "ES"),
                )
            }
        }
    }

    private fun proyecto(done: Boolean = false) = Project(
        id = "p1",
        title = "Un proyecto",
        createdAtMillis = ahora,
        done = done,
    )

    private fun nota(id: String, body: String) = Note(
        id = id,
        createdAtMillis = ahora,
        updatedAtMillis = ahora,
        body = body,
        projectId = "p1",
    )

    @Test
    fun `ensena el titulo y las notas del proyecto`() {
        render(
            ProjectDetailUiState(
                project = proyecto(),
                notes = listOf(nota("a", "Una idea del proyecto")),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("Un proyecto").assertIsDisplayed()
        composeRule.onNodeWithText("Una idea del proyecto").assertIsDisplayed()
    }

    @Test
    fun `tocar una nota la abre`() {
        render(
            ProjectDetailUiState(
                project = proyecto(),
                notes = listOf(nota("a", "Una idea del proyecto")),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("Una idea del proyecto").performClick()

        assertEquals(listOf("a"), opened)
    }

    @Test
    fun `marcar terminado esta a un toque y avisa`() {
        render(ProjectDetailUiState(project = proyecto(), loading = false))

        composeRule.onNodeWithText("Marcar terminado").performClick()

        assertEquals(1, marked)
    }

    @Test
    fun `un proyecto ya terminado no vuelve a ofrecer el boton`() {
        render(ProjectDetailUiState(project = proyecto(done = true), loading = false))

        composeRule.onNodeWithText("Marcar terminado").assertDoesNotExist()
    }

    @Test
    fun `un proyecto que desaparece bajo los pies no revienta la pantalla`() {
        render(ProjectDetailUiState(project = null, loading = false))

        composeRule.onNodeWithText("PROYECTO").assertIsDisplayed()
    }
}
