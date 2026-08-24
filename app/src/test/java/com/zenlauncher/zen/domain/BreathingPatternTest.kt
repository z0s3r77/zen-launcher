package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.breathing.BreathPhase
import com.zenlauncher.zen.domain.breathing.BreathingPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathingPatternTest {

    @Test
    fun `el minuto son seis ciclos exactos`() {
        // El corte del ejercicio no puede caer en mitad de una respiracion: si el
        // patron y el minuto no encajan, la ultima espiracion se queda a medias y lo
        // que se aprende es a soltar el aire de golpe cuando la pantalla lo dice.
        assertEquals(60_000L, BreathingPattern.TOTAL_MILLIS)
        assertEquals(60_000L, BreathingPattern.CYCLE_MILLIS * BreathingPattern.CYCLES)
        assertEquals(
            BreathingPattern.BREATHS_PER_MINUTE.toLong(),
            60_000L / BreathingPattern.CYCLE_MILLIS,
        )
    }

    @Test
    fun `se espira mas tiempo del que se inspira`() {
        // La razon de ser del patron: el freno vagal actua al soltar el aire. Si algun
        // dia alguien "redondea" los dos tramos a cinco segundos, el ejercicio deja de
        // hacer lo que dice que hace.
        assertTrue(BreathingPattern.EXHALE_MILLIS > BreathingPattern.INHALE_MILLIS)
    }

    @Test
    fun `la curva empieza y termina con los pulmones vacios`() {
        assertEquals(0f, BreathingPattern.amplitudeAt(0L), 0.001f)
        assertEquals(0f, BreathingPattern.amplitudeAt(BreathingPattern.TOTAL_MILLIS), 0.001f)
        assertEquals(0f, BreathingPattern.amplitudeAt(BreathingPattern.CYCLE_MILLIS), 0.001f)
    }

    @Test
    fun `el pico cae justo al terminar de inspirar`() {
        assertEquals(1f, BreathingPattern.amplitudeAt(BreathingPattern.INHALE_MILLIS), 0.001f)
    }

    @Test
    fun `la curva sube durante la inspiracion y baja durante la espiracion`() {
        var previous = BreathingPattern.amplitudeAt(0L)
        var millis = 100L
        while (millis <= BreathingPattern.INHALE_MILLIS) {
            val current = BreathingPattern.amplitudeAt(millis)
            assertTrue("sube en $millis", current > previous)
            previous = current
            millis += 100L
        }
        while (millis <= BreathingPattern.CYCLE_MILLIS) {
            val current = BreathingPattern.amplitudeAt(millis)
            assertTrue("baja en $millis", current < previous)
            previous = current
            millis += 100L
        }
    }

    @Test
    fun `la curva no se sale de sus limites en todo el minuto`() {
        // Se dibuja escalando la amplitud al alto disponible: por encima de 1 la marca
        // se saldria del lienzo.
        for (millis in 0L..BreathingPattern.TOTAL_MILLIS step 50L) {
            val amplitude = BreathingPattern.amplitudeAt(millis)
            assertTrue("$millis -> $amplitude", amplitude in 0f..1f)
        }
    }

    @Test
    fun `fuera del minuto la curva se queda quieta en los extremos`() {
        // La pantalla acota el tiempo, pero el dominio no puede confiar en ello: un
        // desfase de un fotograma no debe reabrir el ciclo por el otro lado.
        assertEquals(0f, BreathingPattern.amplitudeAt(-500L), 0.001f)
        assertEquals(0f, BreathingPattern.amplitudeAt(BreathingPattern.TOTAL_MILLIS + 500L), 0.001f)
    }

    @Test
    fun `la fase cambia al cuarto segundo de cada ciclo`() {
        assertEquals(BreathPhase.INHALE, BreathingPattern.stepAt(0L).phase)
        assertEquals(BreathPhase.INHALE, BreathingPattern.stepAt(3_999L).phase)
        assertEquals(BreathPhase.EXHALE, BreathingPattern.stepAt(4_000L).phase)
        assertEquals(BreathPhase.EXHALE, BreathingPattern.stepAt(9_999L).phase)
        assertEquals(BreathPhase.INHALE, BreathingPattern.stepAt(10_000L).phase)
    }

    @Test
    fun `la cuenta atras de la fase empieza en el total y no un segundo por debajo`() {
        // Redondeo hacia arriba: leer "3" en el primer milisegundo de una inspiracion
        // de cuatro segundos hace pensar que se ha perdido un segundo.
        assertEquals(4, BreathingPattern.stepAt(0L).phaseRemainingSeconds)
        assertEquals(4, BreathingPattern.stepAt(1L).phaseRemainingSeconds)
        assertEquals(1, BreathingPattern.stepAt(3_999L).phaseRemainingSeconds)
        assertEquals(6, BreathingPattern.stepAt(4_000L).phaseRemainingSeconds)
        assertEquals(1, BreathingPattern.stepAt(9_999L).phaseRemainingSeconds)
    }

    @Test
    fun `el ultimo instante sigue siendo el sexto ciclo y no un septimo`() {
        // `elapsed % ciclo` vuelve a cero al terminar: sin acotar, la ultima lectura
        // seria "CICLO 7 / 6".
        assertEquals(1, BreathingPattern.stepAt(0L).cycle)
        assertEquals(1, BreathingPattern.stepAt(9_999L).cycle)
        assertEquals(2, BreathingPattern.stepAt(10_000L).cycle)
        assertEquals(6, BreathingPattern.stepAt(59_999L).cycle)
        assertEquals(6, BreathingPattern.stepAt(BreathingPattern.TOTAL_MILLIS).cycle)
    }

    @Test
    fun `solo esta terminado al llegar al minuto`() {
        assertFalse(BreathingPattern.stepAt(59_999L).finished)
        assertTrue(BreathingPattern.stepAt(BreathingPattern.TOTAL_MILLIS).finished)
        assertEquals(0, BreathingPattern.stepAt(BreathingPattern.TOTAL_MILLIS).remainingSeconds)
        // Terminado no queda ninguna orden pendiente: la cifra de la fase se apaga.
        assertEquals(0, BreathingPattern.stepAt(BreathingPattern.TOTAL_MILLIS).phaseRemainingSeconds)
    }

    @Test
    fun `el tiempo restante se lee en segundos enteros y empieza en sesenta`() {
        assertEquals(60, BreathingPattern.stepAt(0L).remainingSeconds)
        assertEquals(59, BreathingPattern.stepAt(1_000L).remainingSeconds)
        assertEquals(1, BreathingPattern.stepAt(59_001L).remainingSeconds)
    }
}
