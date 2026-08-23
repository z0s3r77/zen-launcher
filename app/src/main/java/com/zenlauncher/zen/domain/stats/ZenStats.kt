package com.zenlauncher.zen.domain.stats

/**
 * Resumen de uso. Deliberadamente sin rachas, sin logros y sin comparativas: son datos
 * para saber si Zen se esta usando, no un marcador.
 */
data class ZenStats(
    val totalZenMillis: Long,
    val completedCount: Int,
    val abandonedCount: Int,
    /** Suma de puntos de bateria, solo de sesiones con medida fiable. */
    val batteryConsumedPercent: Int,
    /** Cuantas sesiones aportaron una medida de bateria utilizable. */
    val batterySampleCount: Int,
    val longestSessionMillis: Long,
    val averageSessionMillis: Long,
    /** 0..100. Cero cuando todavia no hay sesiones. */
    val completionRatePercent: Int,
) {
    val totalCount: Int get() = completedCount + abandonedCount

    val isEmpty: Boolean get() = totalCount == 0

    /** La cifra de bateria solo se muestra si algo la respalda. */
    val hasBatteryData: Boolean get() = batterySampleCount > 0

    companion object {
        val Empty = ZenStats(
            totalZenMillis = 0,
            completedCount = 0,
            abandonedCount = 0,
            batteryConsumedPercent = 0,
            batterySampleCount = 0,
            longestSessionMillis = 0,
            averageSessionMillis = 0,
            completionRatePercent = 0,
        )
    }
}
