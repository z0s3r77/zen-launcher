package com.zenlauncher.zen.domain.media

/** Estado de una sesion de medios, reducido a lo unico que decide quien manda. */
enum class PlaybackKind {
    /** Suena, o esta a punto: reproduciendo, cargando, avanzando. */
    PLAYING,

    /** Tiene contenido cargado y el usuario lo pauso. */
    PAUSED,

    /** Ni suena ni esta pausada: parada, sin estado o con error. */
    INACTIVE,
}

/**
 * Elige que sesion de medios representa "lo que suena".
 *
 * Un telefono tiene varias sesiones vivas a la vez sin que el usuario lo sepa: el
 * reproductor, el navegador, la grabadora del sistema, un juego. Fiarse del orden en que
 * el sistema las devuelve no vale —ese orden cambia solo—, asi que se ordenan aqui.
 *
 * Las inactivas se **descartan**, no se ordenan al final: una sesion en estado NONE no
 * es "lo que suena flojito", es una aplicacion que registro un reproductor y nunca lo
 * uso. Si no queda ninguna, no hay nada que ensenar.
 */
object MediaSessionRanking {

    fun <T> pick(
        candidates: List<T>,
        kind: (T) -> PlaybackKind,
        lastUpdate: (T) -> Long,
    ): T? = candidates
        .filter { kind(it) != PlaybackKind.INACTIVE }
        .maxWithOrNull(
            // Primero la que suena; entre iguales, la que se movio mas recientemente,
            // que es la que el usuario toco por ultima vez.
            compareBy<T> { if (kind(it) == PlaybackKind.PLAYING) 1 else 0 }
                .thenBy { lastUpdate(it) },
        )
}
