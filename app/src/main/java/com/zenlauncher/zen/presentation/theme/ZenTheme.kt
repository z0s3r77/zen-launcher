package com.zenlauncher.zen.presentation.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Zen es siempre oscura y siempre monocroma: `isSystemInDarkTheme()` se ignora a
 * proposito, porque el tema claro reintroduciria el brillo que la app intenta quitar.
 */
private val ZenColorScheme = darkColorScheme(
    primary = ZenColors.Foreground,
    onPrimary = ZenColors.Background,
    secondary = ZenColors.Secondary,
    onSecondary = ZenColors.Background,
    tertiary = ZenColors.Tertiary,
    onTertiary = ZenColors.Background,
    background = ZenColors.Background,
    onBackground = ZenColors.Foreground,
    surface = ZenColors.Background,
    onSurface = ZenColors.Foreground,
    surfaceVariant = ZenColors.Hairline,
    onSurfaceVariant = ZenColors.Secondary,
    outline = ZenColors.Border,
    outlineVariant = ZenColors.Hairline,
    // El rojo de error tambien se apaga: un aviso no deberia gritar mas que el resto.
    error = ZenColors.Tertiary,
    onError = ZenColors.Background,
    errorContainer = ZenColors.Hairline,
    onErrorContainer = ZenColors.Foreground,
    scrim = ZenColors.Background,
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
fun ZenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZenColorScheme,
        typography = ZenTypography,
    ) {
        CompositionLocalProvider(
            // Sin ondas ni destellos al tocar: el feedback es el cambio de estado en si.
            LocalIndication provides NoIndication,
            LocalTextStyle provides ZenTextStyles.Body.copy(color = ZenColors.Foreground),
            content = content,
        )
    }
}

