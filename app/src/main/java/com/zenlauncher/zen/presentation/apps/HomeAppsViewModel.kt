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
import kotlinx.coroutines.launch

/** Una aplicacion ya elegida, con el numero de hueco que ocupa en la reticula. */
data class ChosenAppRow(val app: InstalledApp, val position: Int)

/** Un resultado de la busqueda, con lo unico que hace falta saber: si ya esta puesta. */
data class CandidateAppRow(val app: InstalledApp, val chosen: Boolean)

data class HomeAppsUiState(
    val query: String = "",
    val chosen: List<ChosenAppRow> = emptyList(),
    val candidates: List<CandidateAppRow> = emptyList(),
    val loading: Boolean = true,
) {
    val chosenCount: Int get() = chosen.size

    /** Si hay busqueda en curso: sin ella no se ensena ninguna candidata. */
    val searching: Boolean get() = query.isNotBlank()
}

/**
 * Elegir que aplicaciones se ven en la pantalla de inicio.
 *
 * Vivia dentro de Ajustes como una lista con **todas** las aplicaciones del telefono,
 * doscientas filas debajo de los interruptores del sistema: para poner el telefono en
 * el inicio habia que reconocerlo entre todo lo instalado, y la pantalla de ajustes
 * dejaba de tener final. Aqui la pregunta es solo una, y se responde en dos partes:
 * arriba lo que ya esta puesto —con su numero de hueco, tocar para quitarlo—, abajo un
 * buscador que **no lista nada hasta que se escribe**. Elegir entre doscientas no es
 * elegir; escribir tres letras del nombre que ya se tiene en la cabeza, si.
 */
class HomeAppsViewModel(
    private val preferences: PreferencesRepository,
    installedApps: InstalledAppsRepository,
    restrictions: AppRestrictionManager,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<HomeAppsUiState> = combine(
        installedApps.observeInstalledApps(),
        preferences.favouritePackages,
        restrictions.restrictedPackages,
        query,
    ) { apps, favourites, restricted, currentQuery ->
        // Una app restringida no puede estar en el inicio: seria una contradiccion
        // visible. Tampoco se puede elegir una que ya no esta instalada.
        val selectable = apps.filterNot { it.packageName in restricted }
        val chosen = favourites
            .mapNotNull { pkg -> selectable.firstOrNull { it.packageName == pkg } }
            .mapIndexed { position, app -> ChosenAppRow(app = app, position = position) }

        val chosenPackages = chosen.mapTo(mutableSetOf()) { it.app.packageName }
        val candidates = if (currentQuery.isBlank()) {
            emptyList()
        } else {
            selectable
                .filter { it.matches(currentQuery) }
                .sortedBy { it.sortKey }
                .map { CandidateAppRow(app = it, chosen = it.packageName in chosenPackages) }
        }

        HomeAppsUiState(
            query = currentQuery,
            chosen = chosen,
            candidates = candidates,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeAppsUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Quita la aplicacion del inicio. Los huecos siguientes suben un numero. */
    fun remove(app: InstalledApp) {
        viewModelScope.launch {
            preferences.setFavourites(chosenPackages() - app.packageName)
        }
    }

    /**
     * Anade al final. Si ya esta puesta no hace nada: la fila que lo llama ya lo dice, y
     * aqui se comprueba otra vez porque el estado puede haber cambiado entre el toque y
     * la escritura.
     *
     * Ya no hay tope. Lo hubo —ocho— mientras la pantalla de inicio no se desplazaba:
     * la novena aplicacion existia pero no habia forma de llegar a ella. Desde que la
     * home se desplaza, cortar en ocho seria una limitacion sin nada detras.
     */
    fun add(app: InstalledApp) {
        viewModelScope.launch {
            val current = chosenPackages()
            if (app.packageName in current) return@launch
            preferences.setFavourites(current + app.packageName)
        }
    }

    private fun chosenPackages(): List<String> = state.value.chosen.map { it.app.packageName }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Coincide por nombre o por paquete, sin distinguir mayusculas. */
private fun InstalledApp.matches(query: String): Boolean {
    val needle = query.trim().lowercase()
    return label.lowercase().contains(needle) || packageName.lowercase().contains(needle)
}
