package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.system.SwipeUpPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La pantalla de un Nothing Phone (2a) en pixeles, con la franja de gestos de Android
 * abajo. Los numeros son los de un dispositivo real para que los casos limite —empezar
 * justo en la raya— signifiquen algo.
 */
private const val ALTO = 2412f
private const val FRANJA = 132f
private const val UMBRAL = 160f

class SwipeUpPolicyTest {

    @Test
    fun `deslizar desde el centro abre la lista`() {
        assertTrue(
            SwipeUpPolicy.opensDrawer(
                startY = 1200f,
                height = ALTO,
                systemEdge = FRANJA,
                dragged = -400f,
                threshold = UMBRAL,
            ),
        )
    }

    @Test
    fun `deslizar desde el borde inferior no abre nada`() {
        // Regresion: ahi el usuario esta sacando la barra de gestos, que Zen oculta pero
        // Android sigue entregando. La lista se abria sola cada vez.
        assertFalse(
            SwipeUpPolicy.opensDrawer(
                startY = ALTO - 20f,
                height = ALTO,
                systemEdge = FRANJA,
                dragged = -900f,
                threshold = UMBRAL,
            ),
        )
    }

    @Test
    fun `la franja se reserva entera aunque el gesto acabe muy arriba`() {
        // Lo que decide es donde empieza: es lo unico que distingue "quiero la barra"
        // de "quiero la lista".
        assertFalse(
            SwipeUpPolicy.opensDrawer(
                startY = ALTO - FRANJA + 1f,
                height = ALTO,
                systemEdge = FRANJA,
                dragged = -2000f,
                threshold = UMBRAL,
            ),
        )
    }

    @Test
    fun `justo encima de la franja sigue siendo de Zen`() {
        assertTrue(
            SwipeUpPolicy.opensDrawer(
                startY = ALTO - FRANJA - 1f,
                height = ALTO,
                systemEdge = FRANJA,
                dragged = -400f,
                threshold = UMBRAL,
            ),
        )
    }

    @Test
    fun `un roce corto no abre la lista sin querer`() {
        assertFalse(
            SwipeUpPolicy.opensDrawer(
                startY = 1200f,
                height = ALTO,
                systemEdge = FRANJA,
                dragged = -40f,
                threshold = UMBRAL,
            ),
        )
    }

    @Test
    fun `deslizar hacia abajo nunca abre la lista`() {
        assertFalse(
            SwipeUpPolicy.opensDrawer(
                startY = 400f,
                height = ALTO,
                systemEdge = FRANJA,
                dragged = 900f,
                threshold = UMBRAL,
            ),
        )
    }
}
