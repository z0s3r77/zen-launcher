package com.zenlauncher.zen.presentation.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.domain.apps.AppRestrictionManager
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppDrawerUiState(
    val query: String = "",
    val apps: List<InstalledApp> = emptyList(),
    val hiddenByRestriction: Int = 0,
    val loading: Boolean = true,
)

/**
 * Lista completa, en texto plano y sin iconos.
 *
 * Existe para el caso real de necesitar una aplicacion puntual sin convertir la
 * pantalla de inicio en un cajon: hay que buscarla a proposito.
 */
class AppDrawerViewModel(
    preferences: PreferencesRepository,
    private val installedApps: InstalledAppsRepository,
    restrictions: AppRestrictionManager,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<AppDrawerUiState> = combine(
        installedApps.observeInstalledApps(),
        restrictions.restrictedPackages,
        query,
    ) { apps, restricted, currentQuery ->
        val allowed = apps.filterNot { it.packageName in restricted }
        AppDrawerUiState(
            query = currentQuery,
            apps = allowed.filter { it.matches(currentQuery) },
            hiddenByRestriction = apps.size - allowed.size,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AppDrawerUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun launch(app: InstalledApp) {
        installedApps.launch(app)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Coincide por nombre o por paquete, sin distinguir mayusculas. */
private fun InstalledApp.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim().lowercase()
    return label.lowercase().contains(needle) || packageName.lowercase().contains(needle)
}
