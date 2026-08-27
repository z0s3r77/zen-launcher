package com.zenlauncher.zen.presentation.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Formateo con `java.time` y zona del sistema resuelta en cada llamada, para que un
 * cambio de zona horaria mientras la app vive no deje el reloj desfasado.
 */
object ZenDateFormats {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Formateadores ya construidos, por patron e idioma.
     *
     * `DateTimeFormatter.ofPattern` no es barato: analiza el patron y monta un
     * `DateTimeFormatterBuilder` entero. Se construia uno **en cada llamada**, y
     * [shortDate] se llama una vez por fila en las listas de notas y de libros, o sea
     * dentro del cuerpo de un elemento que se desplaza. Aqui se construye uno por patron
     * e idioma y se reutiliza.
     *
     * `ConcurrentHashMap` porque las listas se componen desde el hilo principal pero
     * nada impide que un test o una precomposicion entren desde otro.
     */
    private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

    private fun formatter(pattern: String, locale: Locale): DateTimeFormatter =
        formatters.getOrPut("$pattern|${locale.toLanguageTag()}") {
            DateTimeFormatter.ofPattern(pattern, locale)
        }

    fun time(epochMillis: Long): String = format(epochMillis, timeFormatter)

    /** Fecha larga en el idioma del dispositivo, en mayusculas para la franja tecnica. */
    fun date(epochMillis: Long, locale: Locale): String =
        format(epochMillis, formatter("EEEE d MMMM", locale)).uppercase(locale)

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
        return format(epochMillis, formatter(pattern, locale))
            .uppercase(locale)
            // Algunos idiomas ponen punto al abreviar el mes; en una franja tecnica
            // monoespaciada ese punto es un caracter que no dice nada.
            .replace(".", "")
    }

    /**
     * Una fecha ISO ("2026-08-25") escrita como el resto de fechas cortas de la
     * aplicacion, o null si no es una fecha.
     *
     * Existe para la portada de noticias, que trae **su propia** fecha en el texto del
     * sitio en lugar de una marca de tiempo. Se escribe la que dice la portada y no la
     * del reloj del telefono: si la de hoy no se pudo bajar, lo que se lee es de otro
     * dia y el usuario tiene que verlo.
     */
    fun isoShortDate(iso: String, nowMillis: Long, locale: Locale): String? = try {
        val date = java.time.LocalDate.parse(iso)
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        shortDate(millis, nowMillis, locale)
    } catch (error: java.time.format.DateTimeParseException) {
        // El rotulo del sitio puede cambiar de forma. Se cae a la hora de descarga, que
        // siempre existe, en lugar de ensenar una fecha inventada.
        null
    }

    /**
     * La inicial del dia de la semana, para el pie de la grafica: L M X J V S D.
     *
     * Se saca del idioma del dispositivo y no de una lista escrita a mano: en castellano
     * hay dos dias que empiezan por M y dos por S, y la abreviatura correcta la sabe el
     * sistema.
     */
    fun weekdayInitial(epochMillis: Long, locale: Locale): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .dayOfWeek
            .getDisplayName(java.time.format.TextStyle.NARROW_STANDALONE, locale)
            .uppercase(locale)

    private fun format(epochMillis: Long, formatter: DateTimeFormatter): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
}
