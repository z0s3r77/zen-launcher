package com.zenlauncher.zen.domain.repository

import com.zenlauncher.zen.domain.model.InstalledApp
import kotlinx.coroutines.flow.Flow

/**
 * Aplicaciones lanzables del dispositivo.
 *
 * Se resuelve con `LauncherApps`, que no necesita el permiso QUERY_ALL_PACKAGES:
 * basta con declarar `<queries>` para el intent MAIN/LAUNCHER en el manifiesto.
 */
interface InstalledAppsRepository {

    /** Se reemite al instalar, desinstalar o renombrar una aplicacion. */
    fun observeInstalledApps(): Flow<List<InstalledApp>>

    suspend fun launchableApps(): List<InstalledApp>

    /** @return false si la aplicacion ya no existe o el sistema rechaza el lanzamiento. */
    fun launch(app: InstalledApp): Boolean

    /**
     * Abre una aplicacion de la que solo se conoce el paquete, no el componente: es el
     * caso del reproductor que publica la sesion de medios.
     *
     * @return false si el paquete no tiene ninguna actividad lanzable.
     */
    fun launchPackage(packageName: String): Boolean
}
