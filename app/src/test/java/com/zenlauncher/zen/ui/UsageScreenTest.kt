package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.usage.UsageLevel
import com.zenlauncher.zen.domain.usage.UsageReading
import com.zenlauncher.zen.presentation.theme.ZenTheme
import com.zenlauncher.zen.presentation.usage.UsageAppRow
import com.zenlauncher.zen.presentation.usage.UsageScreen
import com.zenlauncher.zen.presentation.usage.UsageUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class UsageScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var grants = 0
    private var backs = 0
    private var weeks = 0

    private fun render(state: UsageUiState) {
        composeRule.setContent {
            ZenTheme {
                UsageScreen(
                    state = state,
                    onBack = { backs++ },
                    onGrantAccess = { grants++ },
                    onOpenWeek = { weeks++ },
                )
            }
        }
    }

    private fun medido(
        minutos: Long = 200,
        unlocks: Int = 70,
        apps: List<UsageAppRow> = emptyList(),
    ) = UsageUiState(
        reading = UsageReading(
            level = UsageLevel.ALTA,
            screenMillis = minutos * 60_000L,
            unlocks = unlocks,
            topApp = null,
            measured = true,
        ),
        apps = apps,
        hasAccess = true,
    )

    /**
     * Sin acceso concedido no se ensena un cero. Un cero dice "hoy no has usado el
     * movil" y seria mentira: lo que pasa es que Zen no puede verlo.
     */
    @Test
    fun `sin acceso ofrece concederlo y no ensena cifras`() {
        render(UsageUiState())

        composeRule.onNodeWithText("Conceder en Ajustes de Android").assertIsDisplayed()
        composeRule.onNodeWithText("Tiempo de pantalla").assertDoesNotExist()

        composeRule.onNodeWithText("Conceder en Ajustes de Android").performClick()
        assertEquals(1, grants)
    }

    @Test
    fun `con acceso ensena tiempo y desbloqueos`() {
        render(medido(minutos = 200, unlocks = 70))

        composeRule.onNodeWithText("Tiempo de pantalla").assertIsDisplayed()
        composeRule.onNodeWithText("3h 20m").assertIsDisplayed()
        composeRule.onNodeWithText("70").assertIsDisplayed()
    }

    @Test
    fun `cada aplicacion sale con su rotulo, su tiempo y sus veces`() {
        render(
            medido(
                apps = listOf(
                    UsageAppRow("com.instagram.android", "Instagram", openings = 12, foregroundMillis = 45 * 60_000L),
                ),
            ),
        )

        composeRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeRule.onNodeWithText("45m · 12 veces").assertIsDisplayed()
    }

    /**
     * Una lista de cuarenta aplicaciones con dos minutos cada una no dice nada y
     * convierte la pantalla en un inventario.
     */
    @Test
    fun `la lista de aplicaciones tiene tope`() {
        val muchas = (1..14).map {
            UsageAppRow("com.app$it", "App %02d".format(it), openings = 1, foregroundMillis = (20 - it) * 60_000L)
        }
        render(medido(apps = muchas))

        // `assertExists` y no `assertIsDisplayed`: la decima fila cae bajo el pliegue
        // y hay que arrastrar para verla. Lo que se comprueba es que esta compuesta.
        composeRule.onNodeWithText("App 10").assertExists()
        composeRule.onNodeWithText("App 11").assertDoesNotExist()
    }

    @Test
    fun `con acceso pero sin actividad lo dice en lugar de dejar la pantalla vacia`() {
        render(medido(minutos = 0, unlocks = 0))

        composeRule.onNodeWithText("SIN ACTIVIDAD MEDIDA HOY").assertIsDisplayed()
    }
}
