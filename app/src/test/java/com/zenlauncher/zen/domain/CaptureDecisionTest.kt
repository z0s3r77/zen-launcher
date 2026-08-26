package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.scanner.CaptureDecision
import com.zenlauncher.zen.domain.scanner.CaptureHint
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.domain.scanner.ScanPhase
import com.zenlauncher.zen.domain.scanner.ScanPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cuando dispara solo el escaner.
 *
 * Es la regla entera —cuatro esquinas fiables, tamano suficiente, movil quieto y sin
 * saltos entre frames— probada sin una camara delante, que es la razon de que viva en el
 * dominio y no dentro del ViewModel.
 */
class CaptureDecisionTest {

    /** Una hoja bien encuadrada: ocupa mas de la mitad de la imagen. */
    private val encuadrada = Quad(
        topLeft = ScanPoint(0.12f, 0.10f),
        topRight = ScanPoint(0.88f, 0.10f),
        bottomRight = ScanPoint(0.88f, 0.90f),
        bottomLeft = ScanPoint(0.12f, 0.90f),
    )

    /** La misma hoja, pero lejos: ocupa muy poco. */
    private val lejana = Quad(
        topLeft = ScanPoint(0.40f, 0.40f),
        topRight = ScanPoint(0.62f, 0.40f),
        bottomRight = ScanPoint(0.62f, 0.68f),
        bottomLeft = ScanPoint(0.40f, 0.68f),
    )

    @Test
    fun `sin hoja no hay nada que dibujar ni que disparar`() {
        val estado = CaptureDecision.next(
            previous = CaptureDecision.State(),
            detected = null,
            imageAspect = ASPECTO,
            deviceStill = true,
        )

        assertNull(estado.quad)
        assertEquals(CaptureHint.SEARCHING, estado.hint)
        assertEquals(ScanPhase.DETECTING, estado.phase)
        assertFalse(estado.readyToCapture)
    }

    @Test
    fun `perder la hoja suelta el marco en lugar de dejarlo pintado`() {
        // Un marco que se queda sobre una hoja que ya no esta es peor que ningun marco:
        // dice que la deteccion funciona cuando no esta viendo nada.
        val conHoja = sostener(CaptureDecision.REQUIRED_STEADY_FRAMES)
        assertTrue(conHoja.readyToCapture)

        val sinHoja = CaptureDecision.next(conHoja, null, ASPECTO, deviceStill = true)
        assertNull(sinHoja.quad)
        assertEquals(0, sinHoja.steadyFrames)
    }

    @Test
    fun `una hoja lejana se dibuja pero no dispara`() {
        // Los dos umbrales son distintos a proposito: ensenar el marco es lo que le dice
        // al usuario que se acerque, y dispararle daria un documento minusculo.
        var estado = CaptureDecision.State()
        repeat(CaptureDecision.REQUIRED_STEADY_FRAMES * 2) {
            estado = CaptureDecision.next(estado, lejana, ASPECTO, deviceStill = true)
        }

        assertEquals(lejana, estado.quad)
        assertEquals(CaptureHint.TOO_FAR, estado.hint)
        assertEquals(ScanPhase.DOCUMENT_DETECTED, estado.phase)
        assertFalse(estado.readyToCapture)
    }

    @Test
    fun `un solo frame quieto no basta`() {
        // El temblor de la mano pasa por su punto de inversion decenas de veces por
        // segundo: ahi la variacion es cero y no significa nada.
        val estado = sostener(1)
        assertFalse(estado.readyToCapture)
        assertEquals(CaptureHint.READY, estado.hint)
    }

    @Test
    fun `dispara al completar la racha, ni antes ni despues`() {
        assertFalse(sostener(CaptureDecision.REQUIRED_STEADY_FRAMES - 1).readyToCapture)

        val justo = sostener(CaptureDecision.REQUIRED_STEADY_FRAMES)
        assertTrue(justo.readyToCapture)
        assertEquals(ScanPhase.READY_TO_CAPTURE, justo.phase)
    }

