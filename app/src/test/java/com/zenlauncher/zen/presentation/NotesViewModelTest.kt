package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.domain.notes.LexicalEmbedder
import com.zenlauncher.zen.domain.notes.LinkOrigin
import com.zenlauncher.zen.domain.notes.LinkState
import com.zenlauncher.zen.domain.notes.NoteIndexer
import com.zenlauncher.zen.domain.notes.NoteLink
import com.zenlauncher.zen.domain.notes.RecurringCluster
import com.zenlauncher.zen.domain.notes.RecurringThemes
import com.zenlauncher.zen.fakes.FakeNotesRepository
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.fakes.testNote
import com.zenlauncher.zen.presentation.notes.NotesViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeZenClock(wall = 1_700_000_000_000)

    private fun TestScope.viewModel(repository: FakeNotesRepository) = NotesViewModel(
        notes = repository,
        indexer = NoteIndexer(repository, LexicalEmbedder(), clock),
        clock = clock,
        appScope = this,
    )

    @Test
    fun `lista las notas de la mas reciente a la mas antigua`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("vieja", createdAt = 1_000L),
                testNote("nueva", createdAt = 9_000L),
            ),
        )

        viewModel(repository).state.test {
            val cargada = awaitItem().takeIf { !it.loading } ?: awaitItem()

            assertEquals(listOf("nueva", "vieja"), cargada.notes.map { it.id })
            assertEquals(2, cargada.total)
        }
    }

    @Test
    fun `el buscador no pierde letras aunque el filtro tarde`() = runTest {
        // Regresion encontrada probando en el dispositivo: el campo leia el texto que
        // volvia del filtro, y ese viaja por mapLatest y una consulta a SQLite. Mientras
        // volvia, el campo seguia ensenando lo anterior y la tecla siguiente se aplicaba
        // sobre un valor viejo: teclear "aburri" dejaba "buar". Escribiendo DESPACIO,
        // que es como lo hace una persona.
        val repository = FakeNotesRepository(listOf(testNote("a", body = "El aburrimiento")))
        val model = viewModel(repository)

        // Sin dejar correr el planificador entre teclas: es justo la ventana en la que
        // el filtro todavia no ha contestado.
        "aburri".forEachIndexed { index, letra ->
            model.onQueryChange("aburri".take(index + 1))
            assertEquals("aburri".take(index + 1), model.query.value)
        }

        assertEquals("aburri", model.query.value)
    }

    @Test
    fun `buscar filtra sin tocar el total de la cabecera`() = runTest {
        // El numero de la franja dice cuantas notas hay, no cuantas se estan viendo:
        // si bajara al escribir, pareceria que buscar borra notas.
        val repository = FakeNotesRepository(
            listOf(
                testNote("a", body = "Hemos perdido el aburrimiento", createdAt = 1_000L),
                testNote("b", body = "Comprar pan", createdAt = 2_000L),
            ),
        )
        val model = viewModel(repository)

        model.state.test {
            awaitItem().takeIf { !it.loading } ?: awaitItem()

            model.onQueryChange("aburrimiento")
            val filtrada = awaitItem()

            assertEquals(listOf("a"), filtrada.notes.map { it.id })
            assertEquals(2, filtrada.total)
            assertTrue(filtrada.searching)
        }
    }

    @Test
    fun `una biblioteca vacia y una busqueda sin resultados no son lo mismo`() = runTest {
        // Se dicen distinto a proposito: "todavia no hay notas" invita a escribir la
        // primera, y "nada con esas palabras" dice que hay notas pero no esas.
        val repository = FakeNotesRepository(listOf(testNote("a", body = "Comprar pan")))
        val model = viewModel(repository)

        model.state.test {
            val cargada = awaitItem().takeIf { !it.loading } ?: awaitItem()
            assertFalse(cargada.empty)
            assertFalse(cargada.noResults)

            model.onQueryChange("aburrimiento")
            val sinResultados = awaitItem()

            assertTrue(sinResultados.noResults)
            assertFalse(sinResultados.empty)
        }
    }

    @Test
    fun `sin notas la pantalla se declara vacia y no como busqueda fallida`() = runTest {
        viewModel(FakeNotesRepository()).state.test {
            val cargada = awaitItem().takeIf { !it.loading } ?: awaitItem()

            assertTrue(cargada.empty)
            assertFalse(cargada.noResults)
        }
    }

    @Test
    fun `borrar el texto de busqueda devuelve la lista entera`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("a", body = "Hemos perdido el aburrimiento", createdAt = 1_000L),
                testNote("b", body = "Comprar pan", createdAt = 2_000L),
            ),
        )
        val model = viewModel(repository)

        model.state.test {
            awaitItem().takeIf { !it.loading } ?: awaitItem()
            model.onQueryChange("aburrimiento")
            assertEquals(1, awaitItem().notes.size)

            model.onQueryChange("")

            assertEquals(2, awaitItem().notes.size)
        }
    }

    @Test
    fun `buscar encuentra tambien por significado, en una lista aparte`() = runTest {
        // Lo que contiene lo que buscaste y lo que se parece a lo que buscaste son dos
        // cosas: mezclarlas haria dudar de si el buscador entiende lo que se le pide.
        val repository = FakeNotesRepository(
            listOf(
                testNote("literal", body = "El aburrimiento es necesario", createdAt = 1_000L),
                testNote("parecida", body = "Ya nadie sabe aburrirse", createdAt = 2_000L),
                testNote("ajena", body = "Comprar pan y pilas", createdAt = 3_000L),
            ),
        )
        val model = viewModel(repository)

        model.state.test {
            awaitItem().takeIf { !it.loading } ?: awaitItem()
            model.onQueryChange("aburrimiento")

            val encontrado = awaitItem()
            assertEquals(listOf("literal"), encontrado.notes.map { it.id })
            assertEquals(listOf("parecida"), encontrado.related.map { it.id })
        }
    }

    @Test
    fun `una nota no sale dos veces por salir tambien por significado`() = runTest {
        // Repetida en las dos listas parecerian dos notas distintas.
        val repository = FakeNotesRepository(
            listOf(testNote("a", body = "El aburrimiento es necesario")),
        )
        val model = viewModel(repository)

        model.state.test {
            awaitItem().takeIf { !it.loading } ?: awaitItem()
            model.onQueryChange("aburrimiento")

            val encontrado = awaitItem()
            assertEquals(listOf("a"), encontrado.notes.map { it.id })
            assertEquals(emptyList<String>(), encontrado.related.map { it.id })
        }
    }

    @Test
    fun `sin resultados por palabra ni por significado se declara sin resultados`() = runTest {
        val repository = FakeNotesRepository(listOf(testNote("a", body = "Comprar pan")))
        val model = viewModel(repository)

        model.state.test {
            awaitItem().takeIf { !it.loading } ?: awaitItem()
            model.onQueryChange("termodinamica cuantica")

            assertTrue(awaitItem().noResults)
        }
    }

    @Test
    fun `las notas con propuestas sin responder salen en su seccion`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("a", body = "Hemos perdido el aburrimiento", createdAt = 1_000L),
                testNote("b", body = "Ya nadie sabe aburrirse", createdAt = 2_000L),
            ),
        )
        val model = viewModel(repository)

        model.state.test {
            val cargada = awaitItem().takeIf { !it.loading } ?: awaitItem()
            // El indexador corre en el scope del test y propone la pareja.
            val conPropuestas = if (cargada.withSuggestions.isEmpty()) awaitItem() else cargada

            assertEquals(setOf("a", "b"), conPropuestas.withSuggestions.map { it.id }.toSet())
        }
    }

    @Test
    fun `buscando no aparece la seccion de propuestas`() = runTest {
        // Quien busca esta a otra cosa: es un aviso, no una bandeja de entrada.
        val repository = FakeNotesRepository(
            listOf(
                testNote("a", body = "Hemos perdido el aburrimiento", createdAt = 1_000L),
                testNote("b", body = "Ya nadie sabe aburrirse", createdAt = 2_000L),
            ),
        )
        val model = viewModel(repository)

        model.state.test {
            awaitItem().takeIf { !it.loading } ?: awaitItem()
            model.onQueryChange("aburrimiento")

            var estado = awaitItem()
            while (estado.query.isBlank()) estado = awaitItem()

            assertEquals(emptyList<String>(), estado.withSuggestions.map { it.id })
        }
    }

    @Test
    fun `una propuesta ya respondida deja de aparecer en la seccion`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("a", body = "Hemos perdido el aburrimiento", createdAt = 1_000L),
                testNote("b", body = "Ya nadie sabe aburrirse", createdAt = 2_000L),
            ),
        )
        val model = viewModel(repository)

        model.state.test {
            var estado = awaitItem()
            while (estado.withSuggestions.isEmpty()) estado = awaitItem()
            val propuesta = repository.observePendingLinks().first().first()

            repository.putLink(propuesta.copy(state = LinkState.ACCEPTED))

            var despues = awaitItem()
            while (despues.withSuggestions.isNotEmpty()) despues = awaitItem()
            assertEquals(emptyList<String>(), despues.withSuggestions.map { it.id })
        }
    }

    @Test
    fun `una nota nueva aparece sin tener que volver a entrar`() = runTest {
        val repository = FakeNotesRepository(listOf(testNote("a", createdAt = 1_000L)))
        val model = viewModel(repository)

        model.state.test {
            awaitItem().takeIf { !it.loading } ?: awaitItem()

            repository.save(testNote("b", createdAt = 2_000L))

            assertEquals(listOf("b", "a"), awaitItem().notes.map { it.id })
        }
    }

    /** Cinco es el minimo de [RecurringThemes.words]: menos que eso no cuenta como patron. */
    private fun cincoNotasSobreElAburrimiento() = listOf(
        testNote("a", body = "Hemos perdido el aburrimiento", createdAt = 1_000L),
        testNote("b", body = "Ya nadie sabe aburrirse", createdAt = 2_000L),
        testNote("c", body = "El aburrimiento es necesario", createdAt = 3_000L),
        testNote("d", body = "Aburrirse tambien es productivo", createdAt = 4_000L),
        testNote("e", body = "El aburrimiento no es el enemigo", createdAt = 5_000L),
    )

    @Test
    fun `las raices recurrentes salen en patterns`() = runTest {
        val repository = FakeNotesRepository(cincoNotasSobreElAburrimiento())
        val model = viewModel(repository)

        model.state.test {
            var estado = awaitItem()
            while (estado.loading || estado.patterns.isEmpty()) estado = awaitItem()

            assertTrue(estado.patterns.any { it.stem == "aburr" && it.noteCount == 5 })
        }
    }

    @Test
    fun `buscando no aparecen los patrones`() = runTest {
        val repository = FakeNotesRepository(cincoNotasSobreElAburrimiento())
        val model = viewModel(repository)

        model.state.test {
            var estado = awaitItem()
            while (estado.loading || estado.patterns.isEmpty()) estado = awaitItem()

            model.onQueryChange("aburrimiento")

            estado = awaitItem()
            while (estado.query.isBlank()) estado = awaitItem()

            assertEquals(emptyList<Any>(), estado.patterns)
        }
    }

    @Test
    fun `tres notas conectadas entre si proponen un proyecto`() = runTest {
        val repository = FakeNotesRepository(
            listOf(testNote("a", createdAt = 1_000L), testNote("b", createdAt = 2_000L), testNote("c", createdAt = 3_000L)),
        )
        repository.putLink(NoteLink("a", "b", 0.9f, LinkOrigin.MANUAL, LinkState.ACCEPTED, 1L))
        repository.putLink(NoteLink("b", "c", 0.9f, LinkOrigin.MANUAL, LinkState.ACCEPTED, 2L))
        val model = viewModel(repository)

        model.state.test {
            var estado = awaitItem()
            while (estado.loading || estado.projectSuggestions.isEmpty()) estado = awaitItem()

            assertEquals(setOf("a", "b", "c"), estado.projectSuggestions.single().noteIds)
        }
    }

    @Test
    fun `aceptar una sugerencia de proyecto agrupa las notas del cluster`() = runTest {
        val repository = FakeNotesRepository(
            listOf(testNote("a", createdAt = 1_000L), testNote("b", createdAt = 2_000L), testNote("c", createdAt = 3_000L)),
        )
        repository.putLink(NoteLink("a", "b", 0.9f, LinkOrigin.MANUAL, LinkState.ACCEPTED, 1L))
        repository.putLink(NoteLink("b", "c", 0.9f, LinkOrigin.MANUAL, LinkState.ACCEPTED, 2L))
        val model = viewModel(repository)

        var cluster: RecurringCluster? = null
        model.state.test {
            var estado = awaitItem()
            while (estado.loading || estado.projectSuggestions.isEmpty()) estado = awaitItem()
            cluster = estado.projectSuggestions.single()
        }

        model.acceptClusterSuggestion(requireNotNull(cluster), "Un proyecto")
        runCurrent()

        model.state.test {
            var estado = awaitItem()
            while (estado.projectSuggestions.isNotEmpty()) estado = awaitItem()

            val proyecto = repository.observeProjects().first().single()
            assertEquals("Un proyecto", proyecto.title)
            assertEquals(setOf("a", "b", "c"), repository.notesInProject(proyecto.id).map { it.id }.toSet())
        }
    }

    @Test
    fun `ignorar una sugerencia de proyecto la descarta solo en esta sesion`() = runTest {
        val repository = FakeNotesRepository(
            listOf(testNote("a", createdAt = 1_000L), testNote("b", createdAt = 2_000L), testNote("c", createdAt = 3_000L)),
        )
        repository.putLink(NoteLink("a", "b", 0.9f, LinkOrigin.MANUAL, LinkState.ACCEPTED, 1L))
        repository.putLink(NoteLink("b", "c", 0.9f, LinkOrigin.MANUAL, LinkState.ACCEPTED, 2L))
        val model = viewModel(repository)

        model.state.test {
            var estado = awaitItem()
            while (estado.loading || estado.projectSuggestions.isEmpty()) estado = awaitItem()
            val cluster = estado.projectSuggestions.single()

            model.ignoreClusterSuggestion(cluster)

            estado = awaitItem()
            while (estado.projectSuggestions.isNotEmpty()) estado = awaitItem()

            assertEquals(emptyList<Any>(), estado.projectSuggestions)
            // No se persiste en ningun proyecto: sigue siendo un descarte de sesion.
            assertTrue(repository.observeProjects().first().isEmpty())
        }
    }
}
