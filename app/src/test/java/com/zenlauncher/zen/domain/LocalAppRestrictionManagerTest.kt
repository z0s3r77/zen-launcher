package com.zenlauncher.zen.domain

import com.zenlauncher.zen.data.apps.LocalAppRestrictionManager
import com.zenlauncher.zen.domain.apps.EnforcementLevel
import com.zenlauncher.zen.fakes.FakePreferencesRepository
import com.zenlauncher.zen.fakes.installedApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAppRestrictionManagerTest {

    private val apps = listOf(
        installedApp("com.instagram.android", "Instagram"),
        installedApp("com.android.chrome", "Chrome"),
        installedApp("com.example.notes", "Notas"),
    )

    @Test
    fun `visibleApps quita exactamente las restringidas`() {
        val manager = LocalAppRestrictionManager(FakePreferencesRepository())

        val visible = manager.visibleApps(apps, setOf("com.instagram.android"))

        assertEquals(
            listOf("com.android.chrome", "com.example.notes"),
            visible.map { it.packageName },
        )
    }

    @Test
    fun `sin restricciones se ven todas`() {
        val manager = LocalAppRestrictionManager(FakePreferencesRepository())

        assertEquals(apps, manager.visibleApps(apps, emptySet()))
    }

    @Test
    fun `un paquete restringido que ya no esta instalado no molesta`() {
        val manager = LocalAppRestrictionManager(FakePreferencesRepository())

        val visible = manager.visibleApps(apps, setOf("com.desinstalada"))

        assertEquals(apps, visible)
    }

    @Test
    fun `la seleccion se propaga por el flujo`() = runTest {
        val preferences = FakePreferencesRepository()
        val manager = LocalAppRestrictionManager(preferences)

        manager.setRestricted("com.android.chrome", true)

        assertEquals(setOf("com.android.chrome"), manager.restrictedPackages.first())
    }

    @Test
    fun `en v0_1 el nivel de refuerzo es solo visibilidad`() = runTest {
        val manager = LocalAppRestrictionManager(FakePreferencesRepository())

        assertEquals(EnforcementLevel.VISIBILITY_ONLY, manager.enforcementLevel)
        // enforce/release existen para v0.2 y hoy no hacen nada: no deben fallar.
        manager.release()
        assertTrue(true)
    }
}
