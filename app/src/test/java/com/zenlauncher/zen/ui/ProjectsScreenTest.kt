package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.notes.Project
import com.zenlauncher.zen.presentation.notes.ProjectRow
import com.zenlauncher.zen.presentation.notes.ProjectsScreen
import com.zenlauncher.zen.presentation.notes.ProjectsUiState
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ProjectsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<String>()
    private var backs = 0

    private fun render(state: ProjectsUiState) {
        composeRule.setContent {
            ZenTheme {
                ProjectsScreen(
                    state = state,
                    onOpenProject = { opened += it },
                    onBack = { backs++ },
                )
            }
        }
    }

    private fun proyecto(id: String, title: String, done: Boolean = false) = Project(
        id = id,
        title = title,
        createdAtMillis = 1_000L,
        done = done,
    )

    @Test
    fun `sin proyectos lo dice`() {
        render(ProjectsUiState(loading = false))

        composeRule.onNodeWithText("TODAVÍA NO HAY PROYECTOS").assertIsDisplayed()
    }

    @Test
    fun `cada proyecto ensena su conteo de notas`() {
        render(
            ProjectsUiState(
                projects = listOf(ProjectRow(proyecto("p1", "Un proyecto"), noteCount = 3)),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("Un proyecto").assertIsDisplayed()
        composeRule.onNodeWithText("3 notas").assertIsDisplayed()
    }

    @Test
    fun `un proyecto terminado lo dice en vez del conteo`() {
        render(
            ProjectsUiState(
                projects = listOf(ProjectRow(proyecto("p1", "Un proyecto", done = true), noteCount = 3)),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("TERMINADO").assertIsDisplayed()
        composeRule.onNodeWithText("3 notas").assertDoesNotExist()
    }

    @Test
    fun `tocar un proyecto lo abre`() {
        render(
            ProjectsUiState(
                projects = listOf(ProjectRow(proyecto("p1", "Un proyecto"), noteCount = 1)),
                loading = false,
            ),
        )

        composeRule.onNodeWithText("Un proyecto").performClick()

        assertEquals(listOf("p1"), opened)
    }
}
