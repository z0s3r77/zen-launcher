package com.zenlauncher.zen.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.domain.apps.AppRestrictionManager
import com.zenlauncher.zen.domain.battery.BatterySaverController
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.model.ZenThemeChoice
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** Cuantas aplicaciones hay puestas en el inicio. El dato, no la lista. */
    val homeAppsCount: Int = 0,
    val preferredDuration: ZenDuration = ZenDuration.Default,
    val batterySaverEnabled: Boolean = false,
    /** Ciudad del tiempo, o null si no hay ninguna elegida y por tanto no hay red. */
    val weatherPlaceName: String? = null,
    val themeChoice: ZenThemeChoice = ZenThemeChoice.Default,
    val loading: Boolean = true,
)

/**
 * Ajustes de Zen: lo que hace el aparato, no lo que se ve en el.
 *
 * Elegir las aplicaciones del inicio **ya no vive aqui**: era una lista con todas las
 * del telefono colgando del final de esta pantalla. Ahora es una fila que lleva a
 * [com.zenlauncher.zen.presentation.apps.HomeAppsViewModel] y de eso solo queda el
 * numero.
 */
class SettingsViewModel(
    private val preferences: PreferencesRepository,
    installedApps: InstalledAppsRepository,
    restrictions: AppRestrictionManager,
    private val batterySaver: BatterySaverController,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        installedApps.observeInstalledApps(),
        preferences.favouritePackages,
        restrictions.restrictedPackages,
        // Duracion, ciudad y tema viajan juntos: el `combine` tipado admite cinco
        // fuentes y aqui ya son siete.
        combine(
            preferences.preferredDuration,
            preferences.weatherPlace,
            preferences.themeChoice,
            ::Triple,
        ),
        batterySaver.isEnabled,
    ) { apps, favourites, restricted, (duration, weatherPlace, theme), saverEnabled ->
        // Se cuentan solo las que de verdad saldrian: una favorita desinstalada o
        // restringida despues de elegirla sigue en la lista guardada, pero no ocupa
        // hueco en la reticula, y el contador tiene que decir lo que se ve.
        val selectable = apps.filterNot { it.packageName in restricted }
        SettingsUiState(
            homeAppsCount = favourites.count { pkg -> selectable.any { it.packageName == pkg } },
            preferredDuration = duration,
            batterySaverEnabled = saverEnabled,
            weatherPlaceName = weatherPlace?.name,
            themeChoice = theme,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    fun setPreferredDuration(duration: ZenDuration) {
        viewModelScope.launch { preferences.setPreferredDuration(duration) }
    }

    /**
     * El tema al que se quiere ir, no "el siguiente".
     *
     * Quien decide cual toca es la pantalla, que ya tiene el actual en su estado. Si lo
     * calculara aqui habria que leer `state.value`, y esa `StateFlow` es
     * `WhileSubscribed`: fuera de la pantalla devuelve el valor inicial, asi que el
     * primer cambio de tema podria partir del tema equivocado.
     */
    fun setThemeChoice(choice: ZenThemeChoice) {
        viewModelScope.launch { preferences.setThemeChoice(choice) }
    }

    fun requestBatterySaver(): BatterySaverController.RequestResult = batterySaver.requestEnable()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
