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

/**
 * La serif del sistema, para el texto de los libros.
 *
 * **Es la unica tipografia de Zen que no se empaqueta**, y es deliberado por dos
 * razones. La primera, el peso: Archivo ya son 643 KB y una serif con sus versalitas y
 * su cursiva costaria otro tanto dentro del APK del launcher. La segunda, que aqui la
 * eleccion no es de marca sino de oficio —una serif con remates para media hora de
 * prosa—, y la que trae Android (Noto Serif) hace ese trabajo perfectamente y ya esta
 * cargada en memoria por el sistema.
 *
 * Ni Archivo ni DM Mono valen para esto: la primera es una grotesca de rotulo y la
 * segunda es monoespaciada. Es el unico sitio de la aplicacion donde entra una tercera
 * familia, y entra porque el trabajo es otro.
 */
val ReadingSerifFamily = FontFamily.Serif

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

    /**
     * Titular de noticia en una lista.
     *
     * Mide lo mismo que [Tile] pero con mas interlineado, y por eso no lo reusa: una
     * celda de la reticula es una palabra y un titular son tres lineas de texto. Con el
     * interlineado apretado de la reticula, tres lineas seguidas se leen como un bloque.
     */
    val Headline = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight(500),
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.02).em,
    )

    /** Texto corrido. */
    val Body = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight(400),
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    /**
     * Texto corrido de un libro. Es la **base**, no el estilo final.
     *
     * El cuerpo y el interlineado de verdad los pone el usuario con los controles del
     * lector, asi que se derivan de aqui en `readingBodyStyle`: estos numeros son el
     * escalon central de esa escala, no una medida fija. Ver
     * [com.zenlauncher.zen.domain.reading.ReadingSettings].
     *
     * `letterSpacing` a cero, al contrario que el resto de la aplicacion: el espaciado
     * negativo de los rotulos aprieta las palabras para que un nombre largo quepa en una
     * celda, y en un parrafo de doce lineas eso mismo cansa la vista.
     */
    val Reading = TextStyle(
        fontFamily = ReadingSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 31.sp,
    )

    /**
     * Titulo de capitulo dentro del lector.
     *
     * En la serif del texto y no en Archivo: un titulo en la tipografia del launcher
     * dentro de una pagina de libro se lee como un rotulo de la aplicacion metido en
     * medio, no como una parte del libro.
     */
    val ReadingHeading = TextStyle(
        fontFamily = ReadingSerifFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.01).em,
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
