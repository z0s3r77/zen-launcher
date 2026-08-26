package com.zenlauncher.zen.domain.usage

/**
 * Cuanto movil llevas hoy, en cuatro escalones.
 *
 * No hay quinto escalon ni porcentajes: la pregunta que se contesta es "¿voy bien o me
 * estoy pasando?", y para eso cuatro palabras bastan. Un numero fino invitaria a
 * optimizarlo, y Zen no quiere que nadie juegue a bajar su marca.
 */
enum class UsageLevel {
    /** Debajo de lo normal. No se pinta nada: no hay nada que decir. */
    CALMA,
    NORMAL,
    ALTA,
    EXCESO,
}

/** La lectura del dia, ya resuelta. Es lo unico que ve la interfaz. */
data class UsageReading(
    val level: UsageLevel,
    val screenMillis: Long,
    val unlocks: Int,
    val topApp: AppUsage?,
    val measured: Boolean,
) {
    /**
     * Si la pantalla de inicio tiene que decir algo.
     *
     * Misma regla que el mando del reproductor: lo que no tiene nada detras no se
     * pinta. Un dia tranquilo deja la home exactamente como estaba.
     *
     * **Lo decide la cara y no el escalon**, y la diferencia se vio en el dispositivo:
     * con dos horas de las que Instagram se llevaba el 77%, el escalon decia NORMAL —no
     * hay pulso— pero la cara ya estaba triste, porque una sola aplicacion acaparando es
     * lo que [UsageMood] mira y el reloj no. Quedaba una franja con `:(` y ni una cifra
     * que lo explicase, y al tocarla se llegaba a una pantalla que ponia NORMAL. El
     * resumen y el detalle tienen que decir lo mismo.
     */
    val worthShowing: Boolean get() = measured && UsageMood.face(this).alarming
}

/**
 * Traduce el dia a un escalon. Funcion pura: se prueba entera sin Android.
 *
 * **Dos varas, y manda la peor.** El tiempo dice cuanto te ha costado el movil; los
 * desbloqueos, cuantas veces lo has necesitado. Coger el telefono ciento veinte veces
 * para mirar nada es una conducta compulsiva aunque el total del dia sea de una hora, y
 * al reves. Quedarse solo con el tiempo dejaria fuera justo el caso que Zen existe para
 * ver.
 *
 * **Los umbrales son del dia entero y no se escalan con la hora.** Se probo compararlos
 * contra la fraccion de jornada transcurrida y el resultado era un aviso a las nueve de
 * la manana por veinte minutos de movil: cualquier rato concentrado disparaba la alarma
 * temprano. Tres horas de pantalla son muchas a las diez de la noche y son muchisimas a
 * mediodia; el escalon no necesita saber la hora para eso.
 */
object UsagePressure {

    /** A partir de aqui, el dia deja de ser tranquilo. */
    const val NORMAL_MINUTES = 60L
    const val HIGH_MINUTES = 150L
    const val EXCESS_MINUTES = 300L

    const val NORMAL_UNLOCKS = 30
    const val HIGH_UNLOCKS = 60
    const val EXCESS_UNLOCKS = 100

    /**
     * El escalon de un dia cualquiera a partir de sus dos cifras.
     *
     * Publico y aparte de [read] porque el resumen de la semana lo aplica sobre la
     * **media diaria**: asi la semana se juzga con los mismos umbrales que el dia y no
     * con una segunda tabla de numeros magicos que se pueda desincronizar de esta.
     */
    fun level(screenMillis: Long, unlocks: Int): UsageLevel =
        maxOf(byTime(screenMillis), byUnlocks(unlocks))

    fun read(snapshot: UsageSnapshot): UsageReading = UsageReading(
        level = if (snapshot.measured) {
            level(snapshot.screenMillis, snapshot.unlocks)
        } else {
            UsageLevel.CALMA
        },
        screenMillis = snapshot.screenMillis,
        unlocks = snapshot.unlocks,
        topApp = snapshot.topApp,
        measured = snapshot.measured,
    )

    private fun byTime(screenMillis: Long): UsageLevel {
        val minutes = screenMillis / 60_000L
        return when {
            minutes >= EXCESS_MINUTES -> UsageLevel.EXCESO
            minutes >= HIGH_MINUTES -> UsageLevel.ALTA
            minutes >= NORMAL_MINUTES -> UsageLevel.NORMAL
            else -> UsageLevel.CALMA
        }
    }

    private fun byUnlocks(unlocks: Int): UsageLevel = when {
        unlocks >= EXCESS_UNLOCKS -> UsageLevel.EXCESO
        unlocks >= HIGH_UNLOCKS -> UsageLevel.ALTA
        unlocks >= NORMAL_UNLOCKS -> UsageLevel.NORMAL
        else -> UsageLevel.CALMA
    }
}
