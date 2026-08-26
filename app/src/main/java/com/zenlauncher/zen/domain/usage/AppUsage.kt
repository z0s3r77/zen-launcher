package com.zenlauncher.zen.domain.usage

/**
 * Lo que una aplicacion se ha llevado hoy: cuantas veces se abrio y cuanto tiempo
 * estuvo delante.
 *
 * Las dos cifras cuentan cosas distintas y ninguna sobra: media hora seguida de video
 * y treinta aperturas de diez segundos son el mismo tiempo y no son el mismo problema.
 */
data class AppUsage(
    val packageName: String,
    val openings: Int,
    val foregroundMillis: Long,
)

/**
 * El dia tal y como lo cuenta Android, ya plegado: sin eventos sueltos y sin nada que
 * dependa de la hora a la que se mire.
 *
 * `dayStartMillis` es la medianoche local, no "hace 24 horas": la pregunta que responde
 * es "cuanto llevo hoy", y esa se contesta contra el dia del calendario.
 */
data class UsageSnapshot(
    val dayStartMillis: Long,
    val nowMillis: Long,
    /** Suma del tiempo en primer plano de todas las aplicaciones, Zen aparte. */
    val screenMillis: Long,
    /** Veces que se ha desbloqueado el telefono desde la medianoche. */
    val unlocks: Int,
    /** Ordenadas de mas a menos tiempo. */
    val apps: List<AppUsage>,
    /**
     * Si estos numeros son reales o solo el hueco donde irian.
     *
     * Sin acceso de uso concedido, Zen no puede saber nada: la diferencia entre "hoy no
     * has tocado el movil" y "no puedo verlo" tiene que viajar en el dato, porque una
     * pantalla que ensena un cero que no ha medido esta mintiendo.
     */
    val measured: Boolean = true,
) {
    val topApp: AppUsage? get() = apps.maxByOrNull { it.foregroundMillis }

    companion object {
        /** Sin acceso concedido: no hay cero, hay ausencia de medida. */
        fun unmeasured(nowMillis: Long, dayStartMillis: Long): UsageSnapshot = UsageSnapshot(
            dayStartMillis = dayStartMillis,
            nowMillis = nowMillis,
            screenMillis = 0L,
            unlocks = 0,
            apps = emptyList(),
            measured = false,
        )
    }
}

/**
 * Una apertura concreta, con su hora. Es lo que mira [CompulsionDetector]: el total del
 * dia no distingue entre repartir el movil por la jornada y agotarlo en veinte minutos.
 */
data class AppOpening(
    val packageName: String,
    val atMillis: Long,
    /** Cuanto duro esa vez. Cero si todavia sigue delante. */
    val foregroundMillis: Long,
)
