package com.zenlauncher.zen.ui

import com.zenlauncher.zen.presentation.theme.ZenMotion
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zen se mira cincuenta veces al dia: una transicion que se nota es una espera cincuenta
 * veces al dia. Estos tests fijan los tiempos igual que [ZenColorsTest] fija el
 * contraste, para que "hacerla un poco mas vistosa" tenga que romper un test antes.
 */
class ZenMotionTest {

    @Test
    fun `ninguna transicion llega al umbral en el que se siente como espera`() {
        // Por encima de ~200 ms un cambio deja de leerse como respuesta inmediata.
        assertTrue(
            "Entrar tarda ${ZenMotion.EnterMillis} ms",
            ZenMotion.EnterMillis <= MAXIMO_MILLIS,
        )
        assertTrue(
            "Salir tarda ${ZenMotion.ExitMillis} ms",
            ZenMotion.ExitMillis <= MAXIMO_MILLIS,
        )
    }

    @Test
    fun `salir cuesta menos que entrar`() {
        // Lo que llega hay que poder seguirlo; lo que se va no merece atencion y no
        // debe retrasar lo siguiente que el usuario ya ha pedido.
        assertTrue(ZenMotion.ExitMillis < ZenMotion.EnterMillis)
    }

    @Test
    fun `la curva arranca rapido y frena, no al reves`() {
        // Con la curva invertida el movimiento se lee como un tiron. A la mitad del
        // tiempo ya tiene que estar pasado de la mitad del recorrido.
        assertTrue(ZenMotion.Standard.transform(0.5f) > 0.5f)
        // Y sin rebotes: los extremos son exactos.
        assertTrue(ZenMotion.Standard.transform(0f) == 0f)
        assertTrue(ZenMotion.Standard.transform(1f) == 1f)
    }

    private companion object {
        const val MAXIMO_MILLIS = 200
    }
}
