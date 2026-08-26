package com.zenlauncher.zen.presentation.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.weather.WeatherCondition
import com.zenlauncher.zen.domain.weather.WeatherReading

/** Que dice el glifo del tiempo, en palabras. Ver [WeatherCondition]. */
@StringRes
internal fun weatherConditionLabel(condition: WeatherCondition): Int = when (condition) {
    WeatherCondition.DESPEJADO -> R.string.weather_despejado
    WeatherCondition.NUBES_CLAROS -> R.string.weather_nubes_claros
    WeatherCondition.NUBLADO -> R.string.weather_nublado
    WeatherCondition.LLUVIA -> R.string.weather_lluvia
    WeatherCondition.TORMENTA -> R.string.weather_tormenta
    WeatherCondition.NIEVE -> R.string.weather_nieve
    WeatherCondition.NIEBLA -> R.string.weather_niebla
}

/**
 * El tiempo tal y como se lee en la franja: el glifo del cielo y los grados.
 *
 * Sin cielo reconocido quedan los grados solos. Es la mitad del dato, pero es la mitad
 * que responde a la pregunta con la que se mira la pantalla de inicio antes de salir.
 */
@Composable
internal fun weatherGlyph(reading: WeatherReading): String {
    val degrees = stringResource(R.string.weather_degrees, reading.degrees)
    val condition = reading.condition ?: return degrees
    return "${condition.glyph} $degrees"
}

/**
 * El tiempo en voz alta.
 *
 * El glifo es texto, pero `-O-` no se puede leer; misma razon que la cara del dia (ver
 * `usageFaceDescription`). Dice ademas a donde lleva el toque, porque lleva a algun
 * sitio: a la pantalla del tiempo, con la ciudad y la hora de la lectura.
 */
@Composable
internal fun weatherDescription(reading: WeatherReading): String {
    val condition = reading.condition ?: return pluralStringResource(
        R.plurals.weather_description_plain,
        reading.degrees,
        reading.degrees,
    )
    return pluralStringResource(
        R.plurals.weather_description,
        reading.degrees,
        reading.degrees,
        stringResource(weatherConditionLabel(condition)),
    )
}
