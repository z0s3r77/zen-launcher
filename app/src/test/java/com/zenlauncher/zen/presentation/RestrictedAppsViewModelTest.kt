package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.data.apps.LocalAppRestrictionManager
import com.zenlauncher.zen.fakes.FakeInstalledAppsRepository
import com.zenlauncher.zen.fakes.FakePreferencesRepository
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.fakes.installedApp
import com.zenlauncher.zen.presentation.apps.RestrictedAppRow
import com.zenlauncher.zen.presentation.apps.RestrictedAppsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestrictedAppsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apps = listOf(
        installedApp("com.instagram.android", "Instagram"),
        installedApp("com.android.chrome", "Chrome"),
        installedApp("com.example.notes", "Notas"),
    )

    private fun viewModel(
        preferences: FakePreferencesRepository = FakePreferencesRepository(),
    ): RestrictedAppsViewModel {
        val restrictions = LocalAppRestrictionManager(preferences)
        return RestrictedAppsViewModel(
            preferences = preferences,
            installedApps = FakeInstalledAppsRepository(apps),
            restrictions = restrictions,
        )
    }

    @Test
    fun `lista todas las aplicaciones instaladas con su estado`() = runTest {
        viewModel(FakePreferencesRepository(setOf("com.instagram.android"))).state.test {
            awaitItem() // estado inicial vacio

            val loaded = awaitItem()
            assertEquals(3, loaded.totalCount)
            assertEquals(1, loaded.restrictedCount)
            assertFalse(loaded.loading)

            // Las restringidas se muestran primero: es la lista que se revisa.
            assertEquals("com.instagram.android", loaded.rows.first().app.packageName)
            assertTrue(loaded.rows.first().restricted)
        }
    }

    @Test
    fun `alternar una aplicacion cambia su estado y el recuento`() = runTest {
        val preferences = FakePreferencesRepository()
        val model = viewModel(preferences)

        model.state.test {
            awaitItem()
            val initial = awaitItem()
            assertEquals(0, initial.restrictedCount)

            val chrome = initial.rows.first { it.app.packageName == "com.android.chrome" }
            model.toggle(chrome)

            val afterBlock = awaitItem()
            assertEquals(1, afterBlock.restrictedCount)
            assertTrue(afterBlock.rows.first { it.app.packageName == "com.android.chrome" }.restricted)

            model.toggle(RestrictedAppRow(chrome.app, restricted = true))

            assertEquals(0, awaitItem().restrictedCount)
        }
    }

    @Test
    fun `el filtro busca por nombre sin distinguir mayusculas`() = runTest {
        val model = viewModel()

        model.state.test {
            awaitItem()
            awaitItem()

            model.onQueryChange("insta")

            val filtered = awaitItem()
            assertEquals(1, filtered.rows.size)
            assertEquals("Instagram", filtered.rows.single().app.label)
            // El total no cambia: el filtro es de presentacion, no de datos.
            assertEquals(3, filtered.totalCount)
        }
    }
}
