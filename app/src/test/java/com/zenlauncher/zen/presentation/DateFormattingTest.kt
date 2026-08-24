package com.zenlauncher.zen.presentation

import com.zenlauncher.zen.presentation.util.ZenDateFormats
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * La fecha corta de la lista de notas.
 *
 * La zona horaria se fija a mano: `shortDate` resuelve la del sistema en cada llamada
 * —para que cambiar de pais no deje el reloj desfasado—, y sin fijarla aqui el test
 * pasaria o fallaria segun donde se ejecute.
 */
class DateFormattingTest {

    private val original: TimeZone = TimeZone.getDefault()
    private val es = Locale("es", "ES")

    // 15 de marzo de 2026, 12:00 en Madrid.
    private val marzo2026 = 1_773_570_000_000L

    @Before
    fun fixTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(original)
    }

    @Test
    fun `una nota del ano en curso no repite el ano`() {
        // En una lista donde todas las filas dicen el mismo ano, el ano no distingue
        // ninguna fila de otra: solo gasta ancho.
        val enero = marzo2026 - 60L * 24 * 60 * 60 * 1000

        assertEquals("14 ENE", ZenDateFormats.shortDate(enero, marzo2026, es))
    }

    @Test
    fun `una nota de otro ano si lo lleva`() {
        // Encontrarse una idea de hace dos anos es justo la informacion que importa.
        val haceDosAnos = marzo2026 - 730L * 24 * 60 * 60 * 1000

        assertEquals("15 MAR 24", ZenDateFormats.shortDate(haceDosAnos, marzo2026, es))
    }

    @Test
    fun `el mes abreviado no arrastra el punto de la localizacion`() {
        // En castellano el mes abreviado sale como "ene."; en una franja tecnica
        // monoespaciada ese punto es un caracter que no dice nada.
        val resultado = ZenDateFormats.shortDate(marzo2026, marzo2026, es)

        assertEquals(false, resultado.contains("."))
    }
}
