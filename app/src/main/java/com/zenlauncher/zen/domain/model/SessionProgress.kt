package com.zenlauncher.zen.domain.model

import kotlin.math.abs

/** Estado derivado de una [ActiveSession] en un instante concreto. */
data class SessionProgress(
    val elapsedMillis: Long,
    val remainingMillis: Long,
    val isExpired: Boolean,
    /** El reloj de pared se movio durante la sesion; el dato mostrado usa el monotono. */
    val clockAnomaly: Boolean,
)

/**
 * Calculo puro del progreso. Sin Android y sin estado, para poder cubrir con tests
 * los casos que en un dispositivo real son dificiles de reproducir: reinicio a mitad
 * de sesion y cambio manual de la hora.
 */
object SessionProgressCalculator {

    /**
     * Margen que se tolera entre los dos relojes antes de considerar que la hora del
     * sistema se ha manipulado. Cubre el desfase normal por sincronizacion NTP.
     */
    const val CLOCK_DRIFT_TOLERANCE_MILLIS: Long = 60_000

    fun progress(
        session: ActiveSession,
        nowWallMillis: Long,
        nowElapsedMillis: Long,
    ): SessionProgress {
        val byWall = nowWallMillis - session.startedAtWallMillis
        val byElapsed = nowElapsedMillis - session.startedAtElapsedMillis

        // elapsedRealtime se reinicia con el dispositivo: si retrocede, hubo reinicio
        // y el unico reloj con memoria del pasado es el de pared.
        val rebooted = nowElapsedMillis < session.startedAtElapsedMillis

        val elapsed: Long
        val anomaly: Boolean
        if (rebooted) {
            elapsed = byWall
            anomaly = byWall < 0
        } else {
            anomaly = abs(byElapsed - byWall) > CLOCK_DRIFT_TOLERANCE_MILLIS
            // Con ambos relojes disponibles gana el monotono: no se puede manipular.
            elapsed = byElapsed
        }

        val safeElapsed = elapsed.coerceAtLeast(0)
        val remaining = (session.plannedDurationMillis - safeElapsed).coerceAtLeast(0)
        return SessionProgress(
            elapsedMillis = safeElapsed,
            remainingMillis = remaining,
            isExpired = safeElapsed >= session.plannedDurationMillis,
            clockAnomaly = anomaly,
        )
    }
}
