package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.SessionProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionProgressCalculatorTest {

    private val startWall = 1_700_000_000_000L
    private val startElapsed = 500_000L
    private val thirtyMinutes = 30 * 60_000L

    private fun session(planned: Long = thirtyMinutes) = ActiveSession(
        id = "s1",
        startedAtWallMillis = startWall,
        startedAtElapsedMillis = startElapsed,
        plannedDurationMillis = planned,
        initialBatteryPercent = 84,
        initialCharging = false,
        restrictedAppsCount = 4,
    )

    @Test
    fun `calcula el tiempo restante con ambos relojes avanzando a la vez`() {
        val tenMinutes = 10 * 60_000L
        val progress = SessionProgressCalculator.progress(
            session = session(),
            nowWallMillis = startWall + tenMinutes,
            nowElapsedMillis = startElapsed + tenMinutes,
        )

        assertEquals(tenMinutes, progress.elapsedMillis)
        assertEquals(20 * 60_000L, progress.remainingMillis)
        assertFalse(progress.isExpired)
        assertFalse(progress.clockAnomaly)
    }

    @Test
    fun `marca vencida al alcanzar exactamente la duracion planificada`() {
        val progress = SessionProgressCalculator.progress(
            session = session(),
            nowWallMillis = startWall + thirtyMinutes,
            nowElapsedMillis = startElapsed + thirtyMinutes,
        )

        assertTrue(progress.isExpired)
        assertEquals(0L, progress.remainingMillis)
    }

    @Test
    fun `el restante nunca es negativo aunque se consulte mucho despues`() {
        val progress = SessionProgressCalculator.progress(
            session = session(),
            nowWallMillis = startWall + thirtyMinutes * 10,
            nowElapsedMillis = startElapsed + thirtyMinutes * 10,
        )

        assertEquals(0L, progress.remainingMillis)
        assertTrue(progress.isExpired)
    }

    @Test
    fun `si el usuario atrasa el reloj el tiempo sigue contando con el monotono`() {
        val tenMinutes = 10 * 60_000L
        val progress = SessionProgressCalculator.progress(
            session = session(),
            nowWallMillis = startWall - 60 * 60_000L,
            nowElapsedMillis = startElapsed + tenMinutes,
        )

        assertEquals(tenMinutes, progress.elapsedMillis)
        assertTrue(progress.clockAnomaly)
        assertFalse(progress.isExpired)
    }

    @Test
    fun `si el usuario adelanta el reloj no puede saltarse la sesion`() {
        val progress = SessionProgressCalculator.progress(
            session = session(),
            nowWallMillis = startWall + 24 * 60 * 60_000L,
            nowElapsedMillis = startElapsed + 5 * 60_000L,
        )

        assertEquals(5 * 60_000L, progress.elapsedMillis)
        assertFalse(progress.isExpired)
        assertTrue(progress.clockAnomaly)
    }

    @Test
    fun `tras reiniciar el dispositivo se usa el reloj de pared`() {
        val twelveMinutes = 12 * 60_000L
        val progress = SessionProgressCalculator.progress(
            session = session(),
            nowWallMillis = startWall + twelveMinutes,
            nowElapsedMillis = 3_000L,
        )

        assertEquals(twelveMinutes, progress.elapsedMillis)
        assertEquals(18 * 60_000L, progress.remainingMillis)
        assertFalse(progress.clockAnomaly)
    }

    @Test
    fun `tolera el desfase normal de sincronizacion sin marcar anomalia`() {
        val tenMinutes = 10 * 60_000L
        val progress = SessionProgressCalculator.progress(
            session = session(),
            nowWallMillis = startWall + tenMinutes + 5_000L,
            nowElapsedMillis = startElapsed + tenMinutes,
        )

        assertFalse(progress.clockAnomaly)
    }
}
