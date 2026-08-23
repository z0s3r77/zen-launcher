package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.data.battery.SystemSettingsBatterySaverController
import com.zenlauncher.zen.domain.model.SessionOutcome
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.session.DefaultZenSessionManager
import com.zenlauncher.zen.fakes.FakeBatteryReader
import com.zenlauncher.zen.fakes.FakePreferencesRepository
import com.zenlauncher.zen.fakes.FakeSessionRepository
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.fakes.RecordingAlarmScheduler
import com.zenlauncher.zen.fakes.RecordingRestrictionManager
import com.zenlauncher.zen.presentation.session.SessionViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

/**
 * Robolectric solo por `BatterySaverController`, que necesita un Context real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeZenClock()
    private val preferences = FakePreferencesRepository()
    private val sessions = FakeSessionRepository()
    private val battery = FakeBatteryReader()

    private val manager = DefaultZenSessionManager(
        preferences = preferences,
        sessions = sessions,
        battery = battery,
        restrictions = RecordingRestrictionManager(preferences),
        alarms = RecordingAlarmScheduler(),
        clock = clock,
        idFactory = { "sesion-1" },
    )

    private fun viewModel() = SessionViewModel(
        sessionManager = manager,
        preferences = preferences,
        sessions = sessions,
        battery = battery,
        batterySaver = SystemSettingsBatterySaverController(
            ApplicationProvider.getApplicationContext(),
        ),
        clock = clock,
    )

    @Test
    fun `el resumen aparece aunque la sesion la cierre la alarma en segundo plano`() = runTest {
        // Regresion: el resumen vivia en memoria del ViewModel, asi que una sesion
        // cerrada por la alarma con la pantalla apagada nunca llegaba a mostrarse.
        manager.start(ZenDuration.ofMinutes(1))
        clock.advance(60_000L)
        manager.resolveExpired() // lo que hace SessionEndReceiver

        viewModel().finished.test {
            assertNull(awaitItem()) // valor inicial

            val summary = awaitItem()
            assertNotNull(summary)
            assertEquals("sesion-1", summary!!.id)
            assertEquals(SessionOutcome.COMPLETED, summary.outcome)
        }
    }

    @Test
    fun `descartar el resumen lo borra de forma persistente`() = runTest {
        manager.start(ZenDuration.ofMinutes(1))
        clock.advance(60_000L)
        manager.resolveExpired()

        val model = viewModel()
        model.finished.test {
            awaitItem()
            assertNotNull(awaitItem())

            model.consumeSummary()

            assertNull(awaitItem())
        }
        // Y no reaparece al reconstruir la pantalla.
        assertNull(preferences.pendingSummarySessionId.first())
    }

    @Test
    fun `empezar una sesion nueva descarta un resumen sin ver`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            preferences.setPendingSummary("vieja")

            viewModel().start(ZenDuration.ofMinutes(15))
            advanceUntilIdle()

            assertNull(preferences.pendingSummarySessionId.first())
        }

    @Test
    fun `abandonar deja el resumen pendiente marcado como abandonada`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            manager.start(ZenDuration.ofMinutes(30))
            clock.advance(5 * 60_000L)

            val model = viewModel()
            model.confirmFinish()
            advanceUntilIdle()

            val summary = sessions.find(preferences.pendingSummarySessionId.first()!!)
            assertEquals(SessionOutcome.ABANDONED, summary!!.outcome)
            assertEquals(5 * 60_000L, summary.actualDurationMillis)
        }
}
