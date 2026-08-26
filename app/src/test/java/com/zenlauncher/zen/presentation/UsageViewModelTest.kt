package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.domain.usage.AppOpening
import com.zenlauncher.zen.domain.usage.AppUsage
import com.zenlauncher.zen.domain.usage.CompulsionKind
import com.zenlauncher.zen.domain.usage.DistractionPolicy
import com.zenlauncher.zen.domain.usage.UsageLevel
import com.zenlauncher.zen.domain.usage.UsageSnapshot
import com.zenlauncher.zen.fakes.FakeInstalledAppsRepository
import com.zenlauncher.zen.fakes.FakePreferencesRepository
import com.zenlauncher.zen.fakes.FakeUsageRepository
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.fakes.installedApp
import com.zenlauncher.zen.presentation.usage.UsageViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val ahora = 1_700_000_000_000L

    private val clock = FakeZenClock(wall = ahora)
    private val preferences = FakePreferencesRepository()
    private val apps = FakeInstalledAppsRepository(
        listOf(installedApp("com.instagram.android", "Instagram")),
    )

    private fun minutos(value: Int) = value * 60_000L

    private fun diaCargado() = UsageSnapshot(
        dayStartMillis = ahora - minutos(600),
        nowMillis = ahora,
        screenMillis = minutos(200),
        unlocks = 70,
        apps = listOf(AppUsage("com.instagram.android", openings = 30, foregroundMillis = minutos(120))),
    )

    private fun viewModel(usage: FakeUsageRepository) = UsageViewModel(
        usage = usage,
        installedApps = apps,
        preferences = preferences,
        clock = clock,
    )

    private fun arrastre() = listOf(
        AppOpening("com.instagram.android", atMillis = ahora - minutos(50), foregroundMillis = minutos(48)),
    )

    @Test
    fun `el dia medido se traduce a escalon y a filas con rotulo`() = runTest {
        val usage = FakeUsageRepository(snapshot = diaCargado())

        viewModel(usage).state.test {
            val state = awaitItem().takeIf { it.measured } ?: awaitItem()

            assertEquals(UsageLevel.ALTA, state.reading.level)
            assertTrue(state.hasAccess)
            // El rotulo sale de las aplicaciones instaladas, no del paquete.
            assertEquals("Instagram", state.apps.single().label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Sin acceso concedido no hay cero, hay ausencia de medida: un cero diria "hoy no
     * has usado el movil" y seria mentira.
     */
    @Test
    fun `sin acceso concedido no se mide nada`() = runTest {
        val usage = FakeUsageRepository(granted = false, snapshot = diaCargado())

        viewModel(usage).state.test {
            val state = awaitItem()

            assertFalse(state.hasAccess)
            assertFalse(state.measured)
            assertFalse(state.reading.worthShowing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una sentada larga levanta el aviso`() = runTest {
        val usage = FakeUsageRepository(snapshot = diaCargado(), openings = arrastre())

        val viewModel = viewModel(usage)
        viewModel.distraction.test {
            val aviso = awaitItem() ?: awaitItem()!!

            assertEquals(CompulsionKind.ARRASTRE, aviso!!.compulsion.kind)
            assertEquals("Instagram", aviso.appLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Regresion: la marca de "ya avisado" se escribia al descartar el aviso, asi que
     * salir de Zen sin tocar nada la dejaba sin escribir y el aviso reaparecia intacto
     * a la vuelta. Se escribe al ensenarlo.
     */
    @Test
    fun `el aviso queda anotado en cuanto se ensena, no al descartarlo`() = runTest {
        val usage = FakeUsageRepository(snapshot = diaCargado(), openings = arrastre())

        val viewModel = viewModel(usage)
        viewModel.distraction.test {
            awaitItem() ?: awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(ahora, preferences.lastDistractionAtMillis.first())
    }

    @Test
    fun `dentro de la espera el aviso no vuelve`() = runTest {
        preferences.setLastDistractionAt(ahora - minutos(10))
        val usage = FakeUsageRepository(snapshot = diaCargado(), openings = arrastre())

        val viewModel = viewModel(usage)
        viewModel.distraction.test {
            assertNull(awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `pasada la espera el aviso vuelve`() = runTest {
        preferences.setLastDistractionAt(ahora - minutos(DistractionPolicy.COOLDOWN_MINUTES + 1))
        val usage = FakeUsageRepository(snapshot = diaCargado(), openings = arrastre())

        val viewModel = viewModel(usage)
        viewModel.distraction.test {
            assertNotNull(awaitItem() ?: awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `descartar el aviso lo quita de la pantalla`() = runTest {
        val usage = FakeUsageRepository(snapshot = diaCargado(), openings = arrastre())

        val viewModel = viewModel(usage)
        viewModel.distraction.test {
            awaitItem() ?: awaitItem()
            viewModel.dismissDistraction()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Regresion de bateria: `refresh()` se llama en cada vuelta a la pantalla de inicio,
     * y releer los eventos del dia entero cada vez es recorrer miles de entradas para
     * ver cambiar un minuto. La ventana corta del detector si se consulta siempre.
     */
    @Test
    fun `volver a la home dos veces seguidas no relee el dia entero`() = runTest {
        val usage = FakeUsageRepository(snapshot = diaCargado())

        val viewModel = viewModel(usage)
        advanceUntilIdle()
        assertEquals(1, usage.fullReads)

        viewModel.refresh()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, usage.fullReads)
    }
}
