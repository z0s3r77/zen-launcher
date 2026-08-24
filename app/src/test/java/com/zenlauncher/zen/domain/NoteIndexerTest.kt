package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.notes.LexicalEmbedder
import com.zenlauncher.zen.domain.notes.LinkOrigin
import com.zenlauncher.zen.domain.notes.LinkState
import com.zenlauncher.zen.domain.notes.NoteIndexer
import com.zenlauncher.zen.domain.notes.NoteLink
import com.zenlauncher.zen.fakes.FakeNotesRepository
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.testNote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteIndexerTest {

    private val clock = FakeZenClock(wall = 1_700_000_000_000)

    private fun indexer(repository: FakeNotesRepository) =
        NoteIndexer(repository, LexicalEmbedder(), clock)

    @Test
    fun `indexa las notas que no tienen vector`() = runTest {
        val repository = FakeNotesRepository(
            listOf(testNote("a", body = "Hemos perdido el aburrimiento")),
        )

        assertEquals(1, indexer(repository).sync())
        assertEquals(setOf("a"), repository.embeddings(LexicalEmbedder().id).keys)
    }

    @Test
    fun `no reindexa lo que ya esta indexado`() = runTest {
        // Cada visita a Notas llama a sync: si volviera a indexar todo, una biblioteca
        // grande gastaria bateria cada vez que se entra a mirar una nota.
        val repository = FakeNotesRepository(listOf(testNote("a")))
        val indexer = indexer(repository)

        assertEquals(1, indexer.sync())
        assertEquals(0, indexer.sync())
    }

    @Test
    fun `dos notas del mismo tema quedan propuestas la una a la otra`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("a", body = "Hemos perdido el aburrimiento", createdAt = 1_000L),
                testNote("b", body = "Ya nadie sabe aburrirse", createdAt = 2_000L),
            ),
        )

        indexer(repository).sync()

        val enlace = repository.observeLinks("a").first().single()
        assertEquals(LinkState.PENDING, enlace.state)
        assertEquals(LinkOrigin.SUGGESTED, enlace.origin)
        assertTrue(enlace.score >= LexicalEmbedder().relatedThreshold)
    }

    @Test
    fun `dos notas sin nada que ver no se conectan`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("a", body = "Hemos perdido la capacidad de aburrirnos", createdAt = 1_000L),
                testNote("b", body = "Comprar pan, leche y pilas para el mando", createdAt = 2_000L),
            ),
        )

        indexer(repository).sync()

        assertEquals(emptyList<NoteLink>(), repository.observeLinks("a").first())
    }

    @Test
    fun `una nota no se conecta consigo misma`() = runTest {
        // Sin excluirla, lo primero que encontraria cada nota seria ella misma con un
        // parecido perfecto, y toda la seccion de conexiones seria eso.
        val repository = FakeNotesRepository(listOf(testNote("a")))

        indexer(repository).sync()

        assertTrue(repository.observeLinks("a").first().none { it.toNoteId == "a" && it.fromNoteId == "a" })
    }

    @Test
    fun `una pareja ya descartada no se vuelve a proponer`() = runTest {
        // Ignorar una vez tiene que bastar para siempre: el indice se recalcula en cada
        // visita y volveria a proponerla eternamente.
        val repository = FakeNotesRepository(
            listOf(
                testNote("a", body = "Hemos perdido el aburrimiento", createdAt = 1_000L),
                testNote("b", body = "Ya nadie sabe aburrirse", createdAt = 2_000L),
            ),
        )
        repository.putLink(
            NoteLink("a", "b", 0.5f, LinkOrigin.SUGGESTED, LinkState.IGNORED, 900L),
        )

        indexer(repository).sync()

        assertEquals(LinkState.IGNORED, repository.observeLinks("a").first().single().state)
    }

    @Test
    fun `las notas de la misma tanda se encuentran entre si`() = runTest {
        // Las conexiones se calculan tras indexar la tanda entera. Si se calcularan nota
        // a nota, la primera no veria a la segunda porque aun no tendria vector.
        val repository = FakeNotesRepository(
            listOf(
                testNote("a", body = "Quiero aprender Rust", createdAt = 1_000L),
                testNote("b", body = "Me interesa aprender Rust para sistemas", createdAt = 2_000L),
            ),
        )

        indexer(repository).sync()

        assertFalse(repository.observeLinks("a").first().isEmpty())
    }

    @Test
    fun `buscar por significado encuentra notas que no contienen la palabra exacta`() = runTest {
        val repository = FakeNotesRepository(
            listOf(
                testNote("a", body = "La gente ya no sabe aburrirse", createdAt = 1_000L),
                testNote("b", body = "Comprar pan y leche", createdAt = 2_000L),
            ),
        )
        val indexer = indexer(repository)
        indexer.sync()

        val resultados = indexer.similarTo("el aburrimiento")

        assertEquals(listOf("a"), resultados.map { it.noteId })
    }

    @Test
    fun `buscar admite resultados mas flojos que proponer una conexion`() = runTest {
        // El usuario ha preguntado: un resultado regular es una respuesta regular. Una
        // conexion que nadie pidio y no viene a cuento es peor que no proponer nada.
        val repository = FakeNotesRepository(
            listOf(testNote("a", body = "Quiero aprender Rust orientado a sistemas")),
        )
        val indexer = indexer(repository)
        indexer.sync()

        assertFalse(indexer.similarTo("sistemas").isEmpty())
        assertEquals(emptyList<NoteLink>(), repository.observeLinks("a").first())
    }

    @Test
    fun `buscar sin escribir nada no devuelve nada`() = runTest {
        val repository = FakeNotesRepository(listOf(testNote("a")))
        val indexer = indexer(repository)
        indexer.sync()

        assertEquals(emptyList<Any>(), indexer.similarTo("   "))
    }

    @Test
    fun `las etiquetas y el titulo tambien entran en el indice`() = runTest {
        // Si una etiqueta se ensena en la nota pero no cuenta para las conexiones, dos
        // notas con la misma etiqueta podrian no encontrarse nunca.
        val repository = FakeNotesRepository(
            listOf(
                testNote("a", body = "Nota corta", tags = listOf("aburrimiento"), createdAt = 1_000L),
            ),
        )
        val indexer = indexer(repository)
        indexer.sync()

        assertFalse(indexer.similarTo("aburrimiento").isEmpty())
    }
}
