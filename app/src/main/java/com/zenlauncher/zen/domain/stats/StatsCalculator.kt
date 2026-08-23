package com.zenlauncher.zen.domain.stats

import com.zenlauncher.zen.domain.model.ZenSession
import kotlin.math.roundToInt

/**
 * Agregacion pura, sin SQL y sin Android.
 *
 * Se calcula en Kotlin en lugar de en consultas porque el volumen es minusculo (unas
 * pocas sesiones al dia) y asi la logica queda cubierta por tests JVM, sin duplicarla
 * entre el motor de almacenamiento y el dominio.
 */
object StatsCalculator {

    fun from(sessions: List<ZenSession>): ZenStats {
        if (sessions.isEmpty()) return ZenStats.Empty

        var totalMillis = 0L
        var completed = 0
        var abandoned = 0
        var batteryTotal = 0
        var batterySamples = 0
        var longest = 0L

        for (session in sessions) {
            val duration = session.actualDurationMillis.coerceAtLeast(0)
            totalMillis += duration
            if (duration > longest) longest = duration
            if (session.completed) completed++ else abandoned++

            // Solo suma cuando la lectura tiene sentido; ver ZenSession.batteryConsumedPercent
            session.batteryConsumedPercent?.let {
                batteryTotal += it
                batterySamples++
            }
        }

        val total = completed + abandoned
        return ZenStats(
            totalZenMillis = totalMillis,
            completedCount = completed,
            abandonedCount = abandoned,
            batteryConsumedPercent = batteryTotal,
            batterySampleCount = batterySamples,
            longestSessionMillis = longest,
            averageSessionMillis = totalMillis / total,
            completionRatePercent = (completed * 100.0 / total).roundToInt(),
        )
    }
}
