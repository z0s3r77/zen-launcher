package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.news.NewsHeadline
import com.zenlauncher.zen.domain.news.NewsPoint
import com.zenlauncher.zen.fakes.FakeNewsRepository
import com.zenlauncher.zen.fakes.FakePreferencesRepository
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.presentation.news.NewsUiState
import com.zenlauncher.zen.presentation.news.NewsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
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
class NewsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** La misma hora que marca [FakeZenClock]: una portada no puede nacer de otro dia. */
    private val AHORA = 1_700_000_000_000L

    private val UN_DIA = 24 * 60 * 60_000L

    private fun portada(fetchedAtMillis: Long = AHORA, titular: String = "Titular") = NewsEdition(
        headline = NewsHeadline(titular, "La bajada"),
        points = listOf(
            NewsPoint(
                index = "01",
                title = "Un punto",
                summary = "Su resumen",
                url = "https://noticiasdoxa.es/cluster/1/",
            ),
        ),
        fetchedAtMillis = fetchedAtMillis,
    )

    /**
     * El estado despues de hacer algo, con un colector vivo mientras tanto.
     *
     * `stateIn(WhileSubscribed)` no calcula nada sin nadie suscrito: leer
     * `state.value` a pelo devuelve el valor inicial y el test pasa o falla por
     * casualidad. Y `expectMostRecentItem` en vez de `awaitItem` porque una descarga
     * emite varias veces —empieza, escribe, termina— y lo que se comprueba es el final.
     */
    private suspend fun TestScope.stateAfter(
        viewModel: NewsViewModel,
        action: () -> Unit,
    ): NewsUiState {
        var state = NewsUiState()
        viewModel.state.test {
            action()
            advanceUntilIdle()
            state = expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }
        return state
    }

    private fun viewModel(
        news: FakeNewsRepository = FakeNewsRepository(),
        preferences: FakePreferencesRepository = FakePreferencesRepository(),
        clock: FakeZenClock = FakeZenClock(),
    ) = NewsViewModel(news = news, preferences = preferences, clock = clock)

    @Test
    fun `sin nada guardado se baja la portada y se guarda`() = runTest {
        val news = FakeNewsRepository(edition = portada())
        val preferences = FakePreferencesRepository()
        val viewModel = viewModel(news = news, preferences = preferences)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(1, news.frontPageCalls)
        assertNotNull(preferences.lastNews.first())
    }

    /**
     * La regla entera de esta funcion: **una descarga al dia**. Entrar diez veces en la
     * pantalla la misma manana no puede abrir ni una conexion.
     */
    @Test
    fun `con la portada de hoy guardada no se sale a la red`() = runTest {
        val news = FakeNewsRepository(edition = portada())
        val preferences = FakePreferencesRepository()
        preferences.setLastNews(portada(fetchedAtMillis = AHORA))
        val viewModel = viewModel(news = news, preferences = preferences)

        viewModel.load()
        viewModel.load()
        viewModel.load()
        advanceUntilIdle()

        assertEquals(0, news.frontPageCalls)
    }

    @Test
    fun `con la portada de ayer guardada se baja la de hoy`() = runTest {
        val news = FakeNewsRepository(edition = portada(titular = "La de hoy"))
        val preferences = FakePreferencesRepository()
        preferences.setLastNews(portada(fetchedAtMillis = AHORA - UN_DIA, titular = "La de ayer"))
        val viewModel = viewModel(news = news, preferences = preferences)

        val state = stateAfter(viewModel) { viewModel.load() }

        assertEquals(1, news.frontPageCalls)
        assertEquals("La de hoy", state.edition?.headline?.title)
    }

    /**
     * Sin cobertura, lo guardado **no se borra**: leer la portada de ayer sabiendo que
     * es de ayer es mejor que una pantalla en blanco. Y la pantalla tiene que poder
     * decir las dos cosas: que es vieja y que hoy no se pudo bajar.
     */
    @Test
    fun `un fallo no borra la portada anterior y se marca`() = runTest {
        val news = FakeNewsRepository(edition = null)
        val preferences = FakePreferencesRepository()
        preferences.setLastNews(portada(fetchedAtMillis = AHORA - UN_DIA, titular = "La de ayer"))
        val viewModel = viewModel(news = news, preferences = preferences)

        val state = stateAfter(viewModel) { viewModel.load() }

        assertEquals("La de ayer", state.edition?.headline?.title)
        assertFalse(state.fromToday)
        assertTrue(state.failed)
        assertFalse(state.downloading)
    }

    @Test
    fun `sin nada guardado y sin red la pantalla se queda sin portada`() = runTest {
        val viewModel = viewModel(news = FakeNewsRepository(edition = null))

        val state = stateAfter(viewModel) { viewModel.load() }

        assertNull(state.edition)
        assertTrue(state.failed)
    }

    /** ACTUALIZAR lo pulsa una persona: ahi si se baja aunque la de hoy ya este. */
    @Test
    fun `actualizar a mano baja aunque la portada sea de hoy`() = runTest {
        val news = FakeNewsRepository(edition = portada(titular = "Recien bajada"))
        val preferences = FakePreferencesRepository()
        preferences.setLastNews(portada(fetchedAtMillis = AHORA))
        val viewModel = viewModel(news = news, preferences = preferences)

        val state = stateAfter(viewModel) { viewModel.refreshNow() }

        assertEquals(1, news.frontPageCalls)
        assertEquals("Recien bajada", state.edition?.headline?.title)
    }

    @Test
    fun `la portada bajada hoy se marca como de hoy`() = runTest {
        val viewModel = viewModel(news = FakeNewsRepository(edition = portada()))

        val state = stateAfter(viewModel) { viewModel.load() }

        assertTrue(state.fromToday)
    }
}
