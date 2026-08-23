package com.zenlauncher.zen.domain.model

enum class SessionOutcome {
    /** Llego a cero por si sola. */
    COMPLETED,

    /** El usuario la termino antes de tiempo. */
    ABANDONED,
    ;

    companion object {
        fun fromStorage(raw: String): SessionOutcome =
            entries.firstOrNull { it.name == raw } ?: ABANDONED
    }
}
