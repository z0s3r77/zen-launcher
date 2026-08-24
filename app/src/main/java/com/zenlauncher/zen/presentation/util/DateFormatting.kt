package com.zenlauncher.zen.presentation.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formateo con `java.time` y zona del sistema resuelta en cada llamada, para que un
 * cambio de zona horaria mientras la app vive no deje el reloj desfasado.
 */
object ZenDateFormats {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun time(epochMillis: Long): String = format(epochMillis, timeFormatter)

    /** Fecha larga en el idioma del dispositivo, en mayusculas para la franja tecnica. */
    fun date(epochMillis: Long, locale: Locale): String {
        val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
        return format(epochMillis, formatter).uppercase(locale)
    }

    /**
     * Fecha corta para una lista: `12 MAR`, o `12 MAR 24` si es de otro ano.
     *
     * El ano solo aparece cuando hace falta. Una lista de notas donde todas repiten el
     * ano en curso gasta ancho en un dato que no distingue ninguna fila de otra; en
     * cambio, encontrarse una idea de hace dos anos **es** la informacion.
     */
    fun shortDate(epochMillis: Long, nowMillis: Long, locale: Locale): String {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val pattern = if (date.year == now.year) "d MMM" else "d MMM yy"
        return format(epochMillis, DateTimeFormatter.ofPattern(pattern, locale))
            .uppercase(locale)
            // Algunos idiomas ponen punto al abreviar el mes; en una franja tecnica
            // monoespaciada ese punto es un caracter que no dice nada.
            .replace(".", "")
    }

    private fun format(epochMillis: Long, formatter: DateTimeFormatter): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
}
