package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.reading.BlockKind
import com.zenlauncher.zen.domain.reading.HeadingDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El detector es a proposito conservador: un titulo que se queda en parrafo cuesta una
 * entrada del indice, y un parrafo convertido en titulo ensucia la unica forma de
 * navegar que tiene el libro.
 */
class HeadingDetectorTest {

    @Test
    fun `la numeracion da el nivel`() {
        assertEquals(1, HeadingDetector.headingLevel("3. Libertad", singleLine = true))
        assertEquals(2, HeadingDetector.headingLevel("3.1 Libertad y responsabilidad", true))
        assertEquals(3, HeadingDetector.headingLevel("3.1.2 Determinismo", true))
    }

    @Test
    fun `las palabras de division son capitulo aunque no lleven numero`() {
        assertEquals(1, HeadingDetector.headingLevel("Introduccion", singleLine = true))
        assertEquals(1, HeadingDetector.headingLevel("CAPÍTULO PRIMERO", singleLine = true))
        assertEquals(1, HeadingDetector.headingLevel("IV. El problema del ser", true))
    }

    @Test
    fun `una linea entera en mayusculas y sola es un titulo`() {
        assertEquals(1, HeadingDetector.headingLevel("LA CONCIENCIA", singleLine = true))
    }

    /**
     * Sin la condicion de estar sola en su linea, cualquier frase corta en mayusculas
     * dentro de un parrafo —una cita, unas siglas desarrolladas— se convertiria en un
     * capitulo del libro.
     */
    @Test
    fun `lo mismo dentro de un parrafo no es un titulo`() {
        assertEquals(0, HeadingDetector.headingLevel("LA CONCIENCIA", singleLine = false))
    }

    @Test
    fun `una frase que termina en punto no es un titulo`() {
        assertEquals(0, HeadingDetector.headingLevel("No hay salida.", singleLine = true))
    }

    /**
     * Los dos puntos si, porque en castellano academico un titulo con subtitulo detras
     * de dos puntos es lo mas normal del mundo.
     */
    @Test
    fun `un titulo con dos puntos sigue siendo un titulo`() {
        assertEquals(1, HeadingDetector.headingLevel("La conciencia: un problema", true))
    }

    @Test
    fun `un parrafo largo no es un titulo por muy solo que este`() {
        val largo = "La conciencia constituye uno de los problemas fundamentales de toda " +
            "la filosofia moderna y contemporanea sin excepcion alguna."

        assertEquals(0, HeadingDetector.headingLevel(largo, singleLine = true))
    }

    @Test
    fun `startsBlock reconoce lo que abre division`() {
        assertTrue(HeadingDetector.startsBlock("3. Libertad"))
        assertTrue(HeadingDetector.startsBlock("CAPÍTULO 4"))
        assertTrue(HeadingDetector.startsBlock("IV. El problema"))
        assertFalse(HeadingDetector.startsBlock("y esa libertad no admite excusa"))
        // "V" sin punto seria un romano valido y partiria cualquier frase que empiece
        // por esa letra: el punto es obligatorio.
        assertFalse(HeadingDetector.startsBlock("V amos a ver"))
    }

    @Test
    fun `classify conserva indice y pagina`() {
        val block = HeadingDetector.classify("3. Libertad", index = 7, page = 66, singleLine = true)

        assertEquals(BlockKind.HEADING, block.kind)
        assertEquals(7, block.index)
        assertEquals(66, block.page)
        assertEquals(1, block.level)
    }

    @Test
    fun `un parrafo no lleva nivel`() {
        val texto = "La conciencia constituye uno de los problemas mas discutidos de la " +
            "filosofia moderna, y no por falta de respuestas."
        val block = HeadingDetector.classify(texto, index = 1, page = 2, singleLine = false)

        assertEquals(BlockKind.PARAGRAPH, block.kind)
        assertEquals(0, block.level)
    }
}
