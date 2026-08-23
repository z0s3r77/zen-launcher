package com.zenlauncher.zen.data.apps

import com.zenlauncher.zen.domain.apps.AppRestrictionManager
import com.zenlauncher.zen.domain.apps.EnforcementLevel
import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow

/**
 * Implementacion de v0.1: la restriccion solo afecta a lo que Zen muestra.
 *
 * [enforce] y [release] no hacen nada, y eso es correcto para esta fase: sin Device
 * Owner no existe ninguna API publica que impida abrir otra aplicacion. Se invocan
 * igualmente desde el gestor de sesiones para que el punto de extension este ya
 * ejercitado cuando llegue la implementacion privilegiada.
 */
class LocalAppRestrictionManager(
    private val preferences: PreferencesRepository,
) : AppRestrictionManager {

    override val restrictedPackages: Flow<Set<String>> = preferences.restrictedPackages

    override val enforcementLevel: EnforcementLevel = EnforcementLevel.VISIBILITY_ONLY

    override suspend fun setRestricted(packageName: String, restricted: Boolean) {
        preferences.setRestricted(packageName, restricted)
    }

    override fun visibleApps(
        all: List<InstalledApp>,
        restricted: Set<String>,
    ): List<InstalledApp> = all.filterNot { it.packageName in restricted }

    override suspend fun enforce(session: ActiveSession) = Unit

    override suspend fun release() = Unit
}
