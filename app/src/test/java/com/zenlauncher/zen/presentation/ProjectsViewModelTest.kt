package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.domain.notes.Project
import com.zenlauncher.zen.fakes.FakeNotesRepository
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.fakes.testNote
import com.zenlauncher.zen.presentation.notes.ProjectsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProjectsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `sin proyectos la lista se declara vacia`() = runTest {
        val model = ProjectsViewModel(FakeNotesRepository())

        model.state.test {
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            assertTrue(estado.empty)
        }
    }

    @Test
    fun `cada proyecto lleva el conteo real de sus notas`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("a").copy(projectId = "p1"),
                testNote("b").copy(projectId = "p1"),
                testNote("c"),
            ),
        )
        repository.saveProject(Project(id = "p1", title = "Un proyecto", createdAtMillis = 1_000L))
        val model = ProjectsViewModel(repository)

        model.state.test {
            var estado = awaitItem()
            while (estado.loading || estado.projects.isEmpty()) estado = awaitItem()

            val fila = estado.projects.single()
            assertEquals("Un proyecto", fila.project.title)
            assertEquals(2, fila.noteCount)
        }
    }

    @Test
    fun `un proyecto sin notas todavia cuenta cero, no desaparece`() = runTest {
        val repository = FakeNotesRepository()
        repository.saveProject(Project(id = "p1", title = "Recien creado", createdAtMillis = 1_000L))
        val model = ProjectsViewModel(repository)

        model.state.test {
            var estado = awaitItem()
            while (estado.loading || estado.projects.isEmpty()) estado = awaitItem()

            assertEquals(0, estado.projects.single().noteCount)
        }
    }
}
