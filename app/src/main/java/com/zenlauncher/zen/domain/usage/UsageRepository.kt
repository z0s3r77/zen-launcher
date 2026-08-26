package com.zenlauncher.zen.domain.usage

import android.content.Intent

/**
 * Lo que Android sabe del uso del telefono.
 *
 * Igual que el oyente de notificaciones, **la concesion la da el usuario a mano** en
 * Ajustes de Android (acceso de uso) y Zen funciona entera sin ella: sin acceso no hay
 * medida, y sin medida no se pinta ni el pulso de la home ni el aviso. No se insiste,
 * no se bloquea nada y no hay un cero fingido; ver [UsageSnapshot.measured].
 *
 * El [Intent] vive en la interfaz por la misma razon que en
 * `NotificationsRepository`: la pantalla que ofrece conceder el acceso no tiene por que
 * saber en que ajuste del sistema vive.
 */
interface UsageRepository {

    fun hasAccess(): Boolean

    /** Abre la pantalla del sistema donde se concede y se revoca. */
    fun accessIntent(): Intent

    /** Desde la medianoche local hasta [nowMillis]. */
    suspend fun today(nowMillis: Long): UsageSnapshot

    /**
     * Los ultimos [days] dias, del mas antiguo al mas reciente y con hoy al final.
     *
     * Es [days] consultas, una por dia, y por eso **no se pide al volver a la pantalla
     * de inicio**: solo cuando alguien abre la pantalla de la semana a proposito. Los
     * dias de los que el sistema ya no guarda eventos vuelven sin medir, no a cero; ver
     * [WeeklyUsage].
     */
    suspend fun week(nowMillis: Long, days: Int): WeeklyUsage

    /**
     * Las aperturas del ultimo rato, para el detector de conductas.
     *
     * Se pide aparte del dia entero porque son dos preguntas distintas y la ventana
     * corta se consulta mucho mas a menudo: leer el dia completo para mirar los ultimos
     * quince minutos seria recorrer miles de eventos cada vez que se vuelve a la home.
     */
    suspend fun recentOpenings(windowMillis: Long, nowMillis: Long): List<AppOpening>
}
