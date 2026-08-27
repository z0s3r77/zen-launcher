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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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

    /**
     * **El hallazgo central de la auditoria de rendimiento.**
     *
     * `ZenActivity` colecta esto en **todas** las pantallas, tambien con la home quieta,
     * para decidir si la sesion sustituye a la pantalla entera. Antes colectaba
     * `state`, que empieza por un `tickerFlow` de un segundo y arrastra `battery.observe`:
     * el launcher despertaba el hilo principal una vez por segundo para siempre, y
     * mantenia registrado un receptor de `ACTION_BATTERY_CHANGED` para un dato que la
     * pantalla de inicio ya ni pinta.
     */
    @Test
    fun `mirar si hay sesion no enciende el reloj ni el receptor de bateria`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val model = viewModel()

            model.active.test {
                assertNull(awaitItem())

                // Si aqui hubiera un ticker de un segundo, esto emitiria sesenta veces.
                advanceTimeBy(60 * 1_000L)
                runCurrent()
                expectNoEvents()
            }

            assertEquals(0, battery.observers)
        }

    @Test
    fun `la pantalla de sesion si enciende el reloj y la bateria`() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            // La contrapartida del test de arriba: dentro de la sesion el cronometro
            // tiene que correr y el porcentaje que se ensena tiene que venir de algun
            // sitio. Lo que se arreglo no es que existan, es donde viven.
            val model = viewModel()

            model.state.test {
                awaitItem()
                advanceTimeBy(1_000L)
                runCurrent()
                assertNotNull(awaitItem())
            }

            assertEquals(1, battery.observers)
        }
}
