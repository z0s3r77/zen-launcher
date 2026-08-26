package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.weather.WeatherCodes
import com.zenlauncher.zen.domain.weather.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherCodesTest {

    @Test
    fun `el cielo despejado y el casi despejado son el mismo sol`() {
        assertEquals(WeatherCondition.DESPEJADO, WeatherCodes.condition(0))
        assertEquals(WeatherCondition.DESPEJADO, WeatherCodes.condition(1))
    }

    @Test
    fun `nubes y claros no es lo mismo que cubierto`() {
        assertEquals(WeatherCondition.NUBES_CLAROS, WeatherCodes.condition(2))
        assertEquals(WeatherCondition.NUBLADO, WeatherCodes.condition(3))
    }

    /**
     * Llovizna, lluvia y chubasco son tres codigos y una sola decision: coger algo. En
     * una franja de tres caracteres no hay forma de dibujar la diferencia.
     */
    @Test
    fun `llovizna lluvia y chubasco caen en el mismo glifo`() {
        for (code in listOf(51, 55, 57, 61, 65, 67, 80, 82)) {
            assertEquals("codigo $code", WeatherCondition.LLUVIA, WeatherCodes.condition(code))
        }
    }

    @Test
    fun `la nieve y sus chubascos son nieve`() {
        for (code in listOf(71, 75, 77, 85, 86)) {
            assertEquals("codigo $code", WeatherCondition.NIEVE, WeatherCodes.condition(code))
        }
    }

    /** En una tormenta tambien llueve, y lo que define el dia es la tormenta. */
    @Test
    fun `la tormenta tiene glifo propio y no cae en lluvia`() {
        for (code in listOf(95, 96, 99)) {
            assertEquals("codigo $code", WeatherCondition.TORMENTA, WeatherCodes.condition(code))
        }
    }

    @Test
    fun `la niebla y la niebla helada son niebla`() {
        assertEquals(WeatherCondition.NIEBLA, WeatherCodes.condition(45))
        assertEquals(WeatherCondition.NIEBLA, WeatherCodes.condition(48))
    }

    /**
     * Un codigo que no esta en la tabla se queda sin glifo y ensena los grados solos.
     * Traducirlo al mas parecido seria adivinar el tiempo.
     */
    @Test
    fun `un codigo desconocido no se inventa un glifo`() {
        assertNull(WeatherCodes.condition(-1))
        assertNull(WeatherCodes.condition(4))
        assertNull(WeatherCodes.condition(999))
    }
}
