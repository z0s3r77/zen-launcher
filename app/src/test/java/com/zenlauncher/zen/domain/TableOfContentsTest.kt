package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.reading.PdfPageText
import com.zenlauncher.zen.domain.reading.TableOfContents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **No se da por supuesto ningun formato de indice.** Estos tests recogen las formas que
 * de verdad aparecen en libros y apuntes: con puntos conductores, con tabulaciones, con
 * numeracion delante y sin ella.
 */
class TableOfContentsTest {

    private fun page(number: Int, vararg lines: String) =
        PdfPageText(page = number, text = lines.joinToString("\n"))

    private val indiceConPuntos = page(
        2,
        "ÍNDICE",
        "Introduccion ........................ 5",
        "1. El problema del ser .............. 12",
        "2. La conciencia .................... 34",
        "3. Libertad ......................... 67",
    )

    @Test
    fun `un indice con puntos conductores se entiende entero`() {
        val detection = TableOfContents.detect(listOf(page(0, "Portada"), page(1, ""), indiceConPuntos))

        assertTrue(detection.found)
        assertEquals(4, detection.entries.size)
        assertEquals("Introduccion", detection.entries[0].title)
        assertEquals(5, detection.entries[0].printedPage)
        assertEquals("3. Libertad", detection.entries[3].title)
        assertEquals(67, detection.entries[3].printedPage)
    }

    @Test
    fun `las paginas del indice se devuelven para poder sacarlas del cuerpo`() {
        val detection = TableOfContents.detect(listOf(page(0, "Portada"), indiceConPuntos))

        assertEquals(setOf(2), detection.pages)
    }

    /**
     * La numeracion se queda dentro del titulo: sin ella, tres secciones seguidas se
     * llamarian "Determinismo", "Eleccion" y "Libertad" sin decir de que capitulo son.
     * De ella solo se saca el nivel.
     */
    @Test
    fun `la jerarquia sale de la numeracion`() {
        val detection = TableOfContents.detect(
            listOf(
                page(
                    0,
                    "CONTENIDO",
                    "3. Libertad ............ 67",
                    "3.1 Libertad y responsabilidad ...... 70",
                    "3.2 Determinismo ....... 78",
                    "3.3 Eleccion ........... 85",
                ),
            ),
        )

        assertEquals(listOf(1, 2, 2, 2), detection.entries.map { it.level })
        assertEquals("3.1 Libertad y responsabilidad", detection.entries[1].title)
    }

    @Test
    fun `un indice sin puntos, solo con espacios, tambien se entiende`() {
        val detection = TableOfContents.detect(
            listOf(
                page(
                    0,
                    "SUMARIO",
                    "Prologo    9",
                    "Primera parte    21",
                    "Segunda parte    140",
                    "Epilogo    288",
                ),
            ),
        )

        assertTrue(detection.found)
        assertEquals(listOf(9, 21, 140, 288), detection.entries.map { it.printedPage })
    }

    /**
     * Sin exigir que los numeros suban, una bibliografia con anos al final —"Sartre,
     * J. 1943"— pasaria por indice y el lector llevaria a paginas inventadas.
     */
    @Test
    fun `una lista con numeros desordenados no es un indice`() {
        val detection = TableOfContents.detect(
            listOf(
                page(
                    0,
                    "BIBLIOGRAFIA",
                    "Sartre, Jean-Paul 1943",
                    "Heidegger, Martin 1927",
                    "Kant, Immanuel 1781",
                    "Husserl, Edmund 1913",
                ),
            ),
        )

        assertFalse(detection.found)
    }

    @Test
    fun `unos apuntes sin indice no inventan ninguno`() {
        val detection = TableOfContents.detect(
            listOf(
                page(0, "Apuntes de metafisica"),
                page(1, "Hoy hemos visto el problema del ser y sus principales respuestas."),
            ),
        )

        assertFalse(detection.found)
        assertTrue(detection.entries.isEmpty())
        assertTrue(detection.pages.isEmpty())
    }

    @Test
    fun `una linea suelta se entiende o se descarta`() {
        assertEquals(12, TableOfContents.parse("El problema del ser ....... 12")?.printedPage)
        assertNull(TableOfContents.parse("y esa libertad no admite excusa ninguna"))
        // Sin titulo con letras no hay entrada: "12 .... 34" no dice nada.
        assertNull(TableOfContents.parse("12 ....... 34"))
    }

    /**
     * El rotulo tiene que ser la linea entera. Sin eso, "el indice de refraccion del
     * agua" dentro de un parrafo abriria un indice donde no lo hay.
     */
    @Test
    fun `la palabra indice dentro de una frase no abre un indice`() {
        val detection = TableOfContents.detect(
            listOf(
                page(
                    0,
                    "Vamos a estudiar el indice de refraccion del agua a distintas",
                    "temperaturas y presiones, empezando por el caso mas sencillo.",
                ),
            ),
        )

        assertFalse(detection.found)
    }
}
