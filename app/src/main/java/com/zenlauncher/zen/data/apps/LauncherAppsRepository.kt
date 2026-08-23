package com.zenlauncher.zen.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.Log
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * `LauncherApps` es la via correcta para un launcher: devuelve solo actividades
 * lanzables y **no necesita el permiso QUERY_ALL_PACKAGES**, basta con el bloque
 * `<queries>` del manifiesto.
 */
class LauncherAppsRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : InstalledAppsRepository {

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val user: UserHandle = Process.myUserHandle()
    private val ownPackage = appContext.packageName

    override fun observeInstalledApps(): Flow<List<InstalledApp>> = callbackFlow {
        // El callback solo emite una senal; la lectura se hace despues, fuera del hilo
        // principal, porque getActivityList cruza IPC y toca disco.
        val callback = object : LauncherApps.Callback() {
            private fun signal() {
                trySend(Unit)
            }

            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = signal()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = signal()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = signal()
            override fun onPackagesAvailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) = signal()

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) = signal()
        }

        trySend(Unit)
        // El Handler explicito es obligatorio: la sobrecarga sin el construye un
        // Handler sobre el hilo llamante, y este flujo se produce en Dispatchers.IO,
        // que no tiene Looper. Sin esto la app revienta al arrancar.
        launcherApps?.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps?.unregisterCallback(callback) }
    }
        // Varias altas o bajas seguidas (una actualizacion del sistema) no deben
        // provocar una lectura por cada evento.
        .conflate()
        .map { readApps() }
        .flowOn(io)

    override suspend fun launchableApps(): List<InstalledApp> = withContext(io) { readApps() }

    override fun launch(app: InstalledApp): Boolean = try {
        launcherApps?.startMainActivity(
            ComponentName.unflattenFromString(app.componentName),
            user,
            null,
            null,
        )
        true
    } catch (error: Exception) {
        // La aplicacion pudo desinstalarse entre el pintado y el toque; el launcher no
        // debe caerse por eso.
        Log.w(TAG, "No se pudo abrir ${app.packageName}", error)
        false
    }

    override fun launchPackage(packageName: String): Boolean {
        // getActivityList acotado al paquete: evita recorrer todas las aplicaciones solo
        // para encontrar el componente principal de una.
        val activity = launcherApps?.getActivityList(packageName, user)?.firstOrNull()
            ?: return false
        return try {
            launcherApps.startMainActivity(activity.componentName, user, null, null)
            true
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo abrir $packageName", error)
            false
        }
    }

    private fun readApps(): List<InstalledApp> {
        val activities = launcherApps?.getActivityList(null, user).orEmpty()
        return activities
            .asSequence()
            .filter { it.applicationInfo.packageName != ownPackage }
            .map { info ->
                InstalledApp(
                    packageName = info.applicationInfo.packageName,
                    label = info.label?.toString().orEmpty()
                        .ifBlank { info.applicationInfo.packageName },
                    componentName = info.componentName.flattenToString(),
                )
            }
            // Puede haber varias actividades lanzables por paquete; en un launcher de
            // texto una entrada por aplicacion es lo esperable.
            .distinctBy { it.packageName }
            .sortedBy { it.sortKey }
            .toList()
    }

    private companion object {
        const val TAG = "LauncherAppsRepository"
    }
}
