package com.zenlauncher.zen.domain.battery

import kotlinx.coroutines.flow.Flow

/** Lectura instantanea de bateria. */
data class BatteryStatus(
    val percent: Int,
    val charging: Boolean,
) {
    companion object {
        /** Valor de reserva cuando el sistema no responde; se marca como no fiable. */
        val Unknown = BatteryStatus(percent = -1, charging = false)
    }

    val isKnown: Boolean get() = percent in 0..100
}

/**
 * `BatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)` e `isCharging()` son
 * publicas y no requieren ningun permiso.
 */
interface BatteryReader {
    fun current(): BatteryStatus

    /** Se reemite al cambiar el nivel o el estado de carga. */
    fun observe(): Flow<BatteryStatus>
}
