package com.zenlauncher.zen.ui

import androidx.compose.ui.graphics.Color
import com.zenlauncher.zen.domain.model.ZenThemeChoice
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Las paletas son monocromas, asi que toda la jerarquia depende del contraste. Estos
 * tests la fijan numericamente para que un retoque de color no degrade la legibilidad
 * sin que nadie se entere.
 *
 * Todo se comprueba en **las dos**: un tema nuevo que no cumpla lo mismo que el de
 * siempre no es una preferencia de aspecto, es una pantalla peor. Por eso las pruebas
 * recorren `ZenThemeChoice.entries` y no una lista escrita a mano: anadir un tema sin
 * pasar por aqui no deberia ser posible.
 */
class ZenPaletteTest {

    private val palettes = ZenThemeChoice.entries.map { it to ZenPalette.of(it) }

    @Test
    fun `el fondo es negro puro en los dos temas para apagar el pixel en AMOLED`() {
        // Un gris casi negro enciende los subpixeles sin aportar nada visualmente, y la
        // pantalla de inicio se queda encendida mientras se elige aplicacion. El tema
        // "del sistema" sube las superficies y el texto, no el fondo.
        palettes.forEach { (choice, palette) ->
            assertEquals("$choice", Color(0xFF000000), palette.background)
            assertEquals("$choice", 0.0, relativeLuminance(palette.background), 0.0)
        }
    }

    @Test
    fun `el texto principal supera de sobra el nivel AAA en los dos temas`() {
        palettes.forEach { (choice, palette) ->
            assertMinContrast(choice, palette.foreground, palette.background, minimum = 7.0)
        }
    }

    @Test
    fun `todos los tonos que llevan texto cumplen AA en los dos temas`() {
        // Dim y Muted estaban por debajo (2,88:1 y 4,14:1) y se usan en etiquetas que
        // llevan informacion real: BAT 63%, RESTANTE, nombres de aplicacion.
        palettes.forEach { (choice, palette) ->
            listOf(
                palette.dim,
                palette.muted,
                palette.secondary,
                palette.tertiary,
                palette.reading,
                palette.foreground,
            ).forEach { assertMinContrast(choice, it, palette.background, minimum = 4.5) }
        }
    }

    @Test
    fun `los tonos de trazo se quedan por debajo a proposito en los dos temas`() {
        // Filetes, bordes y marcas huecas no son texto: si alguno subiera hasta el
        // rango legible, la reticula empezaria a competir con el contenido.
        palettes.forEach { (choice, palette) ->
            listOf(
                palette.hairline,
                palette.border,
                palette.faint,
                palette.disabled,
            ).forEach { tone ->
                val ratio = contrastRatio(tone, palette.background)
                assertTrue(
                    "$choice: $tone deberia quedarse por debajo de 3:1, era %.2f".format(ratio),
                    ratio < 3.0,
                )
            }
        }
    }

    @Test
    fun `el fondo es el tono mas oscuro y la escala sube de forma monotona`() {
        palettes.forEach { (choice, palette) ->
            val background = relativeLuminance(palette.background)
            scaleOf(palette).forEach { tone ->
                assertTrue(
                    "$choice: el fondo debe ser mas oscuro que $tone",
                    relativeLuminance(tone) > background,
                )
            }
            // Y la escala debe subir de forma monotona: sin esto dos tonos podrian
            // intercambiarse y romper la jerarquia sin cambiar ningun test.
            scaleOf(palette).zipWithNext { darker, lighter ->
                assertTrue(
                    "$choice: la escala debe crecer en luminosidad: $darker antes que $lighter",
                    relativeLuminance(darker) < relativeLuminance(lighter),
                )
            }
        }
    }

    @Test
    fun `el tema del sistema separa mas la escala del fondo que el negro`() {
        // Es la diferencia que se pidio: mismo fondo, todo lo de encima mas despegado.
        // Sin esto, un retoque podria dejar los dos temas indistinguibles y la fila de
        // ajustes no cambiaria nada visible.
        scaleOf(ZenPalette.Negro).zip(scaleOf(ZenPalette.Sistema)) { negro, sistema ->
            assertTrue(
                "El tono $sistema del tema Sistema deberia ser mas claro que $negro",
                relativeLuminance(sistema) > relativeLuminance(negro),
            )
        }
    }

    @Test
    fun `el ambar y el rojo no cambian con el tema y cumplen su minimo`() {
        // No son tonos de la escala sino significados —restringida y salir de Zen—, asi
        // que viven fuera de la paleta. Los dos temas comparten fondo negro, que es lo
        // que hace que un solo umbral valga para ambos.
        // Los cuadrados de estado de 6dp no son texto: el umbral aplicable es 3 a 1.
        palettes.forEach { (choice, palette) ->
            assertMinContrast(choice, ZenColors.Accent, palette.background, minimum = 3.0)
            assertMinContrast(choice, ZenColors.Danger, palette.background, minimum = 4.5)
        }
    }

    private fun scaleOf(palette: ZenPalette) = listOf(
        palette.hairline,
        palette.border,
        palette.faint,
        palette.disabled,
        palette.dim,
        palette.muted,
        palette.secondary,
        palette.tertiary,
        palette.reading,
        palette.foreground,
    )

    private fun assertMinContrast(
        choice: ZenThemeChoice,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$choice: contraste %.2f:1 por debajo del minimo %.1f:1".format(ratio, minimum),
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
