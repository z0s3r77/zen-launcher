package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.domain.weather.WeatherCondition
import com.zenlauncher.zen.domain.weather.WeatherPlace
import com.zenlauncher.zen.domain.weather.WeatherReading
import com.zenlauncher.zen.domain.weather.WeatherRefresh
import com.zenlauncher.zen.fakes.FakePreferencesRepository
import com.zenlauncher.zen.fakes.FakeWeatherRepository
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.presentation.weather.WeatherViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val madrid = WeatherPlace("Madrid, España", 40.4165, -3.7026)

    /** La misma hora que marca [FakeZenClock]: una lectura no puede nacer vieja. */
    private val AHORA = 1_700_000_000_000L

    private fun viewModel(
        weather: FakeWeatherRepository = FakeWeatherRepository(),
        preferences: FakePreferencesRepository = FakePreferencesRepository(),
        clock: FakeZenClock = FakeZenClock(),
    ) = WeatherViewModel(weather = weather, preferences = preferences, clock = clock)

    /**
     * El estado de fabrica. El tiempo es lo unico de Zen que usa internet y tiene que
     * estar apagado mientras nadie lo pida: sin ciudad, ni una conexion.
     */
    @Test
    fun `sin ciudad elegida no se sale a la red`() = runTest {
        val weather = FakeWeatherRepository()
        val viewModel = viewModel(weather = weather)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(0, weather.currentCalls)
        assertNull(viewModel.state.value.place)
    }

    @Test
    fun `elegir una ciudad la guarda y pide el tiempo al momento`() = runTest {
        val weather = FakeWeatherRepository(
            reading = WeatherReading(18, WeatherCondition.DESPEJADO, AHORA),
        )
        val preferences = FakePreferencesRepository()
        val viewModel = viewModel(weather = weather, preferences = preferences)

        viewModel.choose(madrid)
        advanceUntilIdle()

        // Al momento y no en la siguiente vuelta a la home: se acaba de pedir a
        // proposito y tiene que verse ya.
        assertEquals(1, weather.currentCalls)
        assertEquals(madrid, weather.askedFor.single())
        viewModel.state.test {
            awaitItem() // el valor inicial del stateIn: el combine aun no ha corrido
            val state = awaitItem()
            assertEquals(madrid, state.place)
            assertEquals(18, state.reading?.degrees)
        }
    }

    /**
     * A la pantalla de inicio se vuelve decenas de veces al dia. Sin la espera, cada
     * vuelta seria una peticion a la red.
     */
    @Test
    fun `volver dos veces a la home no pide el tiempo dos veces`() = runTest {
        val weather = FakeWeatherRepository(
            reading = WeatherReading(18, WeatherCondition.DESPEJADO, AHORA),
        )
        val preferences = FakePreferencesRepository()
        preferences.setWeatherPlace(madrid)
        val viewModel = viewModel(weather = weather, preferences = preferences)

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, weather.currentCalls)
    }

    @Test
    fun `pasada la espera se vuelve a pedir`() = runTest {
        val weather = FakeWeatherRepository(
            reading = WeatherReading(18, WeatherCondition.DESPEJADO, AHORA),
        )
        val preferences = FakePreferencesRepository()
        preferences.setWeatherPlace(madrid)
        val clock = FakeZenClock()
        val viewModel = viewModel(weather = weather, preferences = preferences, clock = clock)

        viewModel.refresh()
        advanceUntilIdle()
        clock.wall += WeatherRefresh.INTERVAL_MILLIS
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, weather.currentCalls)
    }

    /**
     * Regresion: contando solo los aciertos, un telefono sin cobertura salia a la red en
     * cada vuelta a la pantalla de inicio. La marca es del intento, no del acierto.
     */
    @Test
    fun `sin red el intento cuenta igual y no se pide en bucle`() = runTest {
        val weather = FakeWeatherRepository(reading = null)
        val preferences = FakePreferencesRepository()
        preferences.setWeatherPlace(madrid)
        val viewModel = viewModel(weather = weather, preferences = preferences)

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, weather.currentCalls)
    }

    /** Lo pide el usuario a mano: se pide, sin mirar el reloj. */
    @Test
    fun `actualizar a mano no espera a la media hora`() = runTest {
        val weather = FakeWeatherRepository(
            reading = WeatherReading(18, WeatherCondition.DESPEJADO, AHORA),
        )
        val preferences = FakePreferencesRepository()
        preferences.setWeatherPlace(madrid)
        val viewModel = viewModel(weather = weather, preferences = preferences)

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.refreshNow()
        advanceUntilIdle()

        assertEquals(2, weather.currentCalls)
    }

    /**
     * Un dato de hace ocho horas no se ensena: el usuario no tiene forma de saber que es
     * viejo mirando un numero, y "18°" de anoche es una mentira con forma de dato.
     */
    @Test
    fun `un dato demasiado viejo no llega a la pantalla`() = runTest {
        val clock = FakeZenClock()
        val preferences = FakePreferencesRepository()
        preferences.setWeatherPlace(madrid)
        preferences.setLastWeather(
            WeatherReading(
                degrees = 18,
                condition = WeatherCondition.DESPEJADO,
                observedAtMillis = clock.wallTimeMillis() - WeatherRefresh.STALE_MILLIS,
            ),
        )
        val viewModel = viewModel(preferences = preferences, clock = clock)

        viewModel.state.test {
            awaitItem() // el valor inicial del stateIn: el combine aun no ha corrido
            val state = awaitItem()
            assertNull(state.reading)
            // Pero se dice que lo hay y esta viejo, en lugar de callar: son dos cosas
            // distintas y la pantalla del tiempo las distingue.
            assertTrue(state.stale)
        }
    }

    @Test
    fun `quitar la ciudad apaga el tiempo por completo`() = runTest {
        val preferences = FakePreferencesRepository()
        preferences.setWeatherPlace(madrid)
        preferences.setLastWeather(WeatherReading(18, WeatherCondition.DESPEJADO, AHORA))
        val weather = FakeWeatherRepository()
        val viewModel = viewModel(weather = weather, preferences = preferences)

        viewModel.clearPlace()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(0, weather.currentCalls)
        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.place)
            // Ni el dato de la ciudad anterior sobrevive: saldria en la franja sin nada
            // que lo explique y ademas seria de un sitio donde el usuario ya no esta.
            assertNull(state.reading)
            assertFalse(state.stale)
        }
    }

    /**
     * Buscar es una accion y no algo que ocurra mientras se teclea: cada letra seria una
     * peticion a la red.
     */
    @Test
    fun `escribir no busca, buscar busca`() = runTest {
        val weather = FakeWeatherRepository(results = listOf(madrid))
        val viewModel = viewModel(weather = weather)

        viewModel.onQueryChange("Mad")
        viewModel.onQueryChange("Madri")
        viewModel.onQueryChange("Madrid")
        advanceUntilIdle()
        assertEquals(0, weather.searchCalls)

        viewModel.searchPlaces()
        advanceUntilIdle()

        assertEquals(1, weather.searchCalls)
        assertEquals(listOf(madrid), viewModel.search.value.results)
    }

    /**
     * "No hay resultados" y "no hay red" se dicen juntos porque desde aqui no se pueden
     * distinguir; lo que no se puede es dejar la pantalla en blanco.
     */
    @Test
    fun `una busqueda sin resultados lo dice`() = runTest {
        val viewModel = viewModel(weather = FakeWeatherRepository(results = emptyList()))

        viewModel.onQueryChange("Ciudad que no existe")
        viewModel.searchPlaces()
        advanceUntilIdle()

        assertTrue(viewModel.search.value.empty)
        assertFalse(viewModel.search.value.searching)
    }
}
