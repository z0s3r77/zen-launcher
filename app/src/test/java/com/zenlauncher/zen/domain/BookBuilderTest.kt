package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.reading.BlockKind
import com.zenlauncher.zen.domain.reading.BookBuilder
import com.zenlauncher.zen.domain.reading.PdfDocumentText
import com.zenlauncher.zen.domain.reading.PdfPageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El montaje completo de un libro: portada, indice impreso, cuerpo y navegacion.
 *
 * El orden importa y esta fijado aqui: el indice impreso se detecta **antes** de
 * reconstruir el cuerpo. Al reves, el lector empezaria el libro leyendo su propio
 * indice como si fuera texto.
 */
class BookBuilderTest {

    private fun page(number: Int, vararg lines: String) =
        PdfPageText(page = number, text = lines.joinToString("\n"))

    private fun document(vararg pages: PdfPageText, fileName: String = "libro.pdf") =
        PdfDocumentText(pages = pages.toList(), fileName = fileName)

    private val libro = document(
        page(0, "El ser y la nada", "Jean-Paul Sartre"),
        page(
            1,
            "ÍNDICE",
            "Introduccion ............ 1",
            "1. El problema del ser .. 2",
            "2. La conciencia ........ 3",
        ),
        page(
            2,
            "Introduccion",
            "Este libro trata de lo que somos cuando ya no queda nadie que nos mire de",
            "frente, y de lo poco que eso se parece a lo que creiamos ser hasta ahora.",
            "Punto final.",
        ),
        page(
            3,
            "1. El problema del ser",
            "El ser es y el no ser no es, decia Parmenides, y con esa frase se abrio un",
            "camino que la filosofia lleva recorriendo desde entonces sin descanso ya.",
            "Se acaba aqui.",
        ),
        page(
            4,
            "2. La conciencia",
            "La conciencia constituye uno de los problemas fundamentales de toda la",
            "filosofia moderna, y no por falta de respuestas sino por exceso de ellas.",
            "Y hasta aqui.",
        ),
    )

    @Test
    fun `saca titulo y autor de la portada`() {
        val built = BookBuilder.build(libro)

        assertEquals("El ser y la nada", built.title)
        assertEquals("Jean-Paul Sartre", built.author)
        assertEquals(5, built.pageCount)
    }

    /**
     * Regresion: sin excluir las paginas del indice, sus lineas ("Introduccion ..... 1")
     * llegan al cuerpo y el lector abre el libro por una lista de titulos con numeros.
     */
    @Test
    fun `el indice impreso no aparece como texto del libro`() {
        val built = BookBuilder.build(libro)

        assertTrue(built.blocks.none { it.text.contains("....") })
        assertTrue(built.blocks.none { it.text == "ÍNDICE" })
    }

    @Test
    fun `el indice del libro lleva a los titulos de verdad`() {
        val built = BookBuilder.build(libro)

        assertEquals(
            listOf("Introduccion", "1. El problema del ser", "2. La conciencia"),
            built.chapters.map { it.title },
        )
        // Cada entrada apunta a un bloque que **es** ese titulo dentro del cuerpo, no a
        // una pagina calculada a ojo.
        built.chapters.forEach { chapter ->
            val target = built.blocks[chapter.blockIndex]
            assertEquals(BlockKind.HEADING, target.kind)
            assertEquals(chapter.title, target.text)
        }
    }

    @Test
    fun `el indice va en orden de lectura`() {
        val built = BookBuilder.build(libro)

        assertEquals(
            built.chapters.map { it.blockIndex },
            built.chapters.map { it.blockIndex }.sorted(),
        )
    }

    /**
     * El plan B: unos apuntes sin indice impreso se navegan con los titulos que se
     * detectan en el cuerpo. Siempre hay una forma de moverse por el documento.
     */
    @Test
    fun `sin indice impreso se navega con los titulos del cuerpo`() {
        val apuntes = document(
            page(0, "Apuntes de metafisica"),
            page(
                1,
                "1. El problema del ser",
                "El ser es y el no ser no es, decia Parmenides, y con esa frase se abrio un",
                "camino larguisimo.",
            ),
            page(
                2,
                "2. La conciencia",
                "La conciencia constituye uno de los problemas fundamentales de toda la",
                "filosofia moderna.",
            ),
        )

        val built = BookBuilder.build(apuntes)

        // El rotulo de la portada tambien cuenta como titulo, y esta bien que cuente:
        // en un documento sin indice, la primera entrada es "volver al principio". Lo
        // que se comprueba es que los dos capitulos de verdad esten y en orden.
        assertTrue(built.chapters.map { it.title }.containsAll(
            listOf("1. El problema del ser", "2. La conciencia"),
        ))
        assertEquals(
            built.chapters.map { it.blockIndex },
            built.chapters.map { it.blockIndex }.sorted(),
        )
    }

    @Test
    fun `un texto corrido sin titulos da un libro sin indice pero legible`() {
        val corrido = document(
            page(
                0,
                "Un texto seguido que no tiene ni titulos ni indice ni nada que se le",
                "parezca, solamente parrafos uno detras de otro hasta el final de todo.",
                "Y ya.",
            ),
        )

        val built = BookBuilder.build(corrido)

        assertTrue(built.readable)
        assertTrue(built.chapters.isEmpty())
    }

    /**
     * Un PDF escaneado abre bien pero no tiene ni una letra. Se distingue de un fallo
     * de lectura para poder decirle al usuario exactamente que le pasa a su fichero.
     */
    @Test
    fun `un escaneo sin texto se marca como no legible`() {
        val escaneo = document(page(0, ""), page(1, "   "))

        assertFalse(BookBuilder.build(escaneo).readable)
    }
}
