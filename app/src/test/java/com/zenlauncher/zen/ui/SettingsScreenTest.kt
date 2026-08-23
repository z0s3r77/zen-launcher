package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.zenlauncher.zen.presentation.settings.SettingsScreen
import com.zenlauncher.zen.presentation.settings.SettingsUiState
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var homeRoleRequests = 0
    private var doubleTapToggles = 0
    private var nowPlayingToggles = 0
    private var homeAppsOpened = 0
    private var backs = 0

    private fun render(
        isDefaultLauncher: Boolean,
        doubleTapLockEnabled: Boolean = false,
        nowPlayingEnabled: Boolean = false,
        homeAppsCount: Int = 0,
    ) {
        composeRule.setContent {
            ZenTheme {
                SettingsScreen(
                    state = SettingsUiState(homeAppsCount = homeAppsCount, loading = false),
                    isDefaultLauncher = isDefaultLauncher,
                    doubleTapLockEnabled = doubleTapLockEnabled,
                    nowPlayingEnabled = nowPlayingEnabled,
                    onOpenHomeApps = { homeAppsOpened++ },
                    onSetDuration = {},
                    onRequestHomeRole = { homeRoleRequests++ },
                    onToggleDoubleTapLock = { doubleTapToggles++ },
                    onToggleNowPlaying = { nowPlayingToggles++ },
                    onOpenBatterySaver = {},
                    onOpenAccessibility = {},
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun `si Zen no es el launcher ofrece establecerlo y se marca inactivo`() {
        render(isDefaultLauncher = false)

        // La fila es pulsable, asi que Compose funde etiqueta y estado en un solo nodo:
        // se busca por los dos textos para no confundirla con "Ahorro de bateria",
        // que tambien muestra INACTIVO.
        composeRule
            .onNode(hasText("Zen como pantalla de inicio") and hasText("INACTIVO"))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `si Zen ya es el launcher la fila sigue siendo pulsable para poder salir`() {
        // Regresion: esta fila quedaba sin accion cuando Zen tenia el rol, dejando al
        // usuario sin forma de devolver la pantalla de inicio desde dentro de la app.
        render(isDefaultLauncher = true)

        composeRule.onNodeWithText("Cambiar la pantalla de inicio")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(1, homeRoleRequests)
    }

    @Test
    fun `el doble toque para bloquear se puede conceder desde ajustes`() {
        render(isDefaultLauncher = false, doubleTapLockEnabled = false)

        composeRule
            .onNode(hasText("Doble toque para bloquear") and hasText("INACTIVO"))
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(1, doubleTapToggles)
    }

    @Test
    fun `concedido, la fila del doble toque sigue siendo pulsable para revocarlo`() {
        // Android exige que se revoque desde sus Ajustes, pero la fila debe llevar
        // hasta alli: si no, quedaria concedido sin forma visible de quitarlo.
        render(isDefaultLauncher = false, doubleTapLockEnabled = true)

        composeRule
            .onNode(hasText("Doble toque para bloquear") and hasText("ACTIVO"))
            .assertHasClickAction()
            .performClick()

        assertEquals(1, doubleTapToggles)
    }

    @Test
    fun `muestra ACTIVO en la fila del launcher cuando Zen tiene el rol`() {
        render(isDefaultLauncher = true)

        composeRule
            .onNode(hasText("Cambiar la pantalla de inicio") and hasText("ACTIVO"))
            .assertIsDisplayed()
    }

    @Test
    fun `hay una salida propia sin depender del gesto del sistema`() {
        // Regresion: con la barra de navegacion oculta, el primer deslizamiento desde el
        // borde saca las barras del sistema en vez de volver atras, y hay que repetirlo.
        render(isDefaultLauncher = true)

        composeRule.onNodeWithContentDescription("Volver")
            .assertHasClickAction()
            .performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `elegir las aplicaciones del inicio es una fila con su cuenta, no una lista`() {
        // Regresion: de esta pantalla colgaba la lista de TODAS las aplicaciones del
        // telefono. Para poner el telefono en el inicio habia que reconocerlo entre
        // doscientas filas y Ajustes no tenia final.
        render(isDefaultLauncher = true, homeAppsCount = 3)

        composeRule
            .onNode(hasText("Aplicaciones en el Inicio") and hasText("03 / 08"))
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        assertEquals(1, homeAppsOpened)
    }

    @Test
    fun `el acceso a los metadatos de la cancion se ofrece apagado y con su letra pequena`() {
        render(isDefaultLauncher = true)

        composeRule.onNodeWithText("Información de la canción")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        assertEquals(1, nowPlayingToggles)
    }
}
