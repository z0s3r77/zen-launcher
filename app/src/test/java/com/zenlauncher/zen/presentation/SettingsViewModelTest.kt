package com.zenlauncher.zen.presentation

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.zenlauncher.zen.data.battery.SystemSettingsBatterySaverController
import com.zenlauncher.zen.domain.model.ZenThemeChoice
import com.zenlauncher.zen.fakes.FakeInstalledAppsRepository
import com.zenlauncher.zen.fakes.FakePreferencesRepository
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.fakes.RecordingRestrictionManager
import com.zenlauncher.zen.fakes.installedApp
import com.zenlauncher.zen.presentation.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric solo por `BatterySaverController`, que necesita un Context real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakePreferencesRepository()

    private fun viewModel() = SettingsViewModel(
        preferences = preferences,
        installedApps = FakeInstalledAppsRepository(listOf(installedApp("com.a"))),
        restrictions = RecordingRestrictionManager(preferences),
        batterySaver = SystemSettingsBatterySaverController(
            ApplicationProvider.getApplicationContext(),
        ),
    )

    @Test
    fun `el estado trae el tema guardado`() = runTest {
        preferences.setThemeChoice(ZenThemeChoice.SISTEMA)

        viewModel().state.test {
            // El primer valor es el inicial de la `StateFlow`, antes de que el `combine`
            // haya armado nada: hay que dejar correr las corrutinas y quedarse con el
            // ultimo, o se comprueba el estado de fabrica en lugar del guardado.
            advanceUntilIdle()

            assertEquals(ZenThemeChoice.SISTEMA, expectMostRecentItem().themeChoice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `elegir tema lo persiste`() = runTest {
        // Regresion: si el tema solo viviera en el estado del ViewModel, se perderia con
        // el proceso del launcher, que muere y revive constantemente.
        val viewModel = viewModel()

        viewModel.setThemeChoice(ZenThemeChoice.SISTEMA)
        advanceUntilIdle()

        assertEquals(ZenThemeChoice.SISTEMA, preferences.themeChoice.first())
    }

    @Test
    fun `sin elegir nada el tema es el de fabrica`() = runTest {
        assertEquals(ZenThemeChoice.NEGRO, preferences.themeChoice.first())
    }
}
