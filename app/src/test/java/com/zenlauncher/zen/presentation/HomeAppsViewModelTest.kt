package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.data.apps.LocalAppRestrictionManager
import com.zenlauncher.zen.fakes.FakeInstalledAppsRepository
import com.zenlauncher.zen.fakes.FakePreferencesRepository
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.fakes.installedApp
import com.zenlauncher.zen.presentation.apps.HomeAppsUiState
import com.zenlauncher.zen.presentation.apps.HomeAppsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAppsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apps = listOf(
        installedApp("com.google.android.dialer", "Teléfono"),
        installedApp("com.instagram.android", "Instagram"),
        installedApp("com.example.notes", "Notas"),
    )

    private fun viewModel(
        preferences: FakePreferencesRepository = FakePreferencesRepository(),
    ) = HomeAppsViewModel(
        preferences = preferences,
        installedApps = FakeInstalledAppsRepository(apps),
        restrictions = LocalAppRestrictionManager(preferences),
    )

    @Test
    fun `sin busqueda no propone ninguna aplicacion`() = runTest {
        // El motivo de que esta pantalla exista: la lista con todas las aplicaciones
        // del telefono no es una eleccion, es un catalogo.
        viewModel().state.test {
            awaitItem() // estado inicial, todavia cargando
            val state = awaitItem()

            assertTrue(state.candidates.isEmpty())
            assertFalse(state.searching)
        }
    }

    @Test
    fun `al escribir aparecen solo las que coinciden`() = runTest {
        val model = viewModel()
        model.state.test {
            awaitItem()
            awaitItem()

            model.onQueryChange("tel")
            val state = awaitItem()

            assertEquals(listOf("Teléfono"), state.candidates.map { it.app.label })
            assertTrue(state.searching)
        }
    }

    @Test
    fun `anadir pone la aplicacion al final y numera los huecos por orden`() = runTest {
        val preferences = FakePreferencesRepository()
        val model = viewModel(preferences)
        model.state.test {
            awaitItem()
            awaitItem()

            model.onQueryChange("no")
            model.add(awaitItem().candidates.first { it.app.label == "Notas" }.app)
            awaitItem()

            model.onQueryChange("tel")
            model.add(awaitItem().candidates.first().app)
            val state = awaitItem()

            assertEquals(listOf("Notas", "Teléfono"), state.chosen.map { it.app.label })
            assertEquals(listOf(0, 1), state.chosen.map { it.position })
        }
    }

    @Test
    fun `quitar libera hueco y renumera lo que queda`() = runTest {
        val preferences = FakePreferencesRepository()
        preferences.setFavourites(listOf("com.example.notes", "com.google.android.dialer"))
        val model = viewModel(preferences)

        model.state.test {
            awaitItem()
            val full = awaitItem()
            model.remove(full.chosen.first().app)
            val state = awaitItem()

            assertEquals(listOf("Teléfono"), state.chosen.map { it.app.label })
            assertEquals(listOf(0), state.chosen.map { it.position })
        }
    }

    @Test
    fun `una aplicacion ya puesta se ve en la busqueda pero no se duplica`() = runTest {
        val preferences = FakePreferencesRepository()
        preferences.setFavourites(listOf("com.example.notes"))
        val model = viewModel(preferences)

        model.state.test {
            awaitItem()
            awaitItem()

            model.onQueryChange("notas")
            val state = awaitItem()

            val row = state.candidates.single()
            assertTrue(row.chosen)

            // Aunque la fila no sea pulsable en pantalla, el modelo tambien lo impide:
            // el estado puede cambiar entre el toque y la escritura.
            model.add(row.app)
            expectNoEvents()
        }
    }

    @Test
    fun `con ocho puestas todavia se puede anadir la novena`() = runTest {
        // Aqui se comprobaba lo contrario: con ocho, anadir no hacia nada. El tope se
        // quito al hacer que la pantalla de inicio se desplace, porque lo que lo
        // justificaba era que la novena no se pudiera alcanzar.
        val muchas = (1..8).map { installedApp("com.app$it", "App $it") }
        val preferences = FakePreferencesRepository()
        preferences.setFavourites(muchas.map { it.packageName })
        val model = HomeAppsViewModel(
            preferences = preferences,
            installedApps = FakeInstalledAppsRepository(muchas + apps),
            restrictions = LocalAppRestrictionManager(preferences),
        )

        model.state.test {
            awaitItem()
            assertEquals(8, awaitItem().chosenCount)

            model.onQueryChange("notas")
            model.add(awaitItem().candidates.single().app)

            val conNueve = awaitItem()
            assertEquals(9, conNueve.chosenCount)
            assertEquals("com.example.notes", conNueve.chosen.last().app.packageName)
        }
    }

    @Test
    fun `una aplicacion restringida no puede ponerse en el inicio`() = runTest {
        // Seria una contradiccion visible: restringida significa que Zen no la ensena.
        val preferences = FakePreferencesRepository(initialRestricted = setOf("com.instagram.android"))
        val model = viewModel(preferences)

        model.state.test {
            awaitItem()
            awaitItem()

            model.onQueryChange("insta")
            val state = awaitItem()

            assertTrue(state.candidates.isEmpty())
        }
    }
}
