package com.zenlauncher.zen.data.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.zenlauncher.zen.domain.media.MediaSessionRanking
import com.zenlauncher.zen.domain.media.MediaTransport
import com.zenlauncher.zen.domain.media.PlaybackKind
import com.zenlauncher.zen.domain.media.NotificationListenerAccess
import com.zenlauncher.zen.domain.media.NowPlaying
import com.zenlauncher.zen.system.ZenNotificationListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Mando del reproductor con dos niveles, segun lo que el usuario haya concedido.
 *
 * **Ordenes**: siempre por `AudioManager.dispatchMediaKeyEvent`, que no pide ningun
 * permiso y llega a la sesion de medios activa igual que el boton de unos auriculares.
 *
 * **Metadatos**: solo si el acceso al oyente de notificaciones esta concedido, porque
 * `MediaSessionManager.getActiveSessions` exige el nombre de un oyente habilitado y no
 * hay ninguna otra API publica que de titulo, artista o caratula. Sin ese acceso, todo
 * lo de aqui devuelve null y el mando sigue funcionando igual.
 */
class MediaSessionTransport(context: Context) : MediaTransport {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val sessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent =
        ComponentName(appContext, ZenNotificationListener::class.java)

    /**
     * `isMusicActive` mira si hay audio saliendo, no si existe una sesion: con el
     * reproductor en pausa devuelve false, que es justo lo que la interfaz quiere decir.
     */
    override fun isPlaying(): Boolean = audioManager?.isMusicActive == true

    override fun playPause(): Boolean = dispatch(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    override fun next(): Boolean = dispatch(KeyEvent.KEYCODE_MEDIA_NEXT)

    override fun previous(): Boolean = dispatch(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    override fun hasMetadataAccess(): Boolean = NotificationListenerAccess.isGranted(
        setting = Settings.Secure.getString(
            appContext.contentResolver,
            ENABLED_NOTIFICATION_LISTENERS,
        ),
        packageName = appContext.packageName,
    )

    override fun metadataAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Se escucha en dos niveles: que cambie **que sesion** manda (abrir otro
     * reproductor) y que cambie **lo que esa sesion publica** (cancion siguiente,
     * pausa). Con solo lo primero, el titulo se quedaba congelado en la cancion con la
     * que se abrio la pantalla.
     */
    override fun observeNowPlaying(): Flow<NowPlaying?> = callbackFlow {
        val manager = sessionManager
        if (manager == null || !hasMetadataAccess()) {
            trySend(null)
            // Sin acceso no hay nada que escuchar, pero el flujo debe seguir vivo: el
            // usuario puede concederlo y volver, y entonces se resuscribe entero.
            awaitClose { }
            return@callbackFlow
        }

        val handler = Handler(Looper.getMainLooper())
        var controller: MediaController? = null

        val controllerCallback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                trySend(controller?.toNowPlaying())
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                trySend(controller?.toNowPlaying())
            }

            override fun onSessionDestroyed() {
                trySend(null)
            }
        }

        fun bind(sessions: List<MediaController>) {
            controller?.unregisterCallback(controllerCallback)
            controller = sessions.pickActive()
            controller?.registerCallback(controllerCallback, handler)
            trySend(controller?.toNowPlaying())
        }

        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            bind(sessions.orEmpty())
        }

        try {
            manager.addOnActiveSessionsChangedListener(
                sessionsListener,
                listenerComponent,
                handler,
            )
            bind(manager.getActiveSessions(listenerComponent))
        } catch (error: SecurityException) {
            // El acceso pudo revocarse entre la comprobacion y la llamada.
            Log.w(TAG, "El sistema rechazo leer las sesiones de medios", error)
            trySend(null)
        }

        awaitClose {
            controller?.unregisterCallback(controllerCallback)
            manager.removeOnActiveSessionsChangedListener(sessionsListener)
        }
    }.distinctUntilChanged()

    /**
     * Una pulsacion son dos eventos, ACTION_DOWN y ACTION_UP: hay reproductores que
     * ignoran el evento suelto, y uno solo dejaria la tecla "hundida".
     */
    private fun dispatch(keyCode: Int): Boolean {
        val manager = audioManager ?: return false
        return try {
            manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        } catch (error: SecurityException) {
            // Alguna ROM restringe el envio cuando la aplicacion no esta en primer plano.
            Log.w(TAG, "El sistema rechazo la tecla de medios $keyCode", error)
            false
        }
    }

    /**
     * Regresion: se cogia la primera sesion que devolvia el sistema si ninguna sonaba, y
     * la grabadora de Nothing —que registra un reproductor en estado NONE y nunca lo
     * usa— tapaba a Spotify pausado con un "News Reporter - Intro". El orden del sistema
     * no es de confianza; ver [MediaSessionRanking].
     */
    private fun List<MediaController>.pickActive(): MediaController? = MediaSessionRanking.pick(
        candidates = this,
        kind = { it.playbackState.kind() },
        lastUpdate = { it.playbackState?.lastPositionUpdateTime ?: 0L },
    )

    /**
     * Cargar, avanzar o saltar de pista son momentos de una reproduccion en curso: si
     * contaran como inactivas, la ficha parpadearia en cada cambio de cancion.
     */
    private fun PlaybackState?.kind(): PlaybackKind = when (this?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        -> PlaybackKind.PLAYING

        PlaybackState.STATE_PAUSED -> PlaybackKind.PAUSED

        else -> PlaybackKind.INACTIVE
    }

    private fun MediaController.toNowPlaying(): NowPlaying? {
        val metadata = metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if (title.isBlank()) return null
        return NowPlaying(
            title = title,
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            playing = playbackState.kind() == PlaybackKind.PLAYING,
            artwork = metadata.artwork(),
            packageName = packageName,
        )
    }

    /**
     * Los reproductores no se ponen de acuerdo en que clave usan para la caratula, asi
     * que se prueban por orden de calidad. Spotify publica ALBUM_ART.
     */
    private fun MediaMetadata.artwork() =
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    private companion object {
        const val TAG = "MediaSessionTransport"

        /**
         * `Settings.Secure.ENABLED_NOTIFICATION_LISTENERS` existe pero es `@hide`: la
         * constante se escribe a mano porque el valor es estable desde hace anos y es
         * la unica forma publica de leer el estado de la concesion.
         */
        const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
    }
}
