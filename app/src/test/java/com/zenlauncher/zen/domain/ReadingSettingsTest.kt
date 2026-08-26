package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.reading.ReadingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSettingsTest {

    @Test
    fun `subir la letra sube tambien el interlineado`() {
        val pequena = ReadingSettings().withText(0)
        val grande = ReadingSettings().withText(ReadingSettings.TEXT_STEPS)

        // El interlineado es un multiplo del cuerpo, no un valor propio: subir la letra
        // sin subirlo junta las lineas, que es lo contrario de lo que busca quien
        // acaba de agrandar el texto.
        assertTrue(
            grande.fontSizeSp * grande.lineHeightRatio >
                pequena.fontSizeSp * pequena.lineHeightRatio,
        )
    }

    /**
     * Los escalones se acotan aqui y no en la pantalla: un valor fuera de rango en el
     * fichero de preferencias no puede dejar el lector con letra de tamano cero.
     */
    @Test
    fun `los escalones no se salen de rango`() {
        assertEquals(0, ReadingSettings().withText(-5).textStep)
        assertEquals(ReadingSettings.TEXT_STEPS, ReadingSettings().withText(99).textStep)
        assertEquals(0, ReadingSettings().withMargin(-1).marginStep)
        assertEquals(ReadingSettings.LEADING_STEPS, ReadingSettings().withLeading(99).leadingStep)
    }

    @Test
    fun `un escalon guardado imposible se lee dentro de rango igualmente`() {
        val roto = ReadingSettings(textStep = 99, leadingStep = -3, marginStep = 40)

        assertTrue(roto.fontSizeSp > 0f)
        assertTrue(roto.lineHeightRatio > 1f)
        assertTrue(roto.marginDp > 0)
    }

    @Test
    fun `por defecto se lee en serif`() {
        assertTrue(ReadingSettings().serif)
    }
}
