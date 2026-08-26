package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.notes.LinkOrigin
import com.zenlauncher.zen.domain.notes.LinkState
import com.zenlauncher.zen.domain.notes.NoteLink
import com.zenlauncher.zen.domain.notes.RecurringThemes
import com.zenlauncher.zen.fakes.testNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringThemesTest {

    private fun link(from: String, to: String, state: LinkState) =
        NoteLink(from, to, 0.5f, LinkOrigin.SUGGESTED, state, 1_000L)

    @Test
    fun `una palabra por debajo del umbral no aparece`() {
        val notes = listOf(
            testNote("a", body = "Hemos perdido el aburrimiento"),
            testNote("b", body = "Ya nadie sabe aburrirse"),
        )

        val palabras = RecurringThemes.words(notes, minNotes = 3)

        assertTrue(palabras.none { it.stem == "aburr" })
    }

    @Test
    fun `una palabra que llega al umbral aparece con su recuento real`() {
        val notes = listOf(
            testNote("a", body = "Hemos perdido el aburrimiento"),
            testNote("b", body = "Ya nadie sabe aburrirse"),
            testNote("c", body = "El aburrimiento es necesario"),
        )

        val palabras = RecurringThemes.words(notes, minNotes = 3)

        assertEquals(1, palabras.size)
        assertEquals("aburr", palabras.first().stem)
        assertEquals(3, palabras.first().noteCount)
    }

    @Test
    fun `repetir la palabra dentro de la misma nota no infla el recuento`() {
        // Cuenta notas distintas, no repeticiones: una nota que repite la palabra
        // diez veces no puede pesar como diez notas.
        val notes = listOf(
            testNote("a", body = "Aburrimiento, aburrimiento y más aburrimiento"),
            testNote("b", body = "Comprar pan"),
        )

        val palabras = RecurringThemes.words(notes, minNotes = 2)

        assertTrue(palabras.isEmpty())
    }

    @Test
    fun `clusters de tamano dos no cuentan`() {
        val notes = listOf(testNote("a"), testNote("b"))
        val links = listOf(link("a", "b", LinkState.ACCEPTED))

        assertEquals(emptyList<Any>(), RecurringThemes.clusters(notes, links, minSize = 3))
    }

    @Test
    fun `tres notas conectadas entre si forman un cluster`() {
        val notes = listOf(testNote("a"), testNote("b"), testNote("c"))
        val links = listOf(
            link("a", "b", LinkState.ACCEPTED),
            link("b", "c", LinkState.ACCEPTED),
        )

        val clusters = RecurringThemes.clusters(notes, links, minSize = 3)

        assertEquals(1, clusters.size)
        assertEquals(setOf("a", "b", "c"), clusters.first().noteIds)
    }

    @Test
    fun `las propuestas sin responder no cuentan para el cluster`() {
        val notes = listOf(testNote("a"), testNote("b"), testNote("c"))
        val links = listOf(
            link("a", "b", LinkState.ACCEPTED),
            link("b", "c", LinkState.PENDING),
        )

        assertEquals(emptyList<Any>(), RecurringThemes.clusters(notes, links, minSize = 3))
    }

    @Test
    fun `notas ya en el mismo proyecto siguen agrupandose igual`() {
        // El filtrado por proyecto ya asignado es responsabilidad de quien llama, no de
        // esta funcion pura: aqui solo se agrupa por conexion aceptada.
        val notes = listOf(
            testNote("a").copy(projectId = "p1"),
            testNote("b").copy(projectId = "p1"),
            testNote("c").copy(projectId = "p1"),
        )
        val links = listOf(
            link("a", "b", LinkState.ACCEPTED),
            link("b", "c", LinkState.ACCEPTED),
        )

        val clusters = RecurringThemes.clusters(notes, links, minSize = 3)

        assertEquals(1, clusters.size)
        assertEquals(setOf("a", "b", "c"), clusters.first().noteIds)
    }

    @Test
    fun `una conexion con una nota que ya no existe no revienta`() {
        val notes = listOf(testNote("a"), testNote("b"))
        val links = listOf(link("a", "borrada", LinkState.ACCEPTED))

        assertEquals(emptyList<Any>(), RecurringThemes.clusters(notes, links, minSize = 3))
    }
}
