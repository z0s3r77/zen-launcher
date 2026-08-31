package com.zenlauncher.zen.presentation.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * Zen es siempre oscura y siempre monocroma: `isSystemInDarkTheme()` se ignora a
 * proposito, porque el tema claro reintroduciria el brillo que la app intenta quitar.
 * Lo que el usuario elige no es claro contra oscuro, sino cuanto se separan del fondo
 * los grises que hay encima. Ver [ZenPalette].
 */
private fun zenColorScheme(palette: ZenPalette) = darkColorScheme(
    primary = palette.foreground,
    onPrimary = palette.background,
    secondary = palette.secondary,
    onSecondary = palette.background,
    tertiary = palette.tertiary,
    onTertiary = palette.background,
    background = palette.background,
    onBackground = palette.foreground,
    surface = palette.background,
    onSurface = palette.foreground,
    surfaceVariant = palette.hairline,
    onSurfaceVariant = palette.secondary,
    outline = palette.border,
    outlineVariant = palette.hairline,
    // El rojo de error tambien se apaga: un aviso no deberia gritar mas que el resto.
    error = palette.tertiary,
    onError = palette.background,
    errorContainer = palette.hairline,
    onErrorContainer = palette.foreground,
    scrim = palette.background,
)

private val ZenTypography = Typography(
    displayLarge = ZenTextStyles.Clock,
    headlineLarge = ZenTextStyles.Title,
    titleLarge = ZenTextStyles.ListItem,
    bodyLarge = ZenTextStyles.Body,
    bodyMedium = ZenTextStyles.Body,
    labelSmall = ZenTextStyles.MonoLabel,
)

@Composable
fun ZenTheme(
    palette: ZenPalette = ZenPalette.Negro,
    content: @Composable () -> Unit,
) {
    // La paleta se publica DESPUES de componer, no durante.
    //
    // `ZenColors.active` es estado de Compose y casi toda la pantalla lo lee; escribirlo
    // en el cuerpo de este composable invalidaria a los hijos que acaban de leerlo en
    // esta misma pasada, y eso es un bucle de recomposicion. Con `SideEffect` la
    // escritura cae al terminar la composicion y el repintado llega en el fotograma
    // siguiente: cambiar de tema cuesta 16 ms mostrando lo que ya se estaba mirando, que
    // no es un parpadeo.
    //
    // Lo que se le pasa a Material y al estilo de texto sale de `palette` y no de
    // `ZenColors`, que en esta pasada todavia responde con el tema anterior.
    SideEffect { ZenColors.active = palette }

    MaterialTheme(
        colorScheme = remember(palette) { zenColorScheme(palette) },
        typography = ZenTypography,
    ) {
        CompositionLocalProvider(
            // Sin ondas ni destellos al tocar: el feedback es el cambio de estado en si.
            LocalIndication provides NoIndication,
            LocalTextStyle provides ZenTextStyles.Body.copy(color = palette.foreground),
            content = content,
        )
    }
}
