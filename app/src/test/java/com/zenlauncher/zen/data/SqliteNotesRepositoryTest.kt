package com.zenlauncher.zen.data

import androidx.test.core.app.ApplicationProvider
import com.zenlauncher.zen.data.notes.SqliteNotesRepository
import com.zenlauncher.zen.domain.notes.AttachmentKind
import com.zenlauncher.zen.domain.notes.LinkOrigin
import com.zenlauncher.zen.domain.notes.LinkState
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.NoteAttachment
import com.zenlauncher.zen.domain.notes.NoteLink
import com.zenlauncher.zen.domain.notes.NoteStage
import com.zenlauncher.zen.domain.notes.Project
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric para ejercitar SQLite de verdad: las claves foraneas en cascada y las
 * transacciones son justo lo que un doble en memoria no probaria, y son la parte donde
 * un fallo se lleva por delante notas del usuario.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SqliteNotesRepositoryTest {

    private lateinit var repository: SqliteNotesRepository

    @Before
    fun setUp() {
        repository = SqliteNotesRepository(
            context = ApplicationProvider.getApplicationContext(),
            io = UnconfinedTestDispatcher(),
        )
    }

    private fun note(
        id: String,
        body: String = "Hemos perdido la capacidad de aburrirnos",
        createdAt: Long = 1_000L,
        attachments: List<NoteAttachment> = emptyList(),
        title: String? = null,
        tags: List<String> = emptyList(),
        enrichedAt: Long? = null,
    ) = Note(
        id = id,
        createdAtMillis = createdAt,
        updatedAtMillis = createdAt,
        body = body,
        attachments = attachments,
        title = title,
        tags = tags,
        enrichedAtMillis = enrichedAt,
    )

    @Test
    fun `guarda y recupera una nota con todos sus campos`() = runTest {
        val original = note(
            id = "a",
            title = "El aburrimiento",
            tags = listOf("atencion", "moviles"),
            enrichedAt = 5_000L,
        ).copy(summary = "Ya nadie se aburre", stage = NoteStage.DEVELOPED)

        repository.save(original)

        assertEquals(original, repository.note("a"))
    }

    @Test
    fun `guarda la nota junto a sus adjuntos`() = runTest {
        val imagen = NoteAttachment("i1", "a", AttachmentKind.IMAGE, "notas/a/1.jpg", 1_100L)
        val enlace = NoteAttachment("l1", "a", AttachmentKind.LINK, "https://ejemplo.es", 1_200L)

        repository.save(note("a", attachments = listOf(imagen, enlace)))

        val recuperada = repository.note("a")!!
        assertEquals(listOf(imagen, enlace), recuperada.attachments)
        assertEquals(listOf(imagen), recuperada.images)
        assertEquals(listOf(enlace), recuperada.links)
    }

    @Test
    fun `las notas se listan de la mas reciente a la mas antigua`() = runTest {
        repository.save(note("vieja", createdAt = 1_000L))
        repository.save(note("nueva", createdAt = 9_000L))

        assertEquals(listOf("nueva", "vieja"), repository.observeNotes().first().map { it.id })
    }

    @Test
    fun `editar una nota no borra sus conexiones ni sus adjuntos`() = runTest {
        // Regresion: guardar con CONFLICT_REPLACE es un DELETE seguido de un INSERT, y
        // el ON DELETE CASCADE se llevaba por delante los adjuntos y las conexiones que
        // el usuario ya habia aceptado. Editar el texto de una nota borraba en silencio
        // los enlaces con las demas.
        val imagen = NoteAttachment("i1", "a", AttachmentKind.IMAGE, "notas/a/1.jpg", 1_100L)
        repository.save(note("a", attachments = listOf(imagen)))
        repository.save(note("b", createdAt = 2_000L))
        repository.putLink(
            NoteLink("a", "b", score = 0.8f, origin = LinkOrigin.SUGGESTED, state = LinkState.ACCEPTED, createdAtMillis = 3_000L),
        )

        repository.save(repository.note("a")!!.copy(body = "Texto corregido"))

        assertEquals("Texto corregido", repository.note("a")!!.body)
        assertEquals(listOf(imagen), repository.note("a")!!.attachments)
        assertEquals(1, repository.observeLinks("a").first().size)
    }

    @Test
    fun `borrar una nota se lleva sus adjuntos y sus conexiones`() = runTest {
        val imagen = NoteAttachment("i1", "a", AttachmentKind.IMAGE, "notas/a/1.jpg", 1_100L)
        repository.save(note("a", attachments = listOf(imagen)))
        repository.save(note("b", createdAt = 2_000L))
        repository.putLink(
            NoteLink("a", "b", 0.8f, LinkOrigin.SUGGESTED, LinkState.ACCEPTED, 3_000L),
        )

        repository.delete("a")

        assertNull(repository.note("a"))
        assertEquals(emptyList<NoteLink>(), repository.observeLinks("b").first())
    }

    @Test
    fun `el buscador encuentra sin acentos y sin distinguir mayusculas`() = runTest {
        repository.save(note("a", body = "Quizá deberíamos aprender a aburrirnos otra vez"))
        repository.save(note("b", body = "Comprar pan", createdAt = 2_000L))

        assertEquals(listOf("a"), repository.search("ABURRIRNOS").map { it.id })
        assertEquals(listOf("a"), repository.search("quiza").map { it.id })
        assertEquals(listOf("a"), repository.search("deberiamos").map { it.id })
    }

    @Test
    fun `el buscador tambien mira el titulo, el resumen y las etiquetas`() = runTest {
        // Se ensenan en la nota, asi que si no encontraran, el buscador pareceria roto.
        repository.save(
            note("a", body = "Comprar pan", tags = listOf("atencion"))
                .copy(title = "La distraccion", summary = "Sobre el algoritmo"),
        )

        assertEquals(listOf("a"), repository.search("distraccion").map { it.id })
        assertEquals(listOf("a"), repository.search("algoritmo").map { it.id })
        assertEquals(listOf("a"), repository.search("atencion").map { it.id })
    }

    @Test
    fun `el porcentaje escrito en la busqueda es un caracter, no un comodin`() = runTest {
        // Sin ESCAPE, buscar "%" devolvia la biblioteca entera: el usuario escribe un
        // signo y la aplicacion lo interpreta como "todo".
        repository.save(note("a", body = "Subir el 20% de las ventas"))
        repository.save(note("b", body = "Comprar pan", createdAt = 2_000L))

        assertEquals(listOf("a"), repository.search("20%").map { it.id })
        assertEquals(emptyList<String>(), repository.search("%zzz%").map { it.id })
    }

    @Test
    fun `una busqueda en blanco devuelve todo en lugar de nada`() = runTest {
        repository.save(note("a"))
        repository.save(note("b", createdAt = 2_000L))

        assertEquals(2, repository.search("   ").size)
    }

    @Test
    fun `la cola de enriquecimiento saca lo mas viejo primero y solo lo pendiente`() = runTest {
        repository.save(note("vieja", createdAt = 1_000L))
        repository.save(note("nueva", createdAt = 9_000L))
        repository.save(note("hecha", createdAt = 2_000L, enrichedAt = 3_000L))

        assertEquals(listOf("vieja", "nueva"), repository.pendingEnrichment(limit = 10).map { it.id })
        assertEquals(listOf("vieja"), repository.pendingEnrichment(limit = 1).map { it.id })
    }

    @Test
    fun `una conexion ignorada no se puede volver a proponer al reves`() = runTest {
        // La pareja no tiene direccion: si el usuario descarta A-B, el indice no puede
        // reaparecer con B-A. Ignorar una vez tiene que bastar para siempre.
        repository.save(note("a"))
        repository.save(note("b", createdAt = 2_000L))
        repository.putLink(NoteLink("a", "b", 0.9f, LinkOrigin.SUGGESTED, LinkState.IGNORED, 3_000L))

        repository.putLink(NoteLink("b", "a", 0.7f, LinkOrigin.SUGGESTED, LinkState.PENDING, 4_000L))

        val enlace = repository.observeLinks("a").first().single()
        assertEquals(LinkState.IGNORED, enlace.state)
        assertTrue(repository.ignoredPairs().contains(enlace.pairKey))
    }

    @Test
    fun `aceptar una conexion si pisa la propuesta anterior`() = runTest {
        // La otra cara de lo mismo: lo que decide el usuario tiene que escribirse
        // aunque la pareja ya estuviera guardada.
        repository.save(note("a"))
        repository.save(note("b", createdAt = 2_000L))
        repository.putLink(NoteLink("a", "b", 0.9f, LinkOrigin.SUGGESTED, LinkState.PENDING, 3_000L))

        repository.putLink(NoteLink("a", "b", 0.9f, LinkOrigin.SUGGESTED, LinkState.ACCEPTED, 4_000L))

        assertEquals(LinkState.ACCEPTED, repository.observeLinks("a").first().single().state)
    }

    @Test
    fun `borrar un proyecto suelta sus notas en lugar de borrarlas`() = runTest {
        // Abandonar un proyecto no puede convertir en basura las ideas que lo formaron.
        repository.saveProject(Project("p1", "Aprender Rust", 1_000L))
        repository.save(note("a"))
        repository.assignToProject("a", "p1")

        repository.deleteProject("p1")

        assertEquals("a", repository.note("a")?.id)
        assertNull(repository.note("a")?.projectId)
    }

    @Test
    fun `las notas de un proyecto se recuperan juntas`() = runTest {
        repository.saveProject(Project("p1", "Aprender Rust", 1_000L))
        repository.save(note("a", createdAt = 1_000L))
        repository.save(note("b", createdAt = 2_000L))
        repository.save(note("c", createdAt = 3_000L))
        repository.assignToProject("a", "p1")
        repository.assignToProject("c", "p1")

        assertEquals(listOf("c", "a"), repository.notesInProject("p1").map { it.id })
    }

    @Test
    fun `un vector vuelve identico del disco`() = runTest {
        // El orden de bytes se fija a mano (little-endian): con el de por defecto de
        // ByteBuffer, los mismos bytes se leerian como numeros distintos y las
        // conexiones se volverian aleatorias sin que nada fallara de forma visible.
        repository.save(note("a"))
        val original = floatArrayOf(0.5f, -0.25f, 0f, 1f, -1f, 0.123456f)

        repository.putEmbedding("a", "lexico-v1", original)

        val recuperado = repository.embeddings("lexico-v1")["a"]!!
        assertArrayEquals(original, recuperado, 0f)
    }

    @Test
    fun `reindexar una nota pisa su vector en lugar de duplicarlo`() = runTest {
        repository.save(note("a"))
        repository.putEmbedding("a", "lexico-v1", floatArrayOf(1f, 0f))

        repository.putEmbedding("a", "lexico-v1", floatArrayOf(0f, 1f))

        val vectores = repository.embeddings("lexico-v1")
        assertEquals(1, vectores.size)
        assertArrayEquals(floatArrayOf(0f, 1f), vectores["a"], 0f)
    }

    @Test
    fun `los vectores de un motor no se mezclan con los de otro`() = runTest {
        // Comparar un vector lexico con uno neuronal daria un numero sin significado en
        // vez de un error: al cambiar de motor, los del anterior dejan de encontrarse.
        repository.save(note("a"))
        repository.putEmbedding("a", "lexico-v1", floatArrayOf(1f, 0f))

        assertEquals(emptyMap<String, FloatArray>(), repository.embeddings("gemma-v1"))
        assertEquals(1, repository.embeddings("lexico-v1").size)
    }

    @Test
    fun `la cola del indice saca lo que ese motor no ha visto, lo mas viejo primero`() = runTest {
        repository.save(note("vieja", createdAt = 1_000L))
        repository.save(note("nueva", createdAt = 9_000L))
        repository.save(note("indexada", createdAt = 2_000L))
        repository.putEmbedding("indexada", "lexico-v1", floatArrayOf(1f))

        assertEquals(
            listOf("vieja", "nueva"),
            repository.notesWithoutEmbedding("lexico-v1", limit = 10).map { it.id },
        )
        // Con otro motor no hay nada indexado: hay que reindexarlo todo.
        assertEquals(3, repository.notesWithoutEmbedding("gemma-v1", limit = 10).size)
    }

    @Test
    fun `borrar una nota se lleva su vector`() = runTest {
        repository.save(note("a"))
        repository.putEmbedding("a", "lexico-v1", floatArrayOf(1f, 0f))

        repository.delete("a")

        assertEquals(emptyMap<String, FloatArray>(), repository.embeddings("lexico-v1"))
    }

    @Test
    fun `las propuestas sin responder se leen todas juntas`() = runTest {
        repository.save(note("a"))
        repository.save(note("b", createdAt = 2_000L))
        repository.save(note("c", createdAt = 3_000L))
        repository.putLink(NoteLink("a", "b", 0.9f, LinkOrigin.SUGGESTED, LinkState.PENDING, 1L))
        repository.putLink(NoteLink("a", "c", 0.4f, LinkOrigin.SUGGESTED, LinkState.ACCEPTED, 2L))

        val pendientes = repository.observePendingLinks().first()

        assertEquals(1, pendientes.size)
        assertEquals(LinkState.PENDING, pendientes.first().state)
    }

    @Test
    fun `observeNotes reemite al guardar`() = runTest {
        repository.save(note("a"))
        assertEquals(1, repository.observeNotes().first().size)

        repository.save(note("b", createdAt = 2_000L))
        assertEquals(2, repository.observeNotes().first().size)
    }
}
