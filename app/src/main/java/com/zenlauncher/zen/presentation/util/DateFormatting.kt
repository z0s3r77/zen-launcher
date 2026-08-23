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

    private fun format(epochMillis: Long, formatter: DateTimeFormatter): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
}
