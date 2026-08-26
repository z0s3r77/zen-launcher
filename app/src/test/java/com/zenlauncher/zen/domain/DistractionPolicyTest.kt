package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.usage.Compulsion
import com.zenlauncher.zen.domain.usage.CompulsionKind
import com.zenlauncher.zen.domain.usage.DistractionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistractionPolicyTest {

    private val ahora = 10_000_000L

    private val compulsion = Compulsion(
        kind = CompulsionKind.ARRASTRE,
        packageName = "com.instagram.android",
        openings = 1,
        foregroundMillis = 45 * 60_000L,
        windowMinutes = 60,
    )

    private fun minutos(value: Int) = value * 60_000L

    @Test
    fun `sin conducta detectada no se interrumpe`() {
        assertFalse(DistractionPolicy.shouldInterrupt(null, null, ahora, sessionActive = false))
    }

    @Test
    fun `la primera vez se interrumpe`() {
        assertTrue(DistractionPolicy.shouldInterrupt(compulsion, null, ahora, sessionActive = false))
    }

    /**
     * Regresion: el patron que disparo el aviso sigue ahi un buen rato despues —las
     * aperturas ya ocurrieron y no se borran—, asi que sin espera el aviso reaparecia en
     * cada vuelta a la pantalla de inicio y se aprendia a descartar sin leerlo.
     */
    @Test
    fun `dentro de la espera no se repite`() {
        val hace10 = ahora - minutos(10)

        assertFalse(DistractionPolicy.shouldInterrupt(compulsion, hace10, ahora, sessionActive = false))
    }

    @Test
    fun `pasada la espera vuelve a interrumpir`() {
        val viejo = ahora - minutos(DistractionPolicy.COOLDOWN_MINUTES + 1)

        assertTrue(DistractionPolicy.shouldInterrupt(compulsion, viejo, ahora, sessionActive = false))
    }

    @Test
    fun `durante una sesion Zen no se interrumpe nunca`() {
        // Quien esta en una sesion ya tomo la decision que el aviso pretende provocar.
        assertFalse(DistractionPolicy.shouldInterrupt(compulsion, null, ahora, sessionActive = true))
    }

    /**
     * Regresion: con la marca en el futuro —cambio de hora, viaje, ajuste por red— la
     * resta salia negativa y la comparacion la trataba como "hace nada", bloqueando el
     * aviso hasta que el reloj alcanzase la marca vieja. Podian ser horas.
     */
    @Test
    fun `una marca en el futuro no bloquea el aviso`() {
        val futuro = ahora + minutos(300)

        assertTrue(DistractionPolicy.shouldInterrupt(compulsion, futuro, ahora, sessionActive = false))
    }
}
