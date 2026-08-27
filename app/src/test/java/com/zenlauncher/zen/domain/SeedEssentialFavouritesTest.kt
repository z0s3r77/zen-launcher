package com.zenlauncher.zen.domain

import com.zenlauncher.zen.data.apps.LocalAppRestrictionManager
import com.zenlauncher.zen.domain.apps.EssentialApps
import com.zenlauncher.zen.domain.apps.SeedEssentialFavourites
import com.zenlauncher.zen.fakes.FakeInstalledAppsRepository
import com.zenlauncher.zen.fakes.FakePreferencesRepository
import com.zenlauncher.zen.fakes.installedApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedEssentialFavouritesTest {

    private val installed = listOf(
        installedApp("com.google.android.googlequicksearchbox", "Google"),
        installedApp("com.whatsapp", "WhatsApp"),
        installedApp("com.google.android.dialer", "Teléfono"),
        installedApp("com.android.settings", "Ajustes"),
        installedApp("com.instagram.android", "Instagram"),
    )

    private fun seeder(preferences: FakePreferencesRepository) = SeedEssentialFavourites(
        preferences = preferences,
        installedApps = FakeInstalledAppsRepository(installed),
        restrictions = LocalAppRestrictionManager(preferences),
    )

    @Test
    fun `una instalacion nueva arranca con las esenciales escritas en preferencias`() = runTest {
        val preferences = FakePreferencesRepository(initialSeeded = false)

        seeder(preferences)()

        assertEquals(
            listOf(
                "com.google.android.googlequicksearchbox",
                "com.whatsapp",
                "com.google.android.dialer",
                "com.android.settings",
            ),
            preferences.favouritePackages.first(),
        )
        assertTrue(preferences.favouritesSeeded.first())
    }

    @Test
    fun `lo que el usuario ya habia elegido se conserva y va primero`() = runTest {
        // Regresion: quien venia de una version anterior tenia favoritos guardados y las
        // esenciales no aparecian; sustituirlos habria sido peor que no sembrar.
        val preferences = FakePreferencesRepository(initialSeeded = false)
        preferences.setFavourites(listOf("com.instagram.android"))

        seeder(preferences)()

        val seeded = preferences.favouritePackages.first()
        assertEquals("com.instagram.android", seeded.first())
        assertTrue("com.whatsapp" in seeded)
    }

    @Test
    fun `un paquete que ya estaba elegido no se duplica`() = runTest {
        val preferences = FakePreferencesRepository(initialSeeded = false)
        preferences.setFavourites(listOf("com.whatsapp"))

        seeder(preferences)()

        val seeded = preferences.favouritePackages.first()
        assertEquals(seeded.distinct(), seeded)
        assertEquals("com.whatsapp", seeded.first())
    }

    @Test
    fun `sembrar no recorta lo que el usuario ya tenia elegido`() = runTest {
        // Aqui se comprobaba que nunca se pasara de ocho. Ese tope se quito al hacer que
        // la pantalla de inicio se desplace, y lo que hay que proteger es lo contrario:
        // sembrar no puede tirar nada de lo que ya estaba escrito.
        val preferences = FakePreferencesRepository(initialSeeded = false)
        val relleno = List(8) { "com.relleno.$it" }
        preferences.setFavourites(relleno)

        seeder(preferences)()

        val seeded = preferences.favouritePackages.first()
        assertEquals(relleno, seeded.take(relleno.size))
        assertTrue("com.whatsapp" in seeded)
        assertEquals(seeded.distinct(), seeded)
    }

    @Test
    fun `no se siembra una aplicacion restringida`() = runTest {
        val preferences = FakePreferencesRepository(
            initialRestricted = setOf("com.whatsapp"),
            initialSeeded = false,
        )

        seeder(preferences)()

        assertTrue("com.whatsapp" !in preferences.favouritePackages.first())
    }

    @Test
    fun `sembrar ocurre una sola vez, aunque el usuario lo vacie despues`() = runTest {
        // Una lista vacia puede ser una decision: volver a sembrar seria pelearse con
        // el usuario en cada arranque.
        val preferences = FakePreferencesRepository(initialSeeded = false)
        seeder(preferences)()

        preferences.setFavourites(emptyList())
        seeder(preferences)()

        assertEquals(emptyList<String>(), preferences.favouritePackages.first())
    }
}
