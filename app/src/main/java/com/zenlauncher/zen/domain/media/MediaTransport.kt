package com.zenlauncher.zen.domain.media

import android.content.Intent
import kotlinx.coroutines.flow.Flow

/**
 * Mando del reproductor que este sonando, sea cual sea.
 *
 * Tiene **dos niveles**, y el bajo funciona siempre:
 *
 * - Sin ningun permiso: mandar teclas de medios y saber si sale audio. Es lo que hace
 *   [isPlaying], [playPause], [next] y [previous].
 * - Con acceso al oyente de notificaciones concedido: ademas, titulo, artista y
 *   caratula por [observeNowPlaying]. Ese acceso es **opcional** y el usuario lo
 *   concede a mano; sin el, la aplicacion no pierde ninguna funcion del mando.
 *
 * Cada orden devuelve false cuando no hay nadie al otro lado, para que la interfaz no
 * finja un cambio de estado que no ha ocurrido.
 */
interface MediaTransport {

    /** ¿Esta saliendo audio ahora mismo? */
    fun isPlaying(): Boolean

    fun playPause(): Boolean

    fun next(): Boolean

    fun previous(): Boolean

    /**
     * Lo que suena, o null si no hay nada sonando **o si no se concedio el acceso**.
     * Se reemite al cambiar de cancion, al pausar y al cambiar de reproductor.
     */
    fun observeNowPlaying(): Flow<NowPlaying?>

    /** Si hoy se pueden leer los metadatos. */
    fun hasMetadataAccess(): Boolean

    /** Pantalla del sistema donde se concede o se revoca ese acceso. */
    fun metadataAccessIntent(): Intent
}
