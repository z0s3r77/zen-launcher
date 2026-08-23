package com.zenlauncher.zen.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.apps.AppRestrictionManager
import com.zenlauncher.zen.domain.apps.EssentialApps
import com.zenlauncher.zen.domain.apps.SeedEssentialFavourites
import com.zenlauncher.zen.domain.media.MediaTransport
import com.zenlauncher.zen.domain.media.NowPlaying
import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.notifications.AppNotification
import com.zenlauncher.zen.domain.notifications.NotificationBadges
import com.zenlauncher.zen.domain.notifications.NotificationsRepository
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import com.zenlauncher.zen.domain.session.ZenSessionManager
import com.zenlauncher.zen.presentation.util.ONE_MINUTE_MILLIS
import com.zenlauncher.zen.presentation.util.tickerFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val nowMillis: Long = 0L,
    val activeSession: ActiveSession? = null,
    /** Lo que se ve en la reticula: los favoritos del usuario o, si no eligio, las esenciales. */
    val homeApps: List<InstalledApp> = emptyList(),
    /** true cuando [homeApps] son las de por defecto y no una eleccion del usuario. */
    val usingEssentials: Boolean = false,
    val restrictedCount: Int = 0,
    val hasApps: Boolean = false,
    val mediaPlaying: Boolean = false,
    /** Titulo, artista y caratula; null sin acceso concedido o sin nada sonando. */
    val nowPlaying: NowPlaying? = null,
    /**
     * Avisos por paquete de las aplicaciones **que pueden llevar marca**: las que estan
     * en la reticula. Sin acceso concedido siempre esta vacio.
     */
    val notificationCounts: Map<String, Int> = emptyMap(),
    /**
     * Todos los avisos que la pantalla de Notificaciones va a listar, lleven marca o
     * no.
     *
     * Regresion: se calculaba sumando [notificationCounts], que solo cuenta
     * aplicaciones lanzables, mientras que la pantalla lista tambien las que no lo son
     * (una actualizacion del sistema, por ejemplo). La fila del menu decia "00" y
     * dentro habia tres avisos.
     */
    val notificationTotal: Int = 0,
) {
    val sessionActive: Boolean get() = activeSession != null

    /**
     * Si el mando del reproductor tiene algo que mandar.
     *
     * Regresion: la barra se pintaba siempre, asi que al entrar en Zen sin haber puesto
     * musica en todo el dia habia un reproductor "EN PAUSA" con tres botones que no
     * hacian nada. Ahora aparece solo cuando hay algo detras: sonando (se oye el audio)
     * o con una sesion de medios viva que publica su ficha, que es el caso de un
     * reproductor recien pausado al que se quiere volver.
     */
    val mediaVisible: Boolean get() = mediaPlaying || nowPlaying != null

}

