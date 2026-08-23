package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.system.LockTaskAction
import com.zenlauncher.zen.domain.system.LockTaskDecision
import com.zenlauncher.zen.domain.system.LockTaskState
import org.junit.Assert.assertEquals
import org.junit.Test

class LockTaskDecisionTest {

    @Test
    fun `al empezar una sesion se ancla la pantalla`() {
        assertEquals(
            LockTaskAction.START,
            LockTaskDecision.decide(sessionActive = true, current = LockTaskState.NONE),
        )
    }

    @Test
    fun `estando ya anclado no se vuelve a anclar`() {
        // Repetir startLockTask reabre la confirmacion del sistema en mitad de la
        // sesion, que es justo la interrupcion que Zen intenta evitar.
        assertEquals(
            LockTaskAction.NONE,
            LockTaskDecision.decide(sessionActive = true, current = LockTaskState.PINNED),
        )
    }

    @Test
    fun `al terminar la sesion se suelta el anclado`() {
        assertEquals(
            LockTaskAction.STOP,
            LockTaskDecision.decide(sessionActive = false, current = LockTaskState.PINNED),
        )
    }

    @Test
    fun `sin sesion y sin anclado no se llama a stopLockTask`() {
        // stopLockTask sin estar anclado lanza IllegalStateException.
        assertEquals(
            LockTaskAction.NONE,
            LockTaskDecision.decide(sessionActive = false, current = LockTaskState.NONE),
        )
    }

    @Test
    fun `un kiosco de Device Owner tambien se suelta al terminar`() {
        // Reservado a v0.2, pero la decision no deberia dejarlo colgado si aparece.
        assertEquals(
            LockTaskAction.STOP,
            LockTaskDecision.decide(sessionActive = false, current = LockTaskState.LOCKED),
        )
    }

    @Test
    fun `con sesion y kiosco activo no se toca nada`() {
        assertEquals(
            LockTaskAction.NONE,
            LockTaskDecision.decide(sessionActive = true, current = LockTaskState.LOCKED),
        )
    }
}
