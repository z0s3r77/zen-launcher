package com.zenlauncher.zen.domain.model

/**
 * Sesion en curso. Se persiste **antes** de navegar a la pantalla de sesion, de modo
 * que la fuente de la verdad nunca sea un contador en memoria: sobrevive a la
 * rotacion, a la muerte del proceso y al reinicio del dispositivo.
 */
data class ActiveSession(
    val id: String,
    val startedAtWallMillis: Long,
    val startedAtElapsedMillis: Long,
    val plannedDurationMillis: Long,
    val initialBatteryPercent: Int,
    val initialCharging: Boolean,
    val restrictedAppsCount: Int,
) {
    val plannedEndWallMillis: Long get() = startedAtWallMillis + plannedDurationMillis
}
