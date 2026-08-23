package com.zenlauncher.zen.domain

import android.view.WindowInsets
import com.zenlauncher.zen.domain.system.SystemBar
import com.zenlauncher.zen.domain.system.SystemBarsPolicy
import com.zenlauncher.zen.system.toInsetsTypeMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemBarsPolicyTest {

    @Test
    fun `se oculta la barra de gestos, que solo invita a salir de la home`() {
        assertTrue(SystemBar.NAVIGATION in SystemBarsPolicy.hidden)
    }

    @Test
    fun `la de estado se queda`() {
        // Regresion: ocultarla hacia que Android la sacase de golpe encima del contenido
        // en cada gesto desde un borde y la volviese a esconder sola. Una barra que
        // aparece y desaparece llama mas la atencion que una que simplemente esta.
        assertFalse(SystemBar.STATUS in SystemBarsPolicy.hidden)
    }

    @Test
    fun `la politica se traduce a la mascara que espera el controlador de insets`() {
        assertEquals(
            WindowInsets.Type.navigationBars(),
            SystemBarsPolicy.hidden.toInsetsTypeMask(),
        )
    }

    @Test
    fun `la mascara sabria traducir tambien la de estado`() {
        // El traductor no depende de la politica del momento: si manana se vuelve a
        // ocultar, no hay que tocarlo.
        assertEquals(
            WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars(),
            setOf(SystemBar.STATUS, SystemBar.NAVIGATION).toInsetsTypeMask(),
        )
    }

    @Test
    fun `un conjunto vacio no oculta nada`() {
        assertEquals(0, emptySet<SystemBar>().toInsetsTypeMask())
    }
}
