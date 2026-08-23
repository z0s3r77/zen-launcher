package com.zenlauncher.zen.domain.media

import android.graphics.Bitmap

/**
 * Lo que suena ahora mismo, tal y como lo publica la sesion de medios activa.
 *
 * [artwork] es un `Bitmap` de Android y no un tipo propio a proposito: es el objeto que
 * entrega `MediaMetadata` y copiarlo a otra representacion solo serviria para duplicar
 * en memoria una imagen que la aplicacion no modifica. Puede ser null: hay reproductores
 * —y muchos podcasts— que publican titulo pero no caratula.
 */
data class NowPlaying(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val artwork: Bitmap? = null,
    /** Quien reproduce, para poder abrirlo tocando la ficha. */
    val packageName: String? = null,
) {
    /** Sin titulo no hay nada que ensenar: un hueco con "Desconocido" es peor que nada. */
    val hasText: Boolean get() = title.isNotBlank()
}
