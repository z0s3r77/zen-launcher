package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.notifications.NotificationGroup
import com.zenlauncher.zen.fakes.appNotification
import com.zenlauncher.zen.presentation.notifications.NotificationsScreen
import com.zenlauncher.zen.presentation.notifications.NotificationsUiState
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class NotificationsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<String>()
    private var grants = 0
    private var backs = 0

    private val whatsapp = NotificationGroup(
        packageName = "com.whatsapp",
        label = "WhatsApp",
        notifications = listOf(
            appNotification("com.whatsapp", title = "Ana", text = "¿Quedamos?"),
            appNotification("com.whatsapp", title = "Luis", text = "Vale"),
        ),
    )

    private val gmail = NotificationGroup(
        packageName = "com.google.android.gm",
        label = "Gmail",
        notifications = listOf(
            appNotification("com.google.android.gm", title = "Factura", text = "Adjunto"),
        ),
    )

    private fun render(
        groups: List<NotificationGroup>,
        hasAccess: Boolean = true,
        focusPackage: String? = null,
    ) {
        composeRule.setContent {
            ZenTheme {
                NotificationsScreen(
                    state = NotificationsUiState(groups = groups, hasAccess = hasAccess),
                    focusPackage = focusPackage,
                    onOpenApp = { opened += it },
                    onGrantAccess = { grants++ },
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun `ensena quien escribio y que dice, agrupado por aplicacion`() {
        render(groups = listOf(whatsapp, gmail))

        composeRule.onNodeWithText("WHATSAPP").assertIsDisplayed()
        composeRule.onNodeWithText("Ana").assertIsDisplayed()
        composeRule.onNodeWithText("¿Quedamos?").assertIsDisplayed()
        composeRule.onNodeWithText("GMAIL").assertIsDisplayed()
    }

    @Test
    fun `tocar un aviso abre su aplicacion`() {
        render(groups = listOf(whatsapp))

        composeRule.onNodeWithText("Ana").performClick()

        assertEquals(listOf("com.whatsapp"), opened)
    }

    @Test
    fun `entrando por la marca de una aplicacion solo se ve esa`() {
        // Quien toca el "3" de WhatsApp va a WhatsApp, no al panel entero: ensenarle
        // todo lo demas seria devolverle el ruido que acaba de esquivar.
        render(groups = listOf(whatsapp, gmail), focusPackage = "com.whatsapp")

        composeRule.onNodeWithText("WHATSAPP").assertIsDisplayed()
        composeRule.onNodeWithText("GMAIL").assertDoesNotExist()
        composeRule.onNodeWithText("Factura").assertDoesNotExist()
    }

    @Test
    fun `sin acceso lo dice y ofrece concederlo, en vez de fingir que no hay nada`() {
        render(groups = emptyList(), hasAccess = false)

        composeRule.onNodeWithText("NADA PENDIENTE").assertDoesNotExist()
        composeRule.onNodeWithText("Conceder el acceso").performClick()

        assertEquals(1, grants)
    }

    @Test
    fun `con acceso y sin nada pendiente lo dice con todas las letras`() {
        render(groups = emptyList())

        composeRule.onNodeWithText("NADA PENDIENTE").assertIsDisplayed()
    }

    @Test
    fun `siempre hay salida a un toque`() {
        // Las barras del sistema estan ocultas: el gesto de atras no es fiable.
        render(groups = listOf(whatsapp))

        composeRule.onNodeWithContentDescription("Volver").performClick()

        assertEquals(1, backs)
    }
}
