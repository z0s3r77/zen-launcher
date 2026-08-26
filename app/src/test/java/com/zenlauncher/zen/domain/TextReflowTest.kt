package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.reading.BlockKind
import com.zenlauncher.zen.domain.reading.PdfPageText
import com.zenlauncher.zen.domain.reading.TextReflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que separa "ensenar un PDF" de "leer un libro" pasa entero por aqui: si la
 * reconstruccion falla, el lector ensena las lineas sueltas de la hoja original y no ha
 * servido de nada.
 */
class TextReflowTest {

    private fun page(number: Int, vararg lines: String) =
        PdfPageText(page = number, text = lines.joinToString("\n"))

    @Test
    fun `las lineas de un parrafo se unen en uno solo`() {
        val pages = listOf(
            page(
                0,
                "La conciencia constituye uno de los problemas mas discutidos de toda",
                "la filosofia moderna, y no por falta de respuestas sino justamente por",
                "el exceso de ellas.",
            ),
        )

        val blocks = TextReflow.blocks(pages)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0].text.startsWith("La conciencia constituye uno"))
        assertTrue(blocks[0].text.endsWith("el exceso de ellas."))
        assertFalse("No debe quedar ningun salto de linea dentro", blocks[0].text.contains('\n'))
    }

    /**
     * La senal de fin de parrafo es el **ancho**, no la puntuacion: en filosofia los
     * parrafos encadenan comas y dos puntos durante media pagina.
     */
    @Test
    fun `una linea corta cierra el parrafo y la siguiente empieza otro`() {
        val pages = listOf(
            page(
                0,
                "Primera linea larga que llega hasta el borde derecho de la caja de texto",
                "y sigue llenando el ancho completo de la misma caja sin dejar ni un hueco",
                "y termina aqui.",
                "Segunda linea larga que vuelve a llenar el ancho completo de la caja de",
                "texto hasta el borde derecho igual que hacia la primera de todas ellas.",
            ),
        )

        val blocks = TextReflow.blocks(pages)

        assertEquals(2, blocks.size)
        assertTrue(blocks[1].text.startsWith("Segunda linea larga"))
    }

    /**
     * Regresion: sin deshacer el guion de corte, el buscador no encontraria "libertad"
     * en la mitad de las paginas del libro porque ahi dentro pone "liber- tad".
     */
    @Test
    fun `la palabra partida con guion se vuelve a unir`() {
        val pages = listOf(
            page(
                0,
                "El hombre esta condenado a ser libre, y esa liber-",
                "tad no admite excusa ninguna, ni siquiera la de haber nacido asi de todo.",
            ),
        )

        val blocks = TextReflow.blocks(pages)

        assertTrue(blocks[0].text.contains("libertad"))
        assertFalse(blocks[0].text.contains("liber- tad"))
        assertFalse(blocks[0].text.contains("liber-tad"))
    }

    /**
     * Un guion de verdad no es un corte de linea. Con la regla de "quitar siempre el
     * guion final", "1939-1945" acabaria escrito "19391945" en el texto del libro.
     */
    @Test
    fun `el guion entre cifras o antes de mayuscula se conserva`() {
        val pages = listOf(
            page(
                0,
                "La guerra ocupa todo el periodo comprendido entre los anos 1939-",
                "1945, y marca el corte del que arranca este capitulo entero sin excepcion.",
            ),
        )

        val blocks = TextReflow.blocks(pages)

        assertTrue(blocks[0].text.contains("1939- 1945") || blocks[0].text.contains("1939-1945"))
        assertFalse(blocks[0].text.contains("19391945"))
    }

    /**
     * La cabecera del libro se repite en todas las hojas y con el numero cambiando, asi
     * que se compara sin cifras. Sin esto, cada pagina meteria el titulo del libro en
     * mitad del texto.
     */
    @Test
    fun `la cabecera repetida y el folio no llegan al texto`() {
        val pages = (0 until 6).map { number ->
            page(
                number,
                "EL SER Y LA NADA",
                "Cuerpo de la pagina numero $number con texto suficiente para llenar la caja",
                "de la hoja entera de lado a lado sin dejar hueco ninguno por la derecha",
                // Linea corta: cierra el parrafo de esta pagina, como en un libro real.
                "y aqui termina.",
                "${number + 12}",
            )
        }

        val blocks = TextReflow.blocks(pages)

        assertTrue(blocks.none { it.text.contains("EL SER Y LA NADA") })
        assertTrue(blocks.none { it.text.trim().all(Char::isDigit) })
        assertEquals(6, blocks.size)
    }

    @Test
    fun `las paginas del indice se pueden excluir del cuerpo`() {
        val pages = listOf(
            page(0, "Introduccion .... 5", "El problema del ser .... 12"),
            page(1, "Aqui empieza el cuerpo del libro con un parrafo cualquiera de prueba."),
        )

        val blocks = TextReflow.blocks(pages, skipPages = setOf(0))

        assertEquals(1, blocks.size)
        assertTrue(blocks[0].text.startsWith("Aqui empieza el cuerpo"))
    }

    @Test
    fun `un titulo numerado corta el parrafo anterior aunque venga de una linea larga`() {
        val pages = listOf(
            page(
                0,
                "Ultima linea del capitulo anterior que llena el ancho completo de la caja",
                "3. Libertad",
                "Primera linea del capitulo nuevo que tambien llena el ancho de la caja de",
                "texto para que no se cierre el parrafo antes de tiempo por ser corta.",
            ),
        )

        val blocks = TextReflow.blocks(pages)

        val heading = blocks.single { it.kind == BlockKind.HEADING }
        assertEquals("3. Libertad", heading.text)
    }

    @Test
    fun `los bloques van numerados en orden y saben de que pagina salieron`() {
        val pages = listOf(
            page(
                0,
                "Parrafo de la primera pagina que llena el ancho completo de la caja de",
                "corto.",
            ),
            page(
                1,
                "Parrafo de la segunda pagina que llena el ancho completo de la caja de",
                "corto.",
            ),
        )

        val blocks = TextReflow.blocks(pages)

        assertEquals(listOf(0, 1), blocks.map { it.index })
        assertEquals(listOf(0, 1), blocks.map { it.page })
    }

    @Test
    fun `un numero de pagina se reconoce en cifras y en romanos`() {
        assertTrue(TextReflow.isPageNumber("87"))
        assertTrue(TextReflow.isPageNumber("- 12 -"))
        assertTrue(TextReflow.isPageNumber("xiv"))
        assertFalse(TextReflow.isPageNumber("Capitulo 3"))
        assertFalse(TextReflow.isPageNumber("1234567890123"))
    }

    @Test
    fun `un documento sin ni una linea no revienta`() {
        assertTrue(TextReflow.blocks(emptyList()).isEmpty())
        assertTrue(TextReflow.blocks(listOf(page(0, "", "   "))).isEmpty())
    }
}
