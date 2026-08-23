package com.zenlauncher.zen.domain.model

/** Sesion ya terminada, tal y como queda registrada. */
data class ZenSession(
    val id: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val plannedDurationMillis: Long,
    val actualDurationMillis: Long,
    val initialBatteryPercent: Int,
    val finalBatteryPercent: Int,
    val initialCharging: Boolean,
    val finalCharging: Boolean,
    val outcome: SessionOutcome,
    val restrictedAppsCount: Int,
) {
    val completed: Boolean get() = outcome == SessionOutcome.COMPLETED

    /**
     * Puntos de bateria consumidos, o null si la medida no significa nada.
     *
     * Se descarta en tres casos: si alguna de las dos lecturas fallo (el sistema
     * devuelve un porcentaje fuera de 0..100), si el dispositivo estuvo cargando en
     * alguno de los dos extremos, o si el porcentaje final es mayor que el inicial.
     * No es una medicion energetica, solo la diferencia de dos lecturas del sistema.
     */
    val batteryConsumedPercent: Int?
        get() {
            // Sin lectura valida no hay medida: devolver 0 aqui haria pasar por
            // "no consumio nada" lo que en realidad es "no se pudo medir".
            if (initialBatteryPercent !in 0..100 || finalBatteryPercent !in 0..100) return null
            if (initialCharging || finalCharging) return null
            val delta = initialBatteryPercent - finalBatteryPercent
            return if (delta < 0) null else delta
        }
}