class HomeViewModel(
    private val preferences: PreferencesRepository,
    private val installedApps: InstalledAppsRepository,
    private val restrictions: AppRestrictionManager,
    private val sessionManager: ZenSessionManager,
    private val media: MediaTransport,
    private val notifications: NotificationsRepository,
    private val seedFavourites: SeedEssentialFavourites,
    private val clock: ZenClock,
) : ViewModel() {

    init {
        // Una sola vez por instalacion: deja las esenciales escritas en preferencias
        // para que Ajustes y la pantalla de inicio cuenten lo mismo.
        viewModelScope.launch { seedFavourites() }
    }

    /**
     * El estado del reproductor no se puede observar sin acceso al oyente de
     * notificaciones, asi que se guarda aqui: se lee al entrar y al volver a primer
     * plano, y se corrige tras cada orden. Un sondeo periodico despertaria la pantalla
     * cada pocos segundos para dibujar un triangulo.
     */
    private val mediaPlaying = MutableStateFlow(media.isPlaying())

    /**
     * El reloj de la home late una vez por minuto, no una vez por segundo: los
     * segundos no se muestran, asi que despertar mas seria gasto puro de bateria.
     */
    val state: StateFlow<HomeUiState> = combine(
        tickerFlow(ONE_MINUTE_MILLIS, clock),
        preferences.activeSession,
        installedApps.observeInstalledApps(),
        combine(
            preferences.favouritePackages,
            restrictions.restrictedPackages,
            // Reproductor, conexiones y avisos viajan juntos: `combine` tipado admite
            // cinco fuentes y aqui ya son ocho.
            combine(
                mediaPlaying,
                media.observeNowPlaying(),
                notifications.observeNotifications(),
                ::DeviceState,
            ),
            ::Triple,
        ),
    ) { now, active, apps, (favourites, restricted, deviceState) ->
        val (audioPlaying, nowPlaying, posted) = deviceState
        // Una restringida no puede aparecer en el inicio por ninguna via: ni elegida a
        // mano, ni colandose como esencial.
        val visible = restrictions.visibleApps(apps, restricted)
        val byPackage = visible.associateBy { it.packageName }
        // Se respeta el orden elegido por el usuario y se descartan en silencio las que
        // ya no estan instaladas.
        val chosen = favourites.mapNotNull { byPackage[it] }
        val essentials = if (chosen.isEmpty()) EssentialApps.resolve(visible) else emptyList()
        // La cuenta se hace una vez y se reparte: la marca solo puede colgar de una
        // aplicacion de la reticula, pero el total del menu tiene que decir lo mismo
        // que va a listar la pantalla de Notificaciones.
        val countable = NotificationBadges.countByPackage(
            posted.filterNot { it.packageName in restricted },
        )

        HomeUiState(
            nowMillis = now,
            activeSession = active,
            homeApps = chosen.ifEmpty { essentials },
            usingEssentials = chosen.isEmpty() && essentials.isNotEmpty(),
            restrictedCount = restricted.size,
            hasApps = apps.isNotEmpty(),
            // Con metadatos, la sesion sabe mas que el nivel de audio: un reproductor
            // silenciado sigue estando en reproduccion, y al reves.
            mediaPlaying = nowPlaying?.playing ?: audioPlaying,
            nowPlaying = nowPlaying,
            // Una restringida desaparece de Zen por completo, tambien de la cuenta.
            notificationCounts = countable.filterKeys { it in byPackage },
            notificationTotal = countable.values.sum(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        // El reloj se lee de forma sincrona para el primer fotograma: el flujo tarda en
        // emitir porque el combine espera a la lista de aplicaciones, y sin esto la hora
        // —lo mas grande de la pantalla— aparecia un instante despues.
        initialValue = HomeUiState(
            nowMillis = clock.wallTimeMillis(),
            mediaPlaying = mediaPlaying.value,
        ),
    )

    /**
     * Se llama al volver a primer plano. Si el tiempo vencio mientras la app no estaba
     * en pantalla (o la alarma no llego a dispararse), la sesion se cierra aqui.
     */
    fun onResumed() {
        viewModelScope.launch { sessionManager.resolveExpired() }
        // Se pudo pausar o arrancar la musica desde otra aplicacion mientras tanto.
        mediaPlaying.value = media.isPlaying()
    }

    fun launch(app: InstalledApp) {
        installedApps.launch(app)
    }

    /**
     * Abre el reproductor que publica la sesion. Se lee del estado y no se guarda aparte
     * para que no puedan discrepar: lo que se abre es exactamente lo que se esta viendo.
     */
    fun openNowPlaying() {
        val packageName = state.value.nowPlaying?.packageName ?: return
        installedApps.launchPackage(packageName)
    }

    /**
     * Se pinta el cambio al momento y se comprueba despues: el reproductor tarda en
     * responder a la tecla, y esperar a la verdad dejaria el boton sin reaccion durante
     * medio segundo. Si la orden no llego a nadie, no se cambia nada.
     */
    fun togglePlayback() {
        if (!media.playPause()) return
        mediaPlaying.value = !mediaPlaying.value
        syncPlaybackSoon()
    }

    fun nextTrack() {
        if (media.next()) syncPlaybackSoon()
    }

    fun previousTrack() {
        if (media.previous()) syncPlaybackSoon()
    }

    private fun syncPlaybackSoon() {
        viewModelScope.launch {
            delay(PLAYBACK_SYNC_DELAY_MILLIS)
            mediaPlaying.value = media.isPlaying()
        }
    }

    /**
     * Estado del dispositivo que no cabe en el `combine` de arriba. Es una clase y no
     * otra `Triple` anidada porque a la tercera el destructuring deja de leerse.
     */
    private data class DeviceState(
        val audioPlaying: Boolean,
        val nowPlaying: NowPlaying?,
        val notifications: List<AppNotification>,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Margen para que el reproductor procese la tecla antes de preguntarle. */
        const val PLAYBACK_SYNC_DELAY_MILLIS = 400L
    }
}
