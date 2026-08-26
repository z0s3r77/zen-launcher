package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.reading.Highlight
import com.zenlauncher.zen.domain.reading.HighlightSpans
import com.zenlauncher.zen.domain.reading.PageFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightSpansTest {

    private fun subrayado(
        id: String,
        blockIndex: Int = 0,
        start: Int,
        end: Int,
        note: String? = null,
    ) = Highlight(
        id = id,
        bookId = "uno",
        blockIndex = blockIndex,
        start = start,
        end = end,
        text = "…",
        note = note,
        page = 12,
        createdAtMillis = 1_000L,
    )

    @Test
    fun `las posiciones salen relativas al trozo que se pinta`() {
        val fragment = PageFragment(blockIndex = 0, start = 100, end = 200)

        val spans = HighlightSpans.inFragment(fragment, listOf(subrayado("a", start = 120, end = 140)))

        assertEquals(1, spans.size)
        assertEquals(20, spans[0].start)
        assertEquals(40, spans[0].end)
    }

    /**
     * Regresion: al partir un parrafo entre dos paginas, un subrayado que cruce el corte
     * tiene que salir a medias en cada una. Sin recortarlo, pintarlo daria posiciones
     * fuera de rango y la pagina reventaria.
     */
    @Test
    fun `un subrayado que cruza el corte de pagina se recorta en las dos mitades`() {
        val subrayados = listOf(subrayado("a", start = 90, end = 130))
        val primera = PageFragment(blockIndex = 0, start = 0, end = 100)
        val segunda = PageFragment(blockIndex = 0, start = 100, end = 200)

        assertEquals(listOf(90 to 100), HighlightSpans.inFragment(primera, subrayados).map { it.start to it.end })
        assertEquals(listOf(0 to 30), HighlightSpans.inFragment(segunda, subrayados).map { it.start to it.end })
    }

    @Test
    fun `un subrayado de otro bloque o fuera del trozo no se pinta`() {
        val fragment = PageFragment(blockIndex = 0, start = 100, end = 200)

        val spans = HighlightSpans.inFragment(
            fragment,
            listOf(
                subrayado("otro-bloque", blockIndex = 1, start = 120, end = 140),
                subrayado("antes", start = 10, end = 40),
                subrayado("despues", start = 300, end = 340),
            ),
        )

        assertTrue(spans.isEmpty())
    }

    @Test
    fun `los subrayados salen en orden de lectura`() {
        val fragment = PageFragment(blockIndex = 0, start = 0, end = 200)

        val spans = HighlightSpans.inFragment(
            fragment,
            listOf(subrayado("b", start = 120, end = 140), subrayado("a", start = 10, end = 40)),
        )

        assertEquals(listOf("a", "b"), spans.map { it.id })
    }

    /** Los que llevan nota se distinguen: en la pagina se ven, no hay que abrir la lista. */
    @Test
    fun `se sabe cuales llevan nota`() {
        val fragment = PageFragment(blockIndex = 0, start = 0, end = 200)

        val spans = HighlightSpans.inFragment(
            fragment,
            listOf(
                subrayado("a", start = 10, end = 40),
                subrayado("b", start = 50, end = 80, note = "Esto es Sartre"),
            ),
        )

        assertEquals(listOf(false, true), spans.map { it.hasNote })
    }

    /** Una nota en blanco no es una nota: no puede pintarse distinto que un subrayado. */
    @Test
    fun `una nota vacia cuenta como sin nota`() {
        val fragment = PageFragment(blockIndex = 0, start = 0, end = 200)

        val spans = HighlightSpans.inFragment(fragment, listOf(subrayado("a", start = 10, end = 40, note = "   ")))

        assertEquals(false, spans.single().hasNote)
    }

    @Test
    fun `se encuentra el subrayado que hay bajo una posicion`() {
        val subrayados = listOf(subrayado("a", start = 10, end = 40))

        assertEquals("a", HighlightSpans.at(0, 20, subrayados)?.id)
        // El final es exclusivo: tocar justo detras no es tocar el subrayado.
        assertNull(HighlightSpans.at(0, 40, subrayados))
        assertNull(HighlightSpans.at(1, 20, subrayados))
    }
}
