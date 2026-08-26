package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.usage.Compulsion
import com.zenlauncher.zen.domain.usage.CompulsionKind
import com.zenlauncher.zen.presentation.theme.ZenTheme
import com.zenlauncher.zen.presentation.usage.DistractionScreen
import com.zenlauncher.zen.presentation.usage.DistractionUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DistractionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var breaths = 0
    private var sessions = 0
    private var dismissals = 0

    private fun render(state: DistractionUiState) {
        composeRule.setContent {
            ZenTheme {
                DistractionScreen(
                    state = state,
                    onBreathe = { breaths++ },
                    onStartSession = { sessions++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
    }

    private fun arrastre() = DistractionUiState(
        compulsion = Compulsion(
            kind = CompulsionKind.ARRASTRE,
            packageName = "com.instagram.android",
            openings = 1,
            foregroundMillis = 48 * 60_000L,
            windowMinutes = 60,
        ),
        appLabel = "Instagram",
    )

    @Test
    fun `el arrastre ensena la aplicacion y el tiempo`() {
        render(arrastre())

        composeRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeRule.onNodeWithText("48m").assertIsDisplayed()
        composeRule.onNodeWithText("ARRASTRE").assertIsDisplayed()
    }

    /**
     * En un arrastre la apertura es una sola: escribir "1 APERTURA" al lado de "48m" no
     * anade un dato, anade ruido.
     */
    @Test
    fun `el arrastre no ensena la cuenta de aperturas`() {
        render(arrastre())

        composeRule.onNodeWithText("1 APERTURA").assertDoesNotExist()
        // Y la ventana tampoco: "48m EN 60 MIN" se lee como dos datos que se
        // contradicen. En un arrastre la duracion es el hecho.
        composeRule.onNodeWithText("EN 60 MIN").assertDoesNotExist()
    }

    @Test
    fun `la repeticion ensena cuantas veces se abrio`() {
        render(
            DistractionUiState(
                compulsion = Compulsion(
                    kind = CompulsionKind.REPETICION,
                    packageName = "com.instagram.android",
                    openings = 7,
                    foregroundMillis = 12 * 60_000L,
                    windowMinutes = 30,
                ),
                appLabel = "Instagram",
            ),
        )

        composeRule.onNodeWithText("7 APERTURAS").assertIsDisplayed()
        composeRule.onNodeWithText("EN 30 MIN").assertIsDisplayed()
    }

    /**
     * En picoteo no sobra una aplicacion, sobra el salto: poner el nombre de la ultima
     * seria senalar a la equivocada.
     */
    @Test
    fun `el picoteo no senala a ninguna aplicacion`() {
        render(
            DistractionUiState(
                compulsion = Compulsion(
                    kind = CompulsionKind.PICOTEO,
                    packageName = null,
                    openings = 14,
                    foregroundMillis = 9 * 60_000L,
                    windowMinutes = 15,
                ),
                appLabel = null,
            ),
        )

        composeRule.onNodeWithText("EL MÓVIL").assertIsDisplayed()
        composeRule.onNodeWithText("14 APERTURAS").assertIsDisplayed()
    }

    /**
     * El aviso no bloquea nada. Un aviso que no deja pasar se aprende a odiar, y lo
     * unico que consigue es que se desinstale el launcher.
     */
    @Test
    fun `siempre hay una salida que no castiga`() {
        render(arrastre())

        composeRule.onNodeWithText("Seguir como estaba").performClick()

        assertEquals(1, dismissals)
    }

    @Test
    fun `las dos salidas son las que Zen ya sabe hacer`() {
        render(arrastre())

        composeRule.onNodeWithText("RESPIRA").performClick()
        composeRule.onNodeWithText("SESIÓN ZEN").performClick()

        assertEquals(1, breaths)
        assertEquals(1, sessions)
    }
}
