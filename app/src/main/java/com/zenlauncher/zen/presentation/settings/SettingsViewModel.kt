package com.zenlauncher.zen.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.domain.apps.AppRestrictionManager
import com.zenlauncher.zen.domain.battery.BatterySaverController
import com.zenlauncher.zen.domain.model.ZenDuration
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
        // Duracion y ciudad viajan juntas: el `combine` tipado admite cinco fuentes y
        // aqui ya son seis.
        combine(preferences.preferredDuration, preferences.weatherPlace, ::Pair),
        batterySaver.isEnabled,
    ) { apps, favourites, restricted, (duration, weatherPlace), saverEnabled ->
        // Se cuentan solo las que de verdad saldrian: una favorita desinstalada o
        // restringida despues de elegirla sigue en la lista guardada, pero no ocupa
        // hueco en la reticula, y el contador tiene que decir lo que se ve.
        val selectable = apps.filterNot { it.packageName in restricted }
        SettingsUiState(
            homeAppsCount = favourites.count { pkg -> selectable.any { it.packageName == pkg } },
            preferredDuration = duration,
            batterySaverEnabled = saverEnabled,
            weatherPlaceName = weatherPlace?.name,
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

    fun requestBatterySaver(): BatterySaverController.RequestResult = batterySaver.requestEnable()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
