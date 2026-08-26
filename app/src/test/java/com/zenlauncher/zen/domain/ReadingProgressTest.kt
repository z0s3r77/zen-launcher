package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.reading.BookChapter
import com.zenlauncher.zen.domain.reading.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingProgressTest {

    private val chapters = listOf(
        BookChapter("1. El problema del ser", level = 1, blockIndex = 10, page = 12),
        BookChapter("2. La conciencia", level = 1, blockIndex = 380, page = 34),
        BookChapter("3. Libertad", level = 1, blockIndex = 420, page = 67),
    )

    @Test
    fun `el progreso va de cero a cien`() {
        assertEquals(0, ReadingProgress.percent(0, 200))
        assertEquals(100, ReadingProgress.percent(199, 200))
        assertEquals(50, ReadingProgress.percent(100, 201))
    }

    /** Un libro vacio esta **sin empezar**, no terminado: dividir por cero daria 100%. */
    @Test
    fun `un libro sin bloques no esta leido`() {
        assertEquals(0, ReadingProgress.percent(0, 0))
        assertEquals(0, ReadingProgress.percent(5, 1))
    }

    /**
     * El ultimo capitulo que empieza antes del bloque, no el mas cercano: en el bloque
     * 400 estas en el capitulo 2 aunque el 3 (420) quede a la mitad de distancia.
     */
    @Test
    fun `el capitulo es el ultimo que empezo, no el mas cercano`() {
        assertEquals("2. La conciencia", ReadingProgress.chapterAt(400, chapters)?.title)
        assertEquals("3. Libertad", ReadingProgress.chapterAt(420, chapters)?.title)
    }

    @Test
    fun `antes del primer capitulo no hay capitulo`() {
        assertNull(ReadingProgress.chapterAt(3, chapters))
        assertNull(ReadingProgress.chapterAt(0, emptyList()))
    }

    /**
     * La barra son caracteres y no un dibujo: asi el progreso se lee tal cual con un
     * lector de pantalla, y no hay una segunda grafica en una aplicacion que tiene una
     * sola a proposito.
     */
    @Test
    fun `la barra mide siempre lo mismo`() {
        val vacia = ReadingProgress.bar(0, 100)
        val llena = ReadingProgress.bar(99, 100)

        assertEquals(ReadingProgress.BAR_WIDTH, vacia.length)
        assertEquals(ReadingProgress.BAR_WIDTH, llena.length)
        assertEquals("░".repeat(ReadingProgress.BAR_WIDTH), vacia)
        assertEquals("█".repeat(ReadingProgress.BAR_WIDTH), llena)
    }
}
