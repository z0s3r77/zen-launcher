package com.zenlauncher.zen.domain.battery

/**
 * Si el telefono esta cargando, leido de los extras de `ACTION_BATTERY_CHANGED`.
 *
 * Regresion: se usaba `BatteryManager.isCharging`, que en el dispositivo de pruebas
 * devolvia false con el cable puesto —el porcentaje subia y Zen seguia sin decir
 * CARGANDO—. El estado del difusion es la fuente que usa el propio sistema para pintar
 * el rayo en la barra de estado.
 *
 * Se mira **tambien** el conector: hay ROMs que dejan el estado en NOT_CHARGING mientras
 * gestionan la carga (carga lenta nocturna, tope al 80%), y para el usuario el cable
 * sigue puesto.
 */
object BatteryCharging {

    /** Valores de `BatteryManager.BATTERY_STATUS_*`, sin depender de Android aqui. */
    const val STATUS_CHARGING = 2
    const val STATUS_FULL = 5

    fun isCharging(status: Int, plugged: Int): Boolean =
        status == STATUS_CHARGING || status == STATUS_FULL || plugged != 0
}
