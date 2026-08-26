package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.domain.notes.NoteStage
import com.zenlauncher.zen.domain.notes.Project
import com.zenlauncher.zen.fakes.FakeNotesRepository
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.fakes.testNote
import com.zenlauncher.zen.presentation.notes.ProjectDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `ensena el proyecto y sus notas`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("a").copy(projectId = "p1", stage = NoteStage.PROJECT),
                testNote("b").copy(projectId = "p1", stage = NoteStage.PROJECT),
                testNote("c"),
            ),
        )
        repository.saveProject(Project(id = "p1", title = "Un proyecto", createdAtMillis = 1_000L))
        val model = ProjectDetailViewModel(repository, this)

        model.state.test {
            model.open("p1")

            var estado = awaitItem()
            while (estado.loading || estado.project == null) estado = awaitItem()

            assertEquals("Un proyecto", estado.project?.title)
            assertEquals(setOf("a", "b"), estado.notes.map { it.id }.toSet())
        }
    }

    @Test
    fun `marcar terminado pone el proyecto y sus notas como terminados`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("a").copy(projectId = "p1", stage = NoteStage.PROJECT),
                testNote("b").copy(projectId = "p1", stage = NoteStage.PROJECT),
            ),
        )
        repository.saveProject(Project(id = "p1", title = "Un proyecto", createdAtMillis = 1_000L))
        val model = ProjectDetailViewModel(repository, this)
        model.open("p1")
        runCurrent()

        model.markDone()
        runCurrent()

        val proyecto = repository.observeProjects().first().single()
        assertTrue(proyecto.done)
        assertTrue(repository.notesInProject("p1").all { it.stage == NoteStage.DONE })
    }
}
