package com.zenlauncher.zen.ui

import androidx.compose.ui.graphics.Color
import com.zenlauncher.zen.presentation.theme.ZenColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * La paleta es monocroma, asi que toda la jerarquia depende del contraste. Estos tests
 * la fijan numericamente para que un retoque de color no degrade la legibilidad sin que
 * nadie se entere.
 */
class ZenColorsTest {

    @Test
    fun `el fondo es negro puro para apagar el pixel en AMOLED`() {
        // Un gris casi negro enciende los subpixeles sin aportar nada visualmente.
        assertEquals(Color(0xFF000000), ZenColors.Background)
        assertEquals(0.0, relativeLuminance(ZenColors.Background), 0.0)
    }

    @Test
    fun `el texto principal supera de sobra el nivel AAA`() {
        assertMinContrast(ZenColors.Foreground, ZenColors.Background, minimum = 7.0)
    }

    @Test
    fun `todos los tonos que llevan texto cumplen AA`() {
        // Dim y Muted estaban por debajo (2.88:1 y 4.14:1) y se usan en etiquetas que
        // llevan informacion real: BAT 63%, RESTANTE, nombres de aplicacion.
        listOf(
            ZenColors.Dim,
            ZenColors.Muted,
            ZenColors.Secondary,
            ZenColors.Tertiary,
            ZenColors.Reading,
            ZenColors.Foreground,
            ZenColors.Danger,
        ).forEach { assertMinContrast(it, ZenColors.Background, minimum = 4.5) }
    }

    @Test
    fun `los tonos de trazo se quedan por debajo a proposito`() {
        // Filetes, bordes y marcas huecas no son texto: si alguno subiera hasta el
        // rango legible, la retícula empezaria a competir con el contenido.
        listOf(
            ZenColors.Hairline,
            ZenColors.Border,
            ZenColors.Faint,
            ZenColors.Disabled,
        ).forEach { tone ->
            val ratio = contrastRatio(tone, ZenColors.Background)
            assertTrue("$tone deberia quedarse por debajo de 3:1, era %.2f".format(ratio), ratio < 3.0)
        }
    }

    @Test
    fun `el acento ambar cumple el minimo de elementos no textuales`() {
        // Los cuadrados de estado de 6dp no son texto: el umbral aplicable es 3 a 1.
        assertMinContrast(ZenColors.Accent, ZenColors.Background, minimum = 3.0)
    }

    @Test
    fun `el fondo es el tono mas oscuro de la escala`() {
        val scale = listOf(
            ZenColors.Hairline,
            ZenColors.Border,
            ZenColors.Faint,
            ZenColors.Disabled,
            ZenColors.Dim,
            ZenColors.Muted,
            ZenColors.Secondary,
            ZenColors.Tertiary,
            ZenColors.Reading,
            ZenColors.Foreground,
        )
        val background = relativeLuminance(ZenColors.Background)
        scale.forEach { tone ->
            assertTrue(
                "El fondo debe ser mas oscuro que $tone",
                relativeLuminance(tone) > background,
            )
        }
        // Y la escala debe subir de forma monotona: sin esto dos tonos podrian
        // intercambiarse y romper la jerarquia sin cambiar ningun test.
        scale.zipWithNext { darker, lighter ->
            assertTrue(
                "La escala debe crecer en luminosidad: $darker antes que $lighter",
                relativeLuminance(darker) < relativeLuminance(lighter),
            )
        }
    }

    private fun assertMinContrast(foreground: Color, background: Color, minimum: Double) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "Contraste %.2f:1 por debajo del minimo %.1f:1".format(ratio, minimum),
            ratio >= minimum,
        )
    }
}

/** WCAG 2.1: (L1 + 0.05) / (L2 + 0.05). */
private fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) +
        0.7152 * channel(color.green) +
        0.0722 * channel(color.blue)
}
