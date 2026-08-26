package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.reading.BlockKind
import com.zenlauncher.zen.domain.reading.BookBlock
import com.zenlauncher.zen.domain.reading.ReadingSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSearchTest {

    private fun block(index: Int, text: String, page: Int = index) =
        BookBlock(index = index, kind = BlockKind.PARAGRAPH, text = text, page = page)

    private val libro = listOf(
        block(0, "El hombre está condenado a ser libre."),
        block(1, "La conciencia constituye uno de los problemas de la filosofía."),
        block(2, "Nada hay más ajeno a la libertad que la mala fe."),
    )

    @Test
    fun `encuentra el fragmento y dice en que bloque y pagina esta`() {
        val hits = ReadingSearch.find(libro, "conciencia")

        assertEquals(1, hits.size)
        assertEquals(1, hits[0].blockIndex)
        assertEquals(1, hits[0].page)
        assertTrue(hits[0].snippet.contains("conciencia"))
    }

    /**
     * Sin normalizar, buscar "filosofia" sin tilde no encontraria nada: al teclear
     * deprisa las tildes no se ponen. Es el mismo criterio que usa el buscador de Notas,
     * por eso reusa `TextNormalizer`.
     */
    @Test
    fun `busca sin acentos y sin distinguir mayusculas`() {
        assertEquals(1, ReadingSearch.find(libro, "filosofia").size)
        assertEquals(1, ReadingSearch.find(libro, "FILOSOFÍA").size)
        assertEquals(1, ReadingSearch.find(libro, "Está Condenado").size)
    }

    @Test
    fun `una busqueda vacia o de una letra no devuelve el libro entero`() {
        assertTrue(ReadingSearch.find(libro, "").isEmpty())
        assertTrue(ReadingSearch.find(libro, " ").isEmpty())
        assertTrue(ReadingSearch.find(libro, "a").isEmpty())
    }

    @Test
    fun `los resultados salen en el orden del libro`() {
        val hits = ReadingSearch.find(libro, "la")

        assertEquals(hits.map { it.blockIndex }, hits.map { it.blockIndex }.sorted())
    }

    /**
     * Regresion: la posicion viene del texto **normalizado**, que puede ser mas corto que
     * el original porque los espacios se colapsan. Sin acotar, el recorte se saldria del
     * texto y la busqueda reventaria en un parrafo con espacios dobles.
     */
    @Test
    fun `un parrafo con espacios de sobra no rompe el recorte`() {
        val raro = listOf(block(0, "El   hombre    está     condenado   a  ser   libre."))

        val hits = ReadingSearch.find(raro, "libre")

        assertEquals(1, hits.size)
        assertTrue(hits[0].snippet.isNotEmpty())
    }

    @Test
    fun `el recorte marca con puntos suspensivos que hay mas texto alrededor`() {
        val largo = "a".repeat(200) + " libertad " + "b".repeat(200)
        val hits = ReadingSearch.find(listOf(block(0, largo)), "libertad")

        assertTrue(hits[0].snippet.startsWith("…"))
        assertTrue(hits[0].snippet.endsWith("…"))
    }

    @Test
    fun `no devuelve mas resultados de los que caben en una lista`() {
        val muchos = (0 until 500).map { block(it, "libertad y responsabilidad") }

        assertEquals(60, ReadingSearch.find(muchos, "libertad").size)
    }
}
