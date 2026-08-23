package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.model.formatDurationClock
import com.zenlauncher.zen.domain.model.formatDurationCompact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ZenDurationTest {

    @Test
    fun `los preajustes son los cinco pedidos`() {
        assertEquals(listOf(15, 30, 60, 90, 120), ZenDuration.Presets.map { it.wholeMinutes })
    }

    @Test
    fun `rechaza duraciones fuera de rango en lugar de lanzar`() {
        assertNull(ZenDuration.ofMinutesOrNull(0))
        assertNull(ZenDuration.ofMinutesOrNull(-5))
        assertNull(ZenDuration.ofMinutesOrNull(ZenDuration.MAX_MINUTES + 1))
        assertNull(ZenDuration.ofMinutesOrNull(null))
    }

    @Test
    fun `acepta los extremos del rango valido`() {
        assertNotNull(ZenDuration.ofMinutesOrNull(ZenDuration.MIN_MINUTES))
        assertNotNull(ZenDuration.ofMinutesOrNull(ZenDuration.MAX_MINUTES))
    }

    @Test
    fun `formatea el cronometro en minutos y segundos`() {
        assertEquals("47:12", formatDurationClock(47 * 60_000L + 12_000L))
        assertEquals("00:00", formatDurationClock(0))
        assertEquals("00:00", formatDurationClock(-5_000L))
    }

    @Test
    fun `anade la hora al cronometro solo cuando hace falta`() {
        assertEquals("1:30:00", formatDurationClock(90 * 60_000L))
        assertEquals("59:59", formatDurationClock(59 * 60_000L + 59_000L))
    }

    @Test
    fun `formatea duraciones compactas para el registro`() {
        assertEquals("4h 32m", formatDurationCompact(4 * 3_600_000L + 32 * 60_000L))
        assertEquals("45m", formatDurationCompact(45 * 60_000L))
        assertEquals("1h 0m", formatDurationCompact(60 * 60_000L))
    }

    @Test
    fun `por debajo del minuto muestra segundos y no un cero enganoso`() {
        // Una media de 48 s como "0m" parece un fallo de la app, no un dato.
        assertEquals("48s", formatDurationCompact(48_000L))
        assertEquals("59s", formatDurationCompact(59_999L))
        assertEquals("1m", formatDurationCompact(60_000L))
        assertEquals("0s", formatDurationCompact(0))
        assertEquals("0s", formatDurationCompact(-1_000L))
    }
}
