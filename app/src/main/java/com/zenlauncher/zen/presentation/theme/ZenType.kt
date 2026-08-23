package com.zenlauncher.zen.presentation.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zenlauncher.zen.R

/**
 * Archivo para rotulos, DM Mono para toda cifra y etiqueta tecnica.
 *
 * Archivo se distribuye como fuente variable, asi que los pesos se piden por el eje
 * `wght` en lugar de por ficheros estaticos. Requiere API 26+, cubierto por minSdk 34.
 */
@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int) = Font(
    resId = R.font.archivo_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val ArchivoFamily = FontFamily(
    archivo(400),
    archivo(500),
    archivo(600),
)

val DmMonoFamily = FontFamily(
    Font(R.font.dm_mono_light, FontWeight.Light),
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Estilos con nombre de rol, no de tamano, para que las pantallas no fijen numeros
 * sueltos. Todos heredan del sistema el escalado por `fontScale`.
 */
object ZenTextStyles {
    /** Reloj del launcher. */
    val Clock = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight(500),
        fontSize = 76.sp,
        lineHeight = 66.sp,
        letterSpacing = (-0.045).em,
        lineHeightStyle = TrimBoth,
    )

    /** Cronometro de sesion. Monoespaciado para que las cifras no bailen. */
    val Timer = TextStyle(
        fontFamily = DmMonoFamily,
        fontWeight = FontWeight.Light,
        fontSize = 70.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.04).em,
        lineHeightStyle = TrimBoth,
    )

    /** Cifra de estadistica. */
    val Figure = TextStyle(
        fontFamily = DmMonoFamily,
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.03).em,
    )

    /** Titulo de pantalla. */
    val Title = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight(600),
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.035).em,
    )

    /** Nombre de aplicacion en lista. */
    val ListItem = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight(500),
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.02).em,
    )

    /** Nombre de aplicacion en la reticula de inicio: dos por linea, asi que cabe menos. */
    val Tile = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight(500),
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.02).em,
    )

    /** Texto corrido. */
    val Body = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight(400),
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    /** Etiqueta tecnica de franja: mayusculas, muy espaciada. */
    val MonoLabel = TextStyle(
        fontFamily = DmMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.28.em,
    )

    /** Dato numerico en linea. */
    val MonoData = TextStyle(
        fontFamily = DmMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.16.em,
    )

    /** Indice de fila (01, 02, ...). */
    val MonoIndex = TextStyle(
        fontFamily = DmMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}
