package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.battery.BatteryStatus
import com.zenlauncher.zen.domain.model.SessionOutcome
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.session.DefaultZenSessionManager
import com.zenlauncher.zen.fakes.FakeBatteryReader
import com.zenlauncher.zen.fakes.FakePreferencesRepository
import com.zenlauncher.zen.fakes.FakeSessionRepository
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.RecordingAlarmScheduler
import com.zenlauncher.zen.fakes.RecordingRestrictionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultZenSessionManagerTest {

    private lateinit var clock: FakeZenClock
    private lateinit var preferences: FakePreferencesRepository
    private lateinit var sessions: FakeSessionRepository
    private lateinit var battery: FakeBatteryReader
    private lateinit var restrictions: RecordingRestrictionManager
    private lateinit var alarms: RecordingAlarmScheduler
    private lateinit var manager: DefaultZenSessionManager

    private val thirtyMinutes = ZenDuration.ofMinutes(30)

    @Before
    fun setUp() {
        clock = FakeZenClock()
        preferences = FakePreferencesRepository(initialRestricted = setOf("com.a", "com.b"))
        sessions = FakeSessionRepository()
        battery = FakeBatteryReader()
        restrictions = RecordingRestrictionManager(preferences)
        alarms = RecordingAlarmScheduler()
        manager = DefaultZenSessionManager(
            preferences = preferences,
            sessions = sessions,
            battery = battery,
            restrictions = restrictions,
            alarms = alarms,
            clock = clock,
            idFactory = { "fixed-id" },
        )
    }

    @Test
    fun `al empezar persiste la sesion, programa la alarma y aplica restricciones`() = runTest {
        val session = manager.start(thirtyMinutes)

        assertEquals("fixed-id", session.id)
        assertEquals(thirtyMinutes.millis, session.plannedDurationMillis)
        assertEquals(84, session.initialBatteryPercent)
        assertEquals(2, session.restrictedAppsCount)

        // Persistida antes de nada: si el proceso muere, la sesion sobrevive.
        assertEquals(session, preferences.currentActiveSession())
        assertEquals(session, alarms.scheduled)
        assertEquals(1, restrictions.enforceCount)
    }

    @Test
    fun `terminar antes de tiempo la registra como abandonada con la duracion real`() = runTest {
        manager.start(thirtyMinutes)
        clock.advance(10 * 60_000L)
        battery.status = BatteryStatus(percent = 80, charging = false)

        val record = manager.finishNow()

        assertNotNull(record)
        assertEquals(SessionOutcome.ABANDONED, record!!.outcome)
        assertEquals(10 * 60_000L, record.actualDurationMillis)
        assertEquals(thirtyMinutes.millis, record.plannedDurationMillis)
        assertEquals(84, record.initialBatteryPercent)
        assertEquals(80, record.finalBatteryPercent)
        assertEquals(4, record.batteryConsumedPercent)

        // Y deja el dispositivo limpio.
        assertNull(preferences.currentActiveSession())
        assertEquals(1, alarms.cancelCount)
        assertEquals(1, restrictions.releaseCount)
    }

    @Test
    fun `resolveExpired no hace nada mientras queda tiempo`() = runTest {
        manager.start(thirtyMinutes)
        clock.advance(29 * 60_000L)

        assertNull(manager.resolveExpired())
        assertNotNull(preferences.currentActiveSession())
    }

    @Test
    fun `resolveExpired cierra como completada al vencer el tiempo`() = runTest {
        manager.start(thirtyMinutes)
        clock.advance(thirtyMinutes.millis)

        val record = manager.resolveExpired()

        assertNotNull(record)
        assertEquals(SessionOutcome.COMPLETED, record!!.outcome)
        assertEquals(thirtyMinutes.millis, record.actualDurationMillis)
        assertNull(preferences.currentActiveSession())
    }

    @Test
    fun `una sesion completada dura lo planificado aunque el aviso llegue tarde`() = runTest {
        manager.start(thirtyMinutes)
        // La alarma se retrasa veinte minutos (Doze, dispositivo apagado...).
        clock.advance(thirtyMinutes.millis + 20 * 60_000L)

        val record = manager.resolveExpired()!!

        assertEquals(SessionOutcome.COMPLETED, record.outcome)
        // No se infla el tiempo registrado con el retraso del aviso.
        assertEquals(thirtyMinutes.millis, record.actualDurationMillis)
    }

    @Test
    fun `resolveExpired es idempotente si se llama dos veces`() = runTest {
        manager.start(thirtyMinutes)
        clock.advance(thirtyMinutes.millis)

        val first = manager.resolveExpired()
        val second = manager.resolveExpired()

        assertNotNull(first)
        // La segunda no encuentra sesion activa, asi que no registra nada nuevo.
        assertNull(second)
        assertEquals(1, sessions.all().size)
    }

    @Test
    fun `la alarma y la UI cerrando a la vez solo registran una sesion`() = runTest {
        val session = manager.start(thirtyMinutes)
        clock.advance(thirtyMinutes.millis)

        // Se simula la carrera: el registro se intenta dos veces con el mismo id.
        manager.resolveExpired()
        sessions.recordIfAbsent(
            sessions.all().first().copy(actualDurationMillis = 999),
        )

        assertEquals(1, sessions.all().size)
        assertEquals(session.id, sessions.all().first().id)
        assertEquals(thirtyMinutes.millis, sessions.all().first().actualDurationMillis)
    }

    @Test
    fun `al cerrar deja el resumen pendiente de ver`() = runTest {
        manager.start(thirtyMinutes)
        clock.advance(thirtyMinutes.millis)

        manager.resolveExpired()

        // Sin esto, una sesion cerrada por la alarma nunca mostraria su resumen.
        assertEquals("fixed-id", preferences.pendingSummarySessionId.first())
    }

    @Test
    fun `sin sesion activa terminar no falla`() = runTest {
        assertNull(manager.finishNow())
        assertNull(manager.resolveExpired())
        assertTrue(sessions.all().isEmpty())
    }

    @Test
    fun `si la bateria no se puede leer la sesion se registra igualmente`() = runTest {
        battery.status = BatteryStatus.Unknown
        manager.start(thirtyMinutes)
        clock.advance(5 * 60_000L)

        val record = manager.finishNow()!!

        assertEquals(-1, record.initialBatteryPercent)
        // Sin lectura fiable no se inventa un consumo.
        assertNull(record.batteryConsumedPercent)
    }
}
