package com.zenlauncher.zen.domain.apps

import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.InstalledApp
import kotlinx.coroutines.flow.Flow

/**
 * Frontera hacia v0.2.
 *
 * En v0.1 la restriccion es **informativa y de visibilidad**: las aplicaciones
 * marcadas desaparecen del launcher y se listan como bloqueadas durante la sesion,
 * pero nada impide abrirlas por otras vias. Android no ofrece a una aplicacion normal
 * ninguna forma de impedir el lanzamiento de otra.
 *
 * [enforce] y [release] existen ya y no hacen nada. Cuando exista una implementacion
 * con Device Owner (DevicePolicyManager.setPackagesSuspended / Lock Task), solo habra
 * que sustituir la implementacion registrada en el contenedor: ni el dominio ni la UI
 * cambian.
 */
interface AppRestrictionManager {

    val restrictedPackages: Flow<Set<String>>

    suspend fun setRestricted(packageName: String, restricted: Boolean)

    /** Aplicaciones visibles en el launcher: quita las restringidas. */
    fun visibleApps(all: List<InstalledApp>, restricted: Set<String>): List<InstalledApp>

    /** Nivel de refuerzo que esta implementacion puede ofrecer de verdad. */
    val enforcementLevel: EnforcementLevel

    /** No-op en v0.1. Se invoca al empezar la sesion. */
    suspend fun enforce(session: ActiveSession)

    /** No-op en v0.1. Se invoca al terminarla, pase lo que pase. */
    suspend fun release()
}

enum class EnforcementLevel {
    /** Solo se ocultan del launcher. Es lo unico posible sin privilegios. */
    VISIBILITY_ONLY,

    /** Reservado a v0.2 con Device Owner. */
    SYSTEM_ENFORCED,
}
