package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.scanner.PdfPageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El tamano de cada hoja del PDF.
 *
 * Es donde se pierde la proporcion sin que nadie se entere: el PDF se mide en puntos y la
 * imagen en pixeles, y un escaneo estirado dentro de un A4 se ve "bien" hasta que se
 * imprime.
 */
class PdfPageSizeTest {

    @Test
    fun `un escaneo vertical cabe a lo alto de un A4`() {
        val (ancho, alto) = PdfPageSize.forImage(1600, 2263)

        assertEquals(PdfPageSize.A4_HEIGHT_POINTS, alto)
        assertTrue("No deberia pasarse de ancho: $ancho", ancho <= PdfPageSize.A4_WIDTH_POINTS)
    }

    @Test
    fun `la proporcion de la imagen se mantiene`() {
        val proporcion = 1600f / 2263f
        val (ancho, alto) = PdfPageSize.forImage(1600, 2263)

        assertEquals(proporcion, ancho.toFloat() / alto, 0.01f)
    }

    @Test
    fun `un escaneo apaisado pone la hoja en horizontal`() {
        // Un apaisado dentro de un A4 vertical deja dos franjas blancas enormes y sale
        // minusculo al imprimir.
        val (ancho, alto) = PdfPageSize.forImage(2263, 1600)

        assertTrue("Deberia salir apaisado: $ancho x $alto", ancho > alto)
        assertEquals(PdfPageSize.A4_HEIGHT_POINTS, ancho)
        assertEquals(2263f / 1600f, ancho.toFloat() / alto, 0.01f)
    }

    @Test
    fun `una imagen cuadrada no se estira`() {
        val (ancho, alto) = PdfPageSize.forImage(2000, 2000)
        assertEquals(ancho, alto)
        assertEquals(PdfPageSize.A4_WIDTH_POINTS, ancho)
    }

    @Test
    fun `una imagen sin medidas cae al A4 entero en lugar de reventar`() {
        assertEquals(
            PdfPageSize.A4_WIDTH_POINTS to PdfPageSize.A4_HEIGHT_POINTS,
            PdfPageSize.forImage(0, 0),
        )
        assertEquals(
            PdfPageSize.A4_WIDTH_POINTS to PdfPageSize.A4_HEIGHT_POINTS,
            PdfPageSize.forImage(-4, 100),
        )
    }

    @Test
    fun `la hoja nunca se sale del A4`() {
        for (ancho in listOf(100, 800, 2400, 4000)) {
            for (alto in listOf(100, 800, 2400, 4000)) {
                val (w, h) = PdfPageSize.forImage(ancho, alto)
                assertTrue(
                    "$ancho x $alto dio $w x $h",
                    maxOf(w, h) <= PdfPageSize.A4_HEIGHT_POINTS &&
                        minOf(w, h) <= PdfPageSize.A4_WIDTH_POINTS,
                )
            }
        }
    }
}
