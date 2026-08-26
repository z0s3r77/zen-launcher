package com.zenlauncher.zen.presentation.reading

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zenlauncher.zen.domain.reading.ReadingSettings
import com.zenlauncher.zen.presentation.theme.ArchivoFamily
import com.zenlauncher.zen.presentation.theme.ReadingSerifFamily
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

/**
 * Los estilos del lector, derivados de los ajustes del usuario.
 *
 * Aqui **si** se calculan medidas, y es la unica excepcion a "no fijes numeros sueltos
 * en una pantalla": el tamano de letra es precisamente lo que el usuario esta ajustando,
 * asi que no puede ser una constante del tema. Lo que si sale del tema es todo lo
 * demas —la familia, el peso, el punto de partida— y lo que sale del dominio son los
 * escalones. Esto solo los junta.
 */
fun readingBodyStyle(settings: ReadingSettings): TextStyle =
    ZenTextStyles.Reading.copy(
        fontFamily = if (settings.serif) ReadingSerifFamily else ArchivoFamily,
        fontSize = settings.fontSizeSp.sp,
        // El interlineado es un multiplo del cuerpo y no un valor propio: ver
        // `ReadingSettings.lineHeightRatio`.
        lineHeight = (settings.fontSizeSp * settings.lineHeightRatio).sp,
    )

/**
 * El titulo de un capitulo o de una seccion.
 *
 * Crece con el cuerpo en lugar de tener su propia escala: quien sube la letra porque no
 * ve bien tiene que ver mejor tambien los titulos, y una escala fija los dejaria
 * pequenos justo cuando mas falta hacen.
 */
fun readingHeadingStyle(settings: ReadingSettings, level: Int): TextStyle {
    val ratio = when (level) {
        1 -> LEVEL_1
        2 -> LEVEL_2
        else -> LEVEL_3
    }
    return ZenTextStyles.ReadingHeading.copy(
        fontFamily = if (settings.serif) ReadingSerifFamily else ArchivoFamily,
        // Las subsecciones no crecen mucho pero si engordan: sin peso, un titulo de
        // nivel 3 al mismo tamano que el texto no se distingue de un parrafo corto.
        fontWeight = if (level >= 3) FontWeight.Medium else ZenTextStyles.ReadingHeading.fontWeight,
        fontSize = (settings.fontSizeSp * ratio).sp,
        lineHeight = (settings.fontSizeSp * ratio * HEADING_LEADING).sp,
    )
}

private const val LEVEL_1 = 1.40f
private const val LEVEL_2 = 1.18f
private const val LEVEL_3 = 1.05f

/** Los titulos van mas apretados que el texto: son una o dos lineas, no doce. */
private const val HEADING_LEADING = 1.25f
