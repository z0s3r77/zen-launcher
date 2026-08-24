package com.zenlauncher.zen.domain.notes

/**
 * Conexion entre dos notas.
 *
 * Se persiste **tambien cuando el usuario la ignora**. Sin guardar el rechazo, cada vez
 * que se recalculara el indice volveria a proponerse la misma pareja, y una sugerencia
 * que reaparece despues de haberla descartado es la aplicacion discutiendo con el
 * usuario. Ignorar una vez tiene que bastar para siempre.
 */
data class NoteLink(
    val fromNoteId: String,
    val toNoteId: String,
    /** Semejanza en 0..1 en el momento de proponerla. */
    val score: Float,
    val origin: LinkOrigin,
    val state: LinkState,
    val createdAtMillis: Long,
) {
    /**
     * Clave sin direccion: A-B y B-A son la misma conexion.
     *
     * Que la nota nueva encuentre a la vieja y la vieja encuentre a la nueva es el
     * comportamiento correcto del indice, pero ensenarlo dos veces en la lista, o pedir
     * que se ignore dos veces, no lo es.
     */
    val pairKey: String
        get() = if (fromNoteId <= toNoteId) "$fromNoteId|$toNoteId" else "$toNoteId|$fromNoteId"

    /**
     * Una propuesta recien salida del indice, sobre la que el usuario aun no ha dicho
     * nada.
     *
     * El almacen la usa para no pisar lo que ya hubiera guardado: el indice se ejecuta
     * cada vez que cambian las notas y volveria a proponer las mismas parejas una y otra
     * vez. Lo que el usuario ha decidido pesa mas que lo que el indice vuelve a calcular.
     */
    val isFreshSuggestion: Boolean
        get() = origin == LinkOrigin.SUGGESTED && state == LinkState.PENDING
}

enum class LinkOrigin {
    /** La propuso el indice semantico. */
    SUGGESTED,

    /** La creo el usuario a mano. */
    MANUAL,
    ;

    companion object {
        fun fromStorage(raw: String): LinkOrigin = entries.firstOrNull { it.name == raw } ?: SUGGESTED
    }
}

enum class LinkState {
    /** Propuesta, todavia sin respuesta. */
    PENDING,

    /** El usuario la acepto: pasa a ser parte de la nota. */
    ACCEPTED,

    /** El usuario la descarto: no se vuelve a proponer. */
    IGNORED,
    ;

    companion object {
        fun fromStorage(raw: String): LinkState = entries.firstOrNull { it.name == raw } ?: PENDING
    }
}
