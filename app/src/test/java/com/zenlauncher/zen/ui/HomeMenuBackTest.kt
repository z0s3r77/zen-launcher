package com.zenlauncher.zen.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.presentation.home.HomeScreen
import com.zenlauncher.zen.presentation.home.HomeUiState
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * El gesto de atras sobre la pantalla de inicio.
 *
 * Va en su propia clase porque necesita una Activity de verdad para poder disparar el
 * despachador de atras; el resto de la home se prueba sin ella.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeMenuBackTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun render() {
        composeRule.setContent {
            ZenTheme {
                HomeScreen(
                    state = HomeUiState(
                        nowMillis = 1_700_000_000_000,
                        homeApps = listOf(
                            InstalledApp("com.phone", "Teléfono", "com.phone/.Main"),
                        ),
                    ),
                    onLaunchApp = {},
                    onOpenDrawer = {},
                    onOpenHomeApps = {},
                    onOpenNotes = {},
                    onStartSession = {},
                    onBreathe = {},
                    onOpenRestricted = {},
                    onOpenStats = {},
                    onOpenSettings = {},
                    onOpenNotifications = {},
                    onExitZen = {},
                    onPreviousTrack = {},
                    onTogglePlayback = {},
                    onNextTrack = {},
                    onOpenPlayer = {},
                    locale = Locale.forLanguageTag("es-ES"),
                )
            }
        }
    }

    private fun pressBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    @Test
    fun `con el menu abierto, atras lo cierra`() {
        render()
        composeRule.onNodeWithText("Menú").performClick()
        composeRule.onNodeWithText("Registro").assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Registro").assertDoesNotExist()
        composeRule.onNodeWithText("Teléfono").assertIsDisplayed()
    }

    @Test
    fun `en la home, atras no lleva a ninguna parte`() {
        // Zen es la pantalla de inicio: si atras cerrase la aplicacion, el movil se
        // quedaria sin home. El menu es la unica cara de la que se sale.
        render()

        pressBack()

        composeRule.onNodeWithText("Teléfono").assertIsDisplayed()
    }
}
