package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.domain.notes.HeuristicIdeaDevelopmentModel
import com.zenlauncher.zen.domain.notes.LexicalEmbedder
import com.zenlauncher.zen.domain.notes.NoteIndexer
import com.zenlauncher.zen.domain.notes.NoteStage
import com.zenlauncher.zen.fakes.FakeDictation
import com.zenlauncher.zen.fakes.FakeNotesRepository
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.fakes.testNote
import com.zenlauncher.zen.presentation.notes.DevelopIdeaViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevelopIdeaViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeZenClock(wall = 1_700_000_000_000)
    private val dictation = FakeDictation()

    private fun indexer(repository: FakeNotesRepository) = NoteIndexer(repository, LexicalEmbedder(), clock)

    private fun model(
        repository: FakeNotesRepository,
        appScope: CoroutineScope,
        indexer: NoteIndexer = indexer(repository),
        voice: FakeDictation = dictation,
    ) = DevelopIdeaViewModel(
        notes = repository,
        indexer = indexer,
        ideaDevelopment = HeuristicIdeaDevelopmentModel(),
        dictation = voice,
        clock = clock,
        appScope = appScope,
    )

    @Test
    fun `abrir una nota existente precarga su texto`() = runTest {
        val repository = FakeNotesRepository(listOf(testNote("a", body = "Una idea de antes")))
        val model = model(repository, this)

        model.open("a")
        runCurrent()

        assertEquals("Una idea de antes", model.ideaText.value)
    }

    @Test
    fun `una idea nueva empieza en blanco`() = runTest {
        assertEquals("", model(FakeNotesRepository(), this).ideaText.value)
    }

    @Test
    fun `el campo de idea no pierde letras aunque el resto del estado tarde`() = runTest {
        // Mismo motivo que en NotesViewModel: `state` viaja por mapLatest y una
        // busqueda por significado, asi que llega tarde.
        val model = model(FakeNotesRepository(), this)

        "idea".forEachIndexed { index, _ ->
            model.onIdeaChange("idea".take(index + 1))
            assertEquals("idea".take(index + 1), model.ideaText.value)
        }
    }

    @Test
    fun `las notas parecidas a la idea salen como relacionadas`() = runTest {
        val repository = FakeNotesRepository(listOf(testNote("parecida", body = "Ya nadie sabe aburrirse")))
        val idx = indexer(repository)
        idx.sync()
        val model = model(repository, this, indexer = idx)

        model.state.test {
            awaitItem().takeIf { !it.loading } ?: awaitItem()
            model.onIdeaChange("aburrimiento")

            val estado = awaitItem()
            assertEquals(listOf("parecida"), estado.related.map { it.id })
        }
    }

    @Test
    fun `una nota abierta no aparece entre sus propias relacionadas`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("origen", body = "El aburrimiento es necesario"),
                testNote("parecida", body = "Ya nadie sabe aburrirse"),
            ),
        )
        val idx = indexer(repository)
        idx.sync()
        val model = model(repository, this, indexer = idx)
        model.open("origen")
        runCurrent()

        model.state.test {
            var estado = awaitItem()
            while (estado.loading) estado = awaitItem()

            assertEquals(listOf("parecida"), estado.related.map { it.id })
        }
    }

    @Test
    fun `guardar una idea nueva crea una nota ya desarrollada`() = runTest {
        val repository = FakeNotesRepository()
        val model = model(repository, this)

        model.onIdeaChange("Escribir sobre el aburrimiento")
        model.saveAsNote()
        runCurrent()

        val guardada = repository.saved.first()
        assertEquals("Escribir sobre el aburrimiento", guardada.body)
        assertEquals(NoteStage.DEVELOPED, guardada.stage)
    }

    @Test
    fun `guardar marca la salida`() = runTest {
        val model = model(FakeNotesRepository(), this)

        model.state.test {
            awaitItem().takeIf { !it.loading } ?: awaitItem()
            model.onIdeaChange("Una idea")

            var estado = awaitItem()
            while (estado.saved) estado = awaitItem()

            model.saveAsNote()

            estado = awaitItem()
            while (!estado.saved) estado = awaitItem()

            assertTrue(estado.saved)
        }
    }

    @Test
    fun `guardar una idea que vino de una nota existente la actualiza en vez de duplicarla`() = runTest {
        val repository = FakeNotesRepository(listOf(testNote("a", body = "Idea a medias")))
        val model = model(repository, this)
        model.open("a")
        runCurrent()

        model.onIdeaChange("Idea ya mas trabajada")
        model.saveAsNote()
        runCurrent()

        assertEquals(1, repository.saved.size)
        val guardada = repository.saved.first()
        assertEquals("a", guardada.id)
        assertEquals("Idea ya mas trabajada", guardada.body)
        assertEquals(NoteStage.DEVELOPED, guardada.stage)
    }

    @Test
    fun `una idea en blanco no se guarda`() = runTest {
        val repository = FakeNotesRepository()
        val model = model(repository, this)

        model.saveAsNote()
        runCurrent()

        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `convertir en proyecto exige al menos tres notas relacionadas`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("una", body = "Ya nadie sabe aburrirse"),
                testNote("otra", body = "Ya nadie sabe aburrirse"),
            ),
        )
        val idx = indexer(repository)
        idx.sync()
        val model = model(repository, this, indexer = idx)

        model.onIdeaChange("aburrimiento")
        runCurrent()
        model.convertToProject("Un proyecto sobre el aburrimiento")
        runCurrent()

        assertTrue(repository.observeProjects().first().isEmpty())
    }

    @Test
    fun `convertir en proyecto con tres relacionadas las agrupa a todas`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("una", body = "Ya nadie sabe aburrirse"),
                testNote("otra", body = "Ya nadie sabe aburrirse"),
                testNote("tercera", body = "Ya nadie sabe aburrirse"),
            ),
        )
        val idx = indexer(repository)
        idx.sync()
        val model = model(repository, this, indexer = idx)

        model.onIdeaChange("aburrimiento")
        runCurrent()
        model.convertToProject("El aburrimiento")
        runCurrent()

        val proyecto = repository.observeProjects().first().single()
        assertEquals("El aburrimiento", proyecto.title)

        // La idea misma se guarda como nota y se une al proyecto junto con las tres
        // relacionadas: "para la nota actual + las relacionadas", como dice el plan.
        val ideaGuardada = repository.saved.first { it.body == "aburrimiento" }
        val enProyecto = repository.notesInProject(proyecto.id)
        assertEquals(setOf("una", "otra", "tercera", ideaGuardada.id), enProyecto.map { it.id }.toSet())
        assertTrue(enProyecto.all { it.stage == NoteStage.PROJECT })
        assertEquals(proyecto.id, ideaGuardada.projectId)
        assertEquals(NoteStage.PROJECT, ideaGuardada.stage)
    }

    @Test
    fun `el texto dictado se une a la idea sin perder lo ya escrito`() = runTest {
        val model = model(FakeNotesRepository(), this)

        model.onIdeaChange("Idea:")
        model.toggleDictation()
        runCurrent()
        dictation.settle("hacer un video sobre el aburrimiento")
        runCurrent()

        assertEquals("Idea: hacer un video sobre el aburrimiento", model.ideaText.value)
    }

    @Test
    fun `denegar el microfono se apunta como estado`() = runTest {
        val model = model(FakeNotesRepository(), this)

        model.state.test {
            awaitItem().takeIf { !it.loading } ?: awaitItem()
            model.onMicrophoneDenied()

            val estado = awaitItem()
            assertTrue(estado.micDenied)
            assertFalse(estado.listening)
        }
    }
}
