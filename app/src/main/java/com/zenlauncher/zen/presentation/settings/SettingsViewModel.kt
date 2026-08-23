package com.zenlauncher.zen.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.domain.apps.AppRestrictionManager
import com.zenlauncher.zen.domain.apps.EssentialApps
import com.zenlauncher.zen.domain.battery.BatterySaverController
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavouriteRow(
    val app: InstalledApp,
    val chosen: Boolean,
    val position: Int?,
)

data class SettingsUiState(
    val query: String = "",
    val rows: List<FavouriteRow> = emptyList(),
    val chosenCount: Int = 0,
    val preferredDuration: ZenDuration = ZenDuration.Default,
    val batterySaverEnabled: Boolean = false,
    val loading: Boolean = true,
) {
    val canChooseMore: Boolean get() = chosenCount < MAX_FAVOURITES

    companion object {
        /** El tope lo fija el dominio: Ajustes y el sembrado tienen que contar igual. */
        const val MAX_FAVOURITES = EssentialApps.MAX_HOME_APPS
    }
}

class SettingsViewModel(
    private val preferences: PreferencesRepository,
    installedApps: InstalledAppsRepository,
    restrictions: AppRestrictionManager,
    private val batterySaver: BatterySaverController,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<SettingsUiState> = combine(
        installedApps.observeInstalledApps(),
        preferences.favouritePackages,
        restrictions.restrictedPackages,
        preferences.preferredDuration,
        combine(query, batterySaver.isEnabled, ::Pair),
    ) { apps, favourites, restricted, duration, (currentQuery, saverEnabled) ->
        val needle = currentQuery.trim().lowercase()
        // Una app restringida no puede ser favorita: seria una contradiccion visible.
        val selectable = apps.filterNot { it.packageName in restricted }
        val rows = selectable
            .filter { needle.isEmpty() || it.label.lowercase().contains(needle) }
            .map { app ->
                val position = favourites.indexOf(app.packageName).takeIf { it >= 0 }
                FavouriteRow(app = app, chosen = position != null, position = position)
            }
            .sortedWith(
                compareBy<FavouriteRow> { it.position ?: Int.MAX_VALUE }
                    .thenBy { it.app.sortKey },
            )
        SettingsUiState(
            query = currentQuery,
            rows = rows,
            chosenCount = favourites.count { pkg -> selectable.any { it.packageName == pkg } },
            preferredDuration = duration,
            batterySaverEnabled = saverEnabled,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun toggleFavourite(row: FavouriteRow) {
        viewModelScope.launch {
            val current = state.value.rows
                .filter { it.chosen }
                .sortedBy { it.position ?: Int.MAX_VALUE }
                .map { it.app.packageName }

            val updated = if (row.chosen) {
                current - row.app.packageName
            } else {
                if (current.size >= SettingsUiState.MAX_FAVOURITES) return@launch
                current + row.app.packageName
            }
            preferences.setFavourites(updated)
        }
    }

    fun setPreferredDuration(duration: ZenDuration) {
        viewModelScope.launch { preferences.setPreferredDuration(duration) }
    }

    fun requestBatterySaver(): BatterySaverController.RequestResult = batterySaver.requestEnable()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