    @Test
    fun `con el movil en movimiento no dispara aunque las esquinas no bailen`() {
        // Apuntando a una hoja con poco contraste las esquinas se quedan clavadas en un
        // borde equivocado aunque la mano viaje: hacen falta las dos condiciones.
        var estado = CaptureDecision.State()
        repeat(CaptureDecision.REQUIRED_STEADY_FRAMES * 2) {
            estado = CaptureDecision.next(estado, encuadrada, ASPECTO, deviceStill = false)
        }

        assertFalse(estado.readyToCapture)
        assertEquals(CaptureHint.HOLD_STILL, estado.hint)
        assertEquals(0, estado.steadyFrames)
    }

    @Test
    fun `un salto entre frames tampoco cuenta como quieto`() {
        var estado = sostener(CaptureDecision.REQUIRED_STEADY_FRAMES - 1)

        // Una sola esquina que se va muy lejos: es lo que pasa cuando el borde de la hoja
        // se confunde con el canto de la mesa.
        val saltada = encuadrada.withCorner(2, ScanPoint(0.60f, 0.90f))
        estado = CaptureDecision.next(estado, saltada, ASPECTO, deviceStill = true)

        assertEquals(CaptureHint.HOLD_STILL, estado.hint)
        assertEquals(0, estado.steadyFrames)
    }

    @Test
    fun `perder la quietud vuelve la cuenta a cero y no un escalon`() {
        // Media cuenta guardada de un encuadre que ya se abandono dispararia antes de
        // tiempo en el siguiente.
        var estado = sostener(CaptureDecision.REQUIRED_STEADY_FRAMES - 1)
        estado = CaptureDecision.next(estado, encuadrada, ASPECTO, deviceStill = false)
        assertEquals(0, estado.steadyFrames)

        estado = CaptureDecision.next(estado, encuadrada, ASPECTO, deviceStill = true)
        assertEquals(1, estado.steadyFrames)
        assertFalse(estado.readyToCapture)
    }

    @Test
    fun `un temblor por debajo del limite sigue contando como quieto`() {
        // El limite es tolerante a proposito: una mano apoyada nunca esta clavada del todo,
        // y exigir cero significaria no disparar jamas.
        var estado = CaptureDecision.State()
        var quad = encuadrada
        repeat(CaptureDecision.REQUIRED_STEADY_FRAMES + 1) { paso ->
            // Menos de la mitad del limite, alternando de signo como un pulso.
            val temblor = if (paso % 2 == 0) 0.005f else -0.005f
            quad = Quad(
                topLeft = encuadrada.topLeft.copy(x = encuadrada.topLeft.x + temblor),
                topRight = encuadrada.topRight.copy(x = encuadrada.topRight.x + temblor),
                bottomRight = encuadrada.bottomRight.copy(x = encuadrada.bottomRight.x + temblor),
                bottomLeft = encuadrada.bottomLeft.copy(x = encuadrada.bottomLeft.x + temblor),
            )
            estado = CaptureDecision.next(estado, quad, ASPECTO, deviceStill = true)
        }

        assertTrue(estado.readyToCapture)
    }

    @Test
    fun `una forma que no puede ser una hoja se descarta aunque este quieta`() {
        val cruzado = Quad(
            topLeft = ScanPoint(0.10f, 0.10f),
            topRight = ScanPoint(0.90f, 0.10f),
            bottomRight = ScanPoint(0.10f, 0.90f),
            bottomLeft = ScanPoint(0.90f, 0.90f),
        )

        var estado = CaptureDecision.State()
        repeat(CaptureDecision.REQUIRED_STEADY_FRAMES * 2) {
            estado = CaptureDecision.next(estado, cruzado, ASPECTO, deviceStill = true)
        }

        assertNull(estado.quad)
        assertEquals(CaptureHint.SEARCHING, estado.hint)
    }

    /** Sostiene la misma hoja quieta durante `frames` y devuelve el estado resultante. */
    private fun sostener(frames: Int): CaptureDecision.State {
        var estado = CaptureDecision.State()
        // Un frame de mas al principio: el primero solo siembra la referencia con la que
        // comparar, porque sin cuadrilatero anterior no hay desplazamiento que medir.
        repeat(frames + 1) {
            estado = CaptureDecision.next(estado, encuadrada, ASPECTO, deviceStill = true)
        }
        return estado
    }

    private companion object {
        const val ASPECTO = 0.75f
    }
}
