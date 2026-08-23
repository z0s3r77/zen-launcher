package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.SessionProgress
import com.zenlauncher.zen.presentation.session.ActiveSessionScreen
import com.zenlauncher.zen.presentation.session.SessionUiState
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveSessionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var finished = 0
    private var cancelled = 0
    private var reachedZero = 0

    private val session = ActiveSession(
        id = "s1",
        startedAtWallMillis = 1_700_000_000_000,
        startedAtElapsedMillis = 0,
        plannedDurationMillis = 30 * 60_000L,
        initialBatteryPercent = 84,
        initialCharging = false,
        restrictedAppsCount = 4,
    )

    private fun render(
        confirming: Boolean = false,
        remainingMillis: Long = 20 * 60_000L,
        elapsedMillis: Long = 10 * 60_000L,
        expired: Boolean = false,
    ) {
        composeRule.setContent {
            ZenTheme {
                ActiveSessionScreen(
                    state = SessionUiState(
                        nowMillis = 1_700_000_600_000,
                        active = session,
                        progress = SessionProgress(
                            elapsedMillis = elapsedMillis,
                            remainingMillis = remainingMillis,
                            isExpired = expired,
                            clockAnomaly = false,
                        ),
                    ),
                    session = session,
                    confirming = confirming,
                    onRequestFinish = {},
                    onCancelFinish = { cancelled++ },
                    onConfirmFinish = { finished++ },
                    onTimerReachedZero = { reachedZero++ },
                )
            }
        }
    }

    @Test
    fun `muestra el tiempo restante y el transcurrido`() {
        render()

        // La hora de pared no se pinta aqui: los indicadores en vivo son de la home.
        composeRule.onNodeWithText("30 MIN").assertIsDisplayed()
        composeRule.onNodeWithText("20:00").assertIsDisplayed()
        composeRule.onNodeWithText("10:00").assertIsDisplayed()
    }

    @Test
    fun `sin confirmacion pendiente no aparece el dialogo`() {
        render(confirming = false)

        composeRule.onNodeWithText("¿Terminar la sesión?").assertDoesNotExistSafely()
    }

    @Test
    fun `TERMINAR confirma el abandono`() {
        render(confirming = true)

        composeRule.onNodeWithText("TERMINAR").performClick()

        assertEquals(1, finished)
        assertEquals(0, cancelled)
    }

    @Test
    fun `SEGUIR cancela y mantiene la sesion`() {
        // Regresion: al invertir el peso visual de los botones se intercambiaron las
        // ranuras del dialogo, y es facil cablear las acciones al reves.
        render(confirming = true)

        composeRule.onNodeWithText("SEGUIR").performClick()

        assertEquals(1, cancelled)
        assertEquals(0, finished)
    }

    @Test
    fun `al llegar a cero avisa una sola vez para cerrar la sesion`() {
        render(remainingMillis = 0, elapsedMillis = 30 * 60_000L, expired = true)

        assertEquals(1, reachedZero)
    }

    @Test
    fun `mientras queda tiempo no intenta cerrar la sesion`() {
        render(expired = false)

        assertEquals(0, reachedZero)
    }
}

/** `assertDoesNotExist` sobre un nodo inexistente lanza; esto lo hace legible. */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistSafely() {
    assertDoesNotExist()
}
