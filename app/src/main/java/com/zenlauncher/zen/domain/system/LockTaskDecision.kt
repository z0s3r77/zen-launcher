package com.zenlauncher.zen.domain.system

/** Estado de anclado tal y como lo reporta el sistema, sin depender de Android aqui. */
enum class LockTaskState {
    /** Libre: se puede salir de Zen y bajar el panel de notificaciones. */
    NONE,

    /**
     * Anclado por la propia aplicacion (`startLockTask` sin Device Owner).
     *
     * Bloquea el panel de notificaciones, Inicio y Recientes. Android **garantiza** una
     * salida manteniendo Atras y Recientes a la vez, y esa salida no se puede quitar
     * sin Device Owner. Es lo maximo que alcanza una aplicacion normal.
     */
    PINNED,

    /** Kiosco real, reservado a Device Owner en v0.2. */
    LOCKED,
}

/** Que hacer con el anclado, dado si hay sesion y como esta el sistema ahora mismo. */
enum class LockTaskAction { START, STOP, NONE }

/**
 * Se aisla del ciclo de vida de la Activity porque es facil equivocarse: llamar a
 * `startLockTask` estando ya anclado provoca un parpadeo del dialogo del sistema, y
 * llamar a `stopLockTask` sin estarlo lanza. La decision tiene que ser explicita.
 */
object LockTaskDecision {

    fun decide(sessionActive: Boolean, current: LockTaskState): LockTaskAction = when {
        sessionActive && current == LockTaskState.NONE -> LockTaskAction.START
        // Ya anclado: no repetir, se volveria a mostrar la confirmacion del sistema.
        sessionActive -> LockTaskAction.NONE
        current == LockTaskState.NONE -> LockTaskAction.NONE
        // Sin sesion y anclado por cualquier via: soltar.
        else -> LockTaskAction.STOP
    }
}
