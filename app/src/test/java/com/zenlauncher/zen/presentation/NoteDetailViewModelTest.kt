package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.domain.notes.Project
import com.zenlauncher.zen.fakes.FakeAttachmentStore
import com.zenlauncher.zen.fakes.FakeNotesRepository
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.fakes.testNote
import com.zenlauncher.zen.presentation.notes.NoteDetailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeZenClock(wall = 1_700_000_000_000)
    private val attachments = FakeAttachmentStore()

    private fun model(repository: FakeNotesRepository, appScope: CoroutineScope) =
        NoteDetailViewModel(repository, attachments, clock, appScope)

    @Test
    fun `asignar a un proyecto existente lo reflejan la nota y su proyecto`() = runTest {
        val repository = FakeNotesRepository(listOf(testNote("a")))
        repository.saveProject(Project(id = "p1", title = "Un proyecto", createdAtMillis = 1_000L))
        val model = model(repository, this)
        model.open("a")
        runCurrent()

        model.assignToProject("p1")
        runCurrent()

        assertEquals("p1", repository.saved.first { it.id == "a" }.projectId)
    }

    @Test
    fun `crear un proyecto nuevo lo crea y asigna la nota de una vez`() = runTest {
        val repository = FakeNotesRepository(listOf(testNote("a")))
        val model = model(repository, this)
        model.open("a")
        runCurrent()

        model.createProjectAndAssign("Idea grande")
        runCurrent()

        val proyecto = repository.observeProjects().first().single()
        assertEquals("Idea grande", proyecto.title)
        assertEquals(proyecto.id, repository.saved.first { it.id == "a" }.projectId)
    }

    @Test
    fun `crear un proyecto con titulo en blanco no hace nada`() = runTest {
        val repository = FakeNotesRepository(listOf(testNote("a")))
        val model = model(repository, this)
        model.open("a")
        runCurrent()

        model.createProjectAndAssign("   ")
        runCurrent()

        assertNull(repository.saved.first { it.id == "a" }.projectId)
    }

    @Test
    fun `el estado ofrece el proyecto de la nota ya resuelto`() = runTest {
        val repository = FakeNotesRepository(listOf(testNote("a")))
        repository.saveProject(Project(id = "p1", title = "Un proyecto", createdAtMillis = 1_000L))
        repository.assignToProject("a", "p1")
        val model = model(repository, this)

        model.state.test {
            model.open("a")

            var estado = awaitItem()
            while (estado.loading || estado.currentProject == null) estado = awaitItem()

            assertEquals("Un proyecto", estado.currentProject?.title)
        }
    }
}
