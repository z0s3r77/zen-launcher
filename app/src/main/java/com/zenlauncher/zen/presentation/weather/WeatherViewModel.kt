package com.zenlauncher.zen.presentation.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import com.zenlauncher.zen.domain.weather.WeatherPlace
import com.zenlauncher.zen.domain.weather.WeatherReading
import com.zenlauncher.zen.domain.weather.WeatherRefresh
import com.zenlauncher.zen.domain.weather.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeatherUiState(
    val place: WeatherPlace? = null,
    /**
     * Lo que se ensena. Ya viene filtrado por edad: una lectura de hace ocho horas no
     * llega hasta aqui, porque el usuario no tiene forma de saber que es vieja mirando
     * un numero. Ver [WeatherRefresh.isStale].
     */
    val reading: WeatherReading? = null,
    /** Hay ciudad y hay dato guardado, pero es demasiado viejo para ensenarlo. */
    val stale: Boolean = false,
)

/** Estado del buscador de ciudades. Vive aparte porque solo existe en su pantalla. */
data class PlaceSearchState(
    val searching: Boolean = false,
    val results: List<WeatherPlace> = emptyList(),
    /** Se busco y no hubo nada: ni resultados ni red. Se dice, no se deja en blanco. */
    val empty: Boolean = false,
)

/**
 * El tiempo: la ciudad elegida, lo ultimo traido y cuando volver a pedirlo.
 *
 * **Vive en el ambito de la Activity**, como `UsageViewModel`, porque su dato se ensena
 * en la franja de la pantalla de inicio y tambien en su propia pantalla: con una
 * instancia por destino habria dos tuberias pidiendo lo mismo a la red.
 */
class WeatherViewModel(
    private val weather: WeatherRepository,
    private val preferences: PreferencesRepository,
    private val clock: ZenClock,
) : ViewModel() {

    /**
     * El texto del buscador es estado propio y no se deriva de lo que devuelve la
     * busqueda.
     *
     * Misma regresion que el buscador de Notas: un campo de texto que lee su valor de un
     * flujo asincrono pierde letras al escribir, porque entre la pulsacion y la vuelta
     * del resultado ya se han tecleado dos mas.
     */
    private val queryState = MutableStateFlow("")
    val query: StateFlow<String> = queryState.asStateFlow()

    private val searchState = MutableStateFlow(PlaceSearchState())
    val search: StateFlow<PlaceSearchState> = searchState.asStateFlow()

    val state: StateFlow<WeatherUiState> = combine(
        preferences.weatherPlace,
        preferences.lastWeather,
    ) { place, reading ->
        // Sin ciudad no hay nada: ni dato viejo que ensenar ni aviso que dar. Es el
        // estado de fabrica y la franja tiene que quedar exactamente como sin esto.
        if (place == null) return@combine WeatherUiState()
        val stale = reading != null && WeatherRefresh.isStale(reading, clock.wallTimeMillis())
        WeatherUiState(
            place = place,
            reading = reading.takeUnless { stale },
            stale = stale,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = WeatherUiState(),
    )

    /**
     * Se llama al volver a la pantalla de inicio, y solo pide de verdad si toca.
     *
     * Es el mismo trato que el uso del movil: no hay sondeo ni flujo que lata. Aqui
     * ademas hay red de por medio, asi que entre dos peticiones pasa media hora aunque
     * se vuelva a la home cincuenta veces (ver [WeatherRefresh]).
     */
    fun refresh() {
        viewModelScope.launch {
            val place = preferences.weatherPlace.first() ?: return@launch
            val lastAttempt = preferences.lastWeatherAttemptAtMillis.first()
            if (!WeatherRefresh.shouldRefresh(lastAttempt, clock.wallTimeMillis())) return@launch
            fetch(place)
        }
    }

    /** Pedir ahora, sin mirar el reloj: lo pidio el usuario a mano. */
    fun refreshNow() {
        viewModelScope.launch {
            val place = preferences.weatherPlace.first() ?: return@launch
            fetch(place)
        }
    }

    private suspend fun fetch(place: WeatherPlace) {
        // La marca del intento se escribe **antes** de saber si salio bien: contando
        // solo los aciertos, un telefono sin cobertura saldria a la red en cada vuelta a
        // la pantalla de inicio.
        preferences.setLastWeatherAttemptAt(clock.wallTimeMillis())
        val reading = weather.current(place) ?: return
        preferences.setLastWeather(reading)
    }

    fun onQueryChange(text: String) {
        queryState.value = text
        if (text.isBlank()) searchState.value = PlaceSearchState()
    }

    /**
     * Buscar es una accion, no algo que ocurra mientras se teclea.
     *
     * Cada letra escrita seria una peticion a la red; con un antirrebote seria una cada
     * pocas letras. Aqui se busca al pulsar, que es una peticion por busqueda y ademas
     * el unico momento en que se sabe que el usuario termino de escribir el nombre.
     */
    fun searchPlaces() {
        val text = queryState.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            searchState.value = PlaceSearchState(searching = true)
            val results = weather.search(text)
            searchState.value = PlaceSearchState(
                searching = false,
                results = results,
                empty = results.isEmpty(),
            )
        }
    }

    fun choose(place: WeatherPlace) {
        viewModelScope.launch {
            preferences.setWeatherPlace(place)
            queryState.value = ""
            searchState.value = PlaceSearchState()
            // Sin esto, la ciudad recien elegida se queda sin dato hasta la siguiente
            // vuelta a la home: se acaba de pedir a proposito y tiene que verse ya.
            fetch(place)
        }
    }

    /** Quitar la ciudad apaga el tiempo por completo, red incluida. */
    fun clearPlace() {
        viewModelScope.launch { preferences.setWeatherPlace(null) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
