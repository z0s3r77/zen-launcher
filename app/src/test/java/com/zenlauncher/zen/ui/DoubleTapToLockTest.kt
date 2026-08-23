package com.zenlauncher.zen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.presentation.components.LocalDoubleTapToLock
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El gesto solo debe existir en el fondo. Si se disparara tambien encima de las filas,
 * tocar dos veces seguidas una aplicacion apagaria la pantalla en lugar de abrirla.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DoubleTapToLockTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var locks = 0
    private var rowClicks = 0

    private fun render() {
        composeRule.setContent {
            ZenTheme {
                CompositionLocalProvider(LocalDoubleTapToLock provides { locks++ }) {
                    ZenScreen {
                        ZenListRow(label = "Una fila", onClick = { rowClicks++ })
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .testTag("zona-vacia"),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `doble toque en una zona vacia apaga la pantalla`() {
        render()

        composeRule.onNodeWithTag("zona-vacia").performTouchInput { doubleClick() }

        assertEquals(1, locks)
        assertEquals(0, rowClicks)
    }

    @Test
    fun `doble toque sobre una fila no apaga la pantalla`() {
        render()

        composeRule.onNodeWithText("Una fila").performTouchInput { doubleClick() }

        // La fila consume los toques, asi que el gesto del fondo no llega a dispararse.
        assertEquals(0, locks)
    }

    @Test
    fun `un toque suelto en el fondo no hace nada`() {
        render()

        composeRule.onNodeWithTag("zona-vacia").performClick()

        assertEquals(0, locks)
    }
}
