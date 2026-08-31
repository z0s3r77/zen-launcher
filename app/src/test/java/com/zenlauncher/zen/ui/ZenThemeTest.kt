package com.zenlauncher.zen.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.zenlauncher.zen.domain.model.ZenThemeChoice
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenPalette
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `ZenColors` es lo unico que leen las 46 pantallas y componentes de Zen, y ademas se
 * lee dentro de varios `Canvas`, donde un `CompositionLocal` no llegaria. Estos tests
 * fijan el unico eslabon que no se ve: que el tema que entra por [ZenTheme] es el que
 * sale por `ZenColors`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZenThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `sin decir nada se pinta con el tema de siempre`() {
        composeRule.setContent { ZenTheme {} }
        composeRule.waitForIdle()

        assertEquals(ZenPalette.Negro, ZenColors.active)
        assertEquals(ZenPalette.Negro.foreground, ZenColors.Foreground)
    }

    @Test
    fun `el tema elegido llega hasta los colores que lee toda la aplicacion`() {
        composeRule.setContent { ZenTheme(palette = ZenPalette.Sistema) {} }
        composeRule.waitForIdle()

        assertEquals(Color(0xFFFFFFFF), ZenColors.Foreground)
        assertEquals(Color(0xFF2C2C2E), ZenColors.Border)
        // El fondo no cambia: es lo unico que los dos temas comparten a proposito.
        assertEquals(Color(0xFF000000), ZenColors.Background)
    }

    @Test
    fun `cambiar de tema en caliente repinta sin recrear el arbol`() {
        // Regresion: la paleta se publica desde un `SideEffect`, no desde el cuerpo del
        // composable. Escribirla durante la composicion invalidaria a los hijos que
        // acaban de leerla y entraria en bucle; hacerlo con `key(palette)` cambiaria de
        // tema tirando el arbol entero, y con el la pila de navegacion y el desplazado
        // de cada lista. Este test comprueba que basta con recomponer.
        var choice by mutableStateOf(ZenThemeChoice.NEGRO)
        var composiciones = 0

        composeRule.setContent {
            ZenTheme(palette = ZenPalette.of(choice)) {
                composiciones++
            }
        }
        composeRule.waitForIdle()
        assertEquals(ZenPalette.Negro.foreground, ZenColors.Foreground)

        choice = ZenThemeChoice.SISTEMA
        composeRule.waitForIdle()

        assertEquals(ZenPalette.Sistema.foreground, ZenColors.Foreground)
        // El contenido se recompuso, no se volvio a crear desde cero.
        assertEquals(2, composiciones)
    }

    @Test
    fun `el ambar y el rojo sobreviven al cambio de tema`() {
        // Son significado, no aspecto: si un tema los moviera, "restringida" y "salir de
        // Zen" dejarian de leerse igual segun el tema puesto.
        composeRule.setContent { ZenTheme(palette = ZenPalette.Sistema) {} }
        composeRule.waitForIdle()

        assertEquals(Color(0xFFB8894A), ZenColors.Accent)
        assertEquals(Color(0xFFE5484D), ZenColors.Danger)
    }
}
