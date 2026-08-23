package com.zenlauncher.zen.domain.model

/**
 * Duracion planificada de una sesion. Value class para que no se confunda con
 * cualquier otro Long de milisegundos que circule por el dominio.
 */
@JvmInline
value class ZenDuration(val millis: Long) {

    val wholeMinutes: Int get() = (millis / MILLIS_PER_MINUTE).toInt()

    init {
        require(millis in MIN_MILLIS..MAX_MILLIS) {
            "Duracion fuera de rango: $millis ms"
        }
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L

        /** Un minuto es el minimo util y ademas hace comodo probar en el dispositivo. */
        val MIN_MINUTES = 1
        val MAX_MINUTES = 12 * 60

        val MIN_MILLIS = MIN_MINUTES * MILLIS_PER_MINUTE
        val MAX_MILLIS = MAX_MINUTES * MILLIS_PER_MINUTE

        val Presets: List<ZenDuration> = listOf(15, 30, 60, 90, 120).map { ofMinutes(it) }

        val Default: ZenDuration = ofMinutes(30)

        fun ofMinutes(minutes: Int): ZenDuration = ZenDuration(minutes * MILLIS_PER_MINUTE)

        /** Devuelve null en lugar de lanzar: la entrada viene de un campo de texto. */
        fun ofMinutesOrNull(minutes: Int?): ZenDuration? {
            if (minutes == null || minutes < MIN_MINUTES || minutes > MAX_MINUTES) return null
            return ofMinutes(minutes)
        }
    }
}

/** Formato mm:ss para el cronometro; hh:mm:ss solo cuando hace falta. */
fun formatDurationClock(millis: Long): String {
    val safe = millis.coerceAtLeast(0)
    val totalSeconds = safe / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * Formato "4h 32m" para estadisticas.
 *
 * Por debajo del minuto baja a segundos: una media de 48 s mostrada como "0m" parece
 * un error de la aplicacion, no un dato.
 */
fun formatDurationCompact(millis: Long): String {
    val safe = millis.coerceAtLeast(0)
    val totalSeconds = safe / 1000
    if (totalSeconds < 60) return "${totalSeconds}s"
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
