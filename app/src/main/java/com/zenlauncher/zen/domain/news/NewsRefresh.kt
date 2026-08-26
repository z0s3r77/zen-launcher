package com.zenlauncher.zen.domain.news

import java.time.Instant
import java.time.ZoneId

/**
 * Cuando hay que volver a bajar la portada. Funciones puras.
 *
 * **Una vez al dia y ni una mas.** Un periodico tiene una edicion diaria: bajarla dos
 * veces el mismo dia es gastar la red del usuario para traer lo mismo. Aqui la regla es
 * mas estricta que en el tiempo (media hora entre peticiones) porque el dato tambien lo
 * es: la temperatura cambia durante el dia y la portada de hoy no.
 *
 * El corte es **el dia natural**, no veinticuatro horas desde la ultima descarga. Con
 * un intervalo de 24 h, quien mira las noticias a las once de la noche se queda sin la
 * edicion nueva hasta las once del dia siguiente: leeria la portada de ayer toda la
 * manana. Cambiar de dia es exactamente el momento en que hay algo nuevo que traer.
 */
object NewsRefresh {

    /** Si lo guardado es de hoy, no se toca la red. */
    fun isFromToday(
        fetchedAtMillis: Long,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean = day(fetchedAtMillis, zone) == day(nowMillis, zone)

    /**
     * @param edition lo que hay guardado, o null si nunca se bajo nada.
     *
     * Sin edicion guardada se baja; con una de hoy no; con una de ayer si. El reloj de
     * pared puede ir hacia atras (cambio de hora, ajuste por red) y entonces el dia no
     * coincide y se vuelve a bajar: preferible a quedarse con una portada vieja para
     * siempre.
     */
    fun shouldDownload(
        edition: NewsEdition?,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean = edition == null || !isFromToday(edition.fetchedAtMillis, nowMillis, zone)

    private fun day(millis: Long, zone: ZoneId) =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
