package com.zenlauncher.zen.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Escala de espaciado con base 2dp. Los margenes de pantalla replican la retícula
 * del sistema Industrial (26 / 60 / 44).
 */
object ZenSpacing {
    val Base: Dp = 2.dp

    val XSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 16.dp
    val Large: Dp = 26.dp
    val XLarge: Dp = 34.dp
    val XXLarge: Dp = 46.dp

    /** Margen horizontal de pantalla. */
    val ScreenHorizontal: Dp = 26.dp

    /** Aire bajo la zona de insets superior. */
    val ScreenTop: Dp = 24.dp

    /** Aire sobre la zona de insets inferior. */
    val ScreenBottom: Dp = 20.dp

    /** Alto de fila de lista. Por encima del minimo tactil de 48dp. */
    val Row: Dp = 64.dp

    /** Lado del cuadrado que marca estado. */
    val StatusMark: Dp = 6.dp

    /** Grosor de filete. */
    val Hairline: Dp = 1.dp
}
