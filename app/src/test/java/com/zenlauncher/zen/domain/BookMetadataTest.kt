package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.reading.BookMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android no da metadatos de un PDF —`PdfRenderer` no expone el diccionario `/Info`—,
 * asi que el titulo y el autor se leen de la portada como los leeria una persona.
 */
class BookMetadataTest {

    @Test
    fun `la portada da titulo y autor`() {
        val info = BookMetadata.detect(
            coverText = "El ser y la nada\nJean-Paul Sartre\nEditorial Losada",
            fileName = "cualquiera.pdf",
        )

        assertEquals("El ser y la nada", info.title)
        assertEquals("Jean-Paul Sartre", info.author)
    }

    /**
     * Un "autor desconocido" es texto que ocupa sitio para no decir nada: la ficha de la
     * biblioteca simplemente no pinta la linea.
     */
    @Test
    fun `sin nada que parezca un nombre el autor se queda vacio`() {
        val info = BookMetadata.detect(
            coverText = "Apuntes de metafisica\ncurso 2025 2026 del segundo cuatrimestre",
            fileName = "apuntes.pdf",
        )

        assertEquals("Apuntes de metafisica", info.title)
        assertNull(info.author)
    }

    /**
     * Casi todos los apuntes que circulan por la universidad se llaman asi. Ensenar el
     * nombre del fichero tal cual en la biblioteca es ensenar un fichero, no un libro.
     */
    @Test
    fun `sin portada legible se recurre al nombre del fichero, presentable`() {
        val info = BookMetadata.detect(
            coverText = "",
            fileName = "sartre_el-ser-y-la-nada.pdf",
        )

        assertEquals("sartre el ser y la nada", info.title)
        assertFalse(info.title.contains(".pdf"))
    }

    @Test
    fun `un nombre de persona se distingue de una frase`() {
        assertTrue(BookMetadata.plausibleAuthor("Jean-Paul Sartre"))
        assertTrue(BookMetadata.plausibleAuthor("Maria Zambrano"))
        assertTrue(BookMetadata.plausibleAuthor("Jose Ortega y Gasset"))
        assertFalse(BookMetadata.plausibleAuthor("uno de los problemas fundamentales de la"))
        assertFalse(BookMetadata.plausibleAuthor("Losada, 1943"))
        assertFalse(BookMetadata.plausibleAuthor("Sartre"))
    }

    @Test
    fun `lo que no es titulo ni autor se salta`() {
        val info = BookMetadata.detect(
            coverText = "www.editorial.es\nISBN 978-84-000-0000-0\nMeditaciones metafisicas\n" +
                "Rene Descartes",
            fileName = "x.pdf",
        )

        assertEquals("Meditaciones metafisicas", info.title)
        assertEquals("Rene Descartes", info.author)
    }
}
