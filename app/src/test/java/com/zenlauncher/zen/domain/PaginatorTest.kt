package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.reading.BlockKind
import com.zenlauncher.zen.domain.reading.BookBlock
import com.zenlauncher.zen.domain.reading.Paginator
import com.zenlauncher.zen.domain.reading.ReadingPosition
import com.zenlauncher.zen.fakes.FakePageMeasurer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El reparto en paginas. Con [FakePageMeasurer] las cuentas salen a mano: 40 caracteres
 * por linea, 20 px por linea, asi que en una pagina de 200 px caben diez lineas.
 */
class PaginatorTest {

    private val alto = 200f

    private fun parrafo(index: Int, caracteres: Int, page: Int = 0) = BookBlock(
        index = index,
        kind = BlockKind.PARAGRAPH,
        text = "x".repeat(caracteres),
        page = page,
    )

    private fun titulo(index: Int, page: Int = 0) = BookBlock(
        index = index,
        kind = BlockKind.HEADING,
        text = "1. El problema del ser",
        page = page,
        level = 1,
    )

    @Test
    fun `una pagina se llena con los bloques que caben`() {
        // Tres parrafos de dos lineas: 40 + 10 + 40 + 10 + 40 = 140 px de 200.
        val blocks = listOf(parrafo(0, 80), parrafo(1, 80), parrafo(2, 80))

        val page = Paginator.page(ReadingPosition.Start, blocks, alto, FakePageMeasurer(blocks))

        assertEquals(3, page.fragments.size)
        assertEquals(listOf(0, 1, 2), page.fragments.map { it.blockIndex })
        // Se acabo el libro: la siguiente posicion cae fuera.
        assertEquals(blocks.size, page.end.blockIndex)
    }

    /**
     * Lo que separa un lector de un visor: un parrafo de filosofia ocupa media pagina o
     * mas, asi que hay que poder partirlo. Sin esto quedarian medias paginas en blanco.
     */
    @Test
    fun `un parrafo mas largo que la pagina se parte por una linea`() {
        val blocks = listOf(parrafo(0, 40 * 25))

        val page = Paginator.page(ReadingPosition.Start, blocks, alto, FakePageMeasurer(blocks))

        val fragment = page.fragments.single()
        assertEquals(0, fragment.start)
        // Diez lineas justas de 40 caracteres.
        assertEquals(400, fragment.end)
        // Y la siguiente pagina sigue **dentro** del mismo bloque, no en el siguiente.
        assertEquals(ReadingPosition(0, 400), page.end)
    }

    @Test
    fun `la pagina siguiente continua exactamente donde acabo la anterior`() {
        val blocks = listOf(parrafo(0, 40 * 25))
        val measurer = FakePageMeasurer(blocks)

        val primera = Paginator.page(ReadingPosition.Start, blocks, alto, measurer)
        val segunda = Paginator.page(primera.end, blocks, alto, measurer)

        assertEquals(400, segunda.fragments.single().start)
        assertEquals(800, segunda.fragments.single().end)
    }

    @Test
    fun `el aire de un titulo no se cuenta si el titulo abre la pagina`() {
        // Titulo (1 linea) + parrafo de nueve lineas = 20 + 10 + 180 = 210 con aire de
        // titulo, 200 justos sin el. Si se contara el aire de arriba, no cabria.
        val blocks = listOf(titulo(0), parrafo(1, 40 * 9))

        val page = Paginator.page(ReadingPosition.Start, blocks, alto, FakePageMeasurer(blocks))

        assertEquals(2, page.fragments.size)
    }

    /**
     * Regresion: una pagina vacia no avanza, y el lector se quedaria clavado ahi para
     * siempre. Dentro de la pantalla de inicio del telefono, eso es un libro que no se
     * puede cerrar leyendo.
     */
    @Test
    fun `un bloque que no cabe ni en una linea se pinta igual en vez de dar pagina vacia`() {
        val blocks = listOf(parrafo(0, 200))

        val page = Paginator.page(ReadingPosition.Start, blocks, pageHeight = 5f, measurer = FakePageMeasurer(blocks))

        assertTrue(page.fragments.isNotEmpty())
        assertTrue("la pagina tiene que avanzar", page.end > page.start)
    }

    @Test
    fun `un libro sin bloques da una pagina vacia y no revienta`() {
        val page = Paginator.page(ReadingPosition.Start, emptyList(), alto, FakePageMeasurer(emptyList()))

        assertTrue(page.empty)
    }

    @Test
    fun `una posicion guardada fuera de rango se recoloca dentro del libro`() {
        val blocks = listOf(parrafo(0, 80))

        val page = Paginator.page(ReadingPosition(99, 999), blocks, alto, FakePageMeasurer(blocks))

        assertEquals(0, page.fragments.single().blockIndex)
    }

    /**
     * Ir hacia atras se resuelve midiendo, no con una pila de paginas visitadas: la pila
     * se vaciaria en cuanto alguien saltara desde el indice o desde una marca, que es
     * justo donde uno quiere retroceder una pagina.
     */
    @Test
    fun `retroceder devuelve la pagina que termina donde empieza la actual`() {
        val blocks = List(12) { parrafo(it, 80) }
        val measurer = FakePageMeasurer(blocks)

        val primera = Paginator.page(ReadingPosition.Start, blocks, alto, measurer)
        val segunda = Paginator.page(primera.end, blocks, alto, measurer)
        val vuelta = Paginator.previous(segunda.start, blocks, alto, measurer)

        assertEquals(primera.fragments, vuelta.fragments)
    }

    @Test
    fun `retroceder desde el principio se queda en el principio`() {
        val blocks = List(6) { parrafo(it, 80) }
        val measurer = FakePageMeasurer(blocks)

        val vuelta = Paginator.previous(ReadingPosition.Start, blocks, alto, measurer)

        assertEquals(ReadingPosition.Start, vuelta.start)
    }

    /** Retroceder despues de saltar a mitad del libro tambien funciona: no hay pila. */
    @Test
    fun `retroceder funciona igual viniendo de un salto`() {
        val blocks = List(40) { parrafo(it, 80) }
        val measurer = FakePageMeasurer(blocks)

        val saltada = Paginator.page(ReadingPosition(30, 0), blocks, alto, measurer)
        val vuelta = Paginator.previous(saltada.start, blocks, alto, measurer)

        assertTrue("tiene que quedar por detras del salto", vuelta.start < saltada.start)
        assertTrue("y tiene que llegar hasta el", vuelta.end >= saltada.start)
    }

    @Test
    fun `recorrer el libro entero pagina a pagina lo cubre sin saltarse nada`() {
        val blocks = List(30) { parrafo(it, 130) }
        val measurer = FakePageMeasurer(blocks)

        var position = ReadingPosition.Start
        val visitados = mutableListOf<Int>()
        var paginas = 0
        while (position.blockIndex < blocks.size && paginas < 500) {
            val page = Paginator.page(position, blocks, alto, measurer)
            page.fragments.forEach { visitados += it.blockIndex }
            position = page.end
            paginas++
        }

        assertEquals("ningun bloque puede quedarse sin pintar", 30, visitados.distinct().size)
        assertTrue("el recorrido tiene que terminar", paginas < 500)
    }
}
