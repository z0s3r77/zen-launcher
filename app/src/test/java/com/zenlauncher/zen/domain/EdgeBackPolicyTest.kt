package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.system.EdgeBackPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ancho de un Nothing Phone (2a) en pixeles, con la franja lateral en densidad real. */
private const val ANCHO = 1084f
private const val FRANJA = 84f
private const val UMBRAL = 147f

class EdgeBackPolicyTest {

    @Test
    fun `arrastrar desde el borde izquierdo hacia dentro vuelve`() {
        // Regresion: con las barras ocultas, Android se queda el primer deslizamiento
        // para sacarlas y solo el segundo llegaba como "atras". Zen lo reconoce ya.
        assertTrue(
            EdgeBackPolicy.goesBack(
                startX = 10f,
                width = ANCHO,
                edge = FRANJA,
                dragged = 400f,
                threshold = UMBRAL,
            ),
        )
    }

    @Test
    fun `y desde el borde derecho, en el otro sentido`() {
        // Zurdos y diestros sujetan el telefono de forma distinta; Android acepta los dos.
        assertTrue(
            EdgeBackPolicy.goesBack(
                startX = ANCHO - 10f,
                width = ANCHO,
                edge = FRANJA,
                dragged = -400f,
                threshold = UMBRAL,
            ),
        )
    }

    @Test
    fun `desde el borde pero hacia fuera no es volver`() {
        assertFalse(
            EdgeBackPolicy.goesBack(
                startX = 10f,
                width = ANCHO,
                edge = FRANJA,
                dragged = -400f,
                threshold = UMBRAL,
            ),
        )
    }

    @Test
    fun `desde el centro nunca vuelve`() {
        // Si valiera, cualquier roce horizontal sobre la reticula sacaria de la pantalla.
        assertFalse(
            EdgeBackPolicy.goesBack(
                startX = ANCHO / 2,
                width = ANCHO,
                edge = FRANJA,
                dragged = 900f,
                threshold = UMBRAL,
            ),
        )
    }

    @Test
    fun `un roce al agarrar el telefono no vuelve`() {
        assertFalse(
            EdgeBackPolicy.goesBack(
                startX = 5f,
                width = ANCHO,
                edge = FRANJA,
                dragged = 30f,
                threshold = UMBRAL,
            ),
        )
    }

    @Test
    fun `la franja lateral es mas ancha que la zona de gestos de Android`() {
        // El dedo que viene del marco entra con velocidad y el primer punto que la
        // pantalla registra suele estar ya unos pixeles dentro.
        assertTrue(EdgeBackPolicy.EDGE_DP > 20)
    }
}
