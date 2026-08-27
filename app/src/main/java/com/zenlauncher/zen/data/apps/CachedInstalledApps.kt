package com.zenlauncher.zen.data.apps

import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import com.zenlauncher.zen.system.LauncherMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

/**
 * Una sola lectura de la lista de aplicaciones para toda la aplicacion.
 *
 * Sin esto, **cada pantalla que observa las aplicaciones abre su propia consulta**: la
 * home, el cajon, elegir favoritos, restringidas, notificaciones y Ajustes. Son seis
 * `LauncherApps.getActivityList` completos —IPC mas disco, con el rotulo de cada
 * aplicacion cruzando el proceso— y seis callbacks registrados a la vez. Aqui se
 * comparte una sola fuente entre todos.
 *
 * Y sobre todo: **la lista cacheada se emite en el primer fotograma**. La pantalla de
 * inicio dibujaba su reticula vacia y la rellenaba cuando volvia el IPC, asi que volver
 * a Zen desde cualquier aplicacion ensenaba un hueco durante un instante. Al volver
 * dentro de la ventana de cache, la reticula ya esta puesta antes de pintar; la lectura
 * de verdad llega detras y corrige si algo cambio.
 *
 * La cache se suelta sola cuando el sistema va escaso de memoria (ver [LauncherMemory]):
 * es reconstruible entera con una llamada, asi que es lo primero que sobra.
 */
class CachedInstalledApps(
    private val delegate: InstalledAppsRepository,
    scope: CoroutineScope,
) : InstalledAppsRepository by delegate, LauncherMemory.Releasable {

    private val cached = MutableStateFlow<List<InstalledApp>?>(null)

    /**
     * `WhileSubscribed` y no `Eagerly`: al salir de Zen, el callback de `LauncherApps`
     * se da de baja y el launcher deja de recibir nada estando en segundo plano. La
     * espera cubre las transiciones entre pantallas, donde un ViewModel se va justo
     * antes de que llegue el siguiente.
     *
     * **`replay = 0` y no 1, y esto no se cambia.** Con replay uno, `shareIn` conserva la
     * ultima lista dentro de su propio buffer, y esa copia no la puede soltar [release]:
     * el aviso de memoria dejaria de liberar nada y ademas un colector nuevo recibiria la
     * lista vieja justo despues de haberla soltado, que es lo contrario de lo que se
     * pidio. Quien llega tarde no se queda sin lista porque el prefijo cacheado de
     * [observeInstalledApps] se la da antes de suscribirse. Fijado en
     * `CachedInstalledAppsTest.soltar la cache la vacia`.
     */
    private val upstream: Flow<List<InstalledApp>> = delegate.observeInstalledApps()
        .onEach { cached.value = it }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 0)

    override fun observeInstalledApps(): Flow<List<InstalledApp>> = flow {
        cached.value?.let { emit(it) }
        emitAll(upstream)
    }
        // La cache y la primera lectura de verdad suelen ser la misma lista: sin esto,
        // toda la interfaz se recomponia dos veces en cada arranque para nada.
        .distinctUntilChanged()

    override fun release() {
        cached.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 10_000L
    }
}
