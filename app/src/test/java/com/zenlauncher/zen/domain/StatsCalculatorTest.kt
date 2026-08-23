package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.model.SessionOutcome
import com.zenlauncher.zen.domain.model.ZenSession
import com.zenlauncher.zen.domain.stats.StatsCalculator
import com.zenlauncher.zen.domain.stats.ZenStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

    private fun session(
        id: String = "s",
        durationMinutes: Int = 30,
        completed: Boolean = true,
        initialBattery: Int = 84,
        finalBattery: Int = 80,
        initialCharging: Boolean = false,
        finalCharging: Boolean = false,
    ) = ZenSession(
        id = id,
        startedAtMillis = 0,
        endedAtMillis = durationMinutes * 60_000L,
        plannedDurationMillis = durationMinutes * 60_000L,
        actualDurationMillis = durationMinutes * 60_000L,
        initialBatteryPercent = initialBattery,
        finalBatteryPercent = finalBattery,
        initialCharging = initialCharging,
        finalCharging = finalCharging,
        outcome = if (completed) SessionOutcome.COMPLETED else SessionOutcome.ABANDONED,
        restrictedAppsCount = 4,
    )

    @Test
    fun `sin sesiones devuelve el resumen vacio y no divide entre cero`() {
        assertEquals(ZenStats.Empty, StatsCalculator.from(emptyList()))
        assertTrue(StatsCalculator.from(emptyList()).isEmpty)
    }

    @Test
    fun `agrega totales, media y sesion mas larga`() {
        val stats = StatsCalculator.from(
            listOf(
                session(id = "a", durationMinutes = 30),
                session(id = "b", durationMinutes = 90),
                session(id = "c", durationMinutes = 60, completed = false),
            ),
        )

        assertEquals(180 * 60_000L, stats.totalZenMillis)
        assertEquals(90 * 60_000L, stats.longestSessionMillis)
        assertEquals(60 * 60_000L, stats.averageSessionMillis)
        assertEquals(2, stats.completedCount)
        assertEquals(1, stats.abandonedCount)
        assertEquals(67, stats.completionRatePercent)
    }

    @Test
    fun `suma la bateria consumida de las sesiones fiables`() {
        val stats = StatsCalculator.from(
            listOf(
                session(id = "a", initialBattery = 84, finalBattery = 80),
                session(id = "b", initialBattery = 80, finalBattery = 77),
            ),
        )

        assertEquals(7, stats.batteryConsumedPercent)
        assertEquals(2, stats.batterySampleCount)
        assertTrue(stats.hasBatteryData)
    }

    @Test
    fun `descarta la bateria cuando el porcentaje final es mayor que el inicial`() {
        val stats = StatsCalculator.from(
            listOf(session(initialBattery = 60, finalBattery = 75)),
        )

        assertEquals(0, stats.batteryConsumedPercent)
        assertEquals(0, stats.batterySampleCount)
        assertFalse(stats.hasBatteryData)
    }

    @Test
    fun `descarta la bateria si el dispositivo estuvo cargando`() {
        val stats = StatsCalculator.from(
            listOf(
                session(id = "a", initialCharging = true),
                session(id = "b", finalCharging = true),
                session(id = "c", initialBattery = 50, finalBattery = 45),
            ),
        )

        assertEquals(5, stats.batteryConsumedPercent)
        assertEquals(1, stats.batterySampleCount)
    }

    @Test
    fun `una unica sesion abandonada da cero por ciento de completadas`() {
        val stats = StatsCalculator.from(listOf(session(completed = false)))

        assertEquals(0, stats.completionRatePercent)
        assertEquals(1, stats.totalCount)
    }
}
