package com.zenlauncher.zen.presentation.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.domain.apps.AppRestrictionManager
import com.zenlauncher.zen.domain.apps.EnforcementLevel
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RestrictedAppRow(
    val app: InstalledApp,
    val restricted: Boolean,
)

data class RestrictedAppsUiState(
    val query: String = "",
    val rows: List<RestrictedAppRow> = emptyList(),
    val restrictedCount: Int = 0,
    val totalCount: Int = 0,
    val enforcementLevel: EnforcementLevel = EnforcementLevel.VISIBILITY_ONLY,
    val loading: Boolean = true,
)

class RestrictedAppsViewModel(
    private val preferences: PreferencesRepository,
    installedApps: InstalledAppsRepository,
    private val restrictions: AppRestrictionManager,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<RestrictedAppsUiState> = combine(
        installedApps.observeInstalledApps(),
        restrictions.restrictedPackages,
        query,
    ) { apps, restricted, currentQuery ->
        val needle = currentQuery.trim().lowercase()
        val rows = apps
            .filter { needle.isEmpty() || it.label.lowercase().contains(needle) }
            // Las ya restringidas suben arriba: es la lista que el usuario revisa.
            .map { RestrictedAppRow(it, it.packageName in restricted) }
            .sortedWith(compareByDescending<RestrictedAppRow> { it.restricted }.thenBy { it.app.sortKey })
        RestrictedAppsUiState(
            query = currentQuery,
            rows = rows,
            restrictedCount = restricted.size,
            totalCount = apps.size,
            enforcementLevel = restrictions.enforcementLevel,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RestrictedAppsUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun toggle(row: RestrictedAppRow) {
        viewModelScope.launch {
            restrictions.setRestricted(row.app.packageName, !row.restricted)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
