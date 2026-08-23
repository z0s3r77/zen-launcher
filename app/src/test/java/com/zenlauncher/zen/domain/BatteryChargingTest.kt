package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.battery.BatteryCharging.isCharging
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryChargingTest {

    private val statusNotCharging = 3
    private val statusDischarging = 4
    private val statusUnknown = 1
    private val pluggedAc = 1
    private val unplugged = 0

    @Test
    fun `el estado de carga del sistema manda`() {
        assertTrue(isCharging(status = 2, plugged = unplugged))
    }

    @Test
    fun `la bateria llena con el cable puesto sigue siendo carga`() {
        // Al 100% el sistema pasa a FULL: decir que no carga con el cable puesto seria
        // mentir, y el rayo de la barra de estado sigue ahi.
        assertTrue(isCharging(status = 5, plugged = pluggedAc))
    }

    @Test
    fun `con el cable puesto cuenta como carga aunque el estado diga que no`() {
        // Regresion del dispositivo: el porcentaje subia y Zen no decia CARGANDO. Hay
        // ROMs que dejan el estado en NOT_CHARGING mientras gestionan la carga (carga
        // lenta nocturna, tope al 80%); para el usuario el cable sigue puesto.
        assertTrue(isCharging(status = statusNotCharging, plugged = pluggedAc))
    }

    @Test
    fun `sin cable y descargando no hay carga`() {
        assertFalse(isCharging(status = statusDischarging, plugged = unplugged))
        assertFalse(isCharging(status = statusUnknown, plugged = unplugged))
        assertFalse(isCharging(status = -1, plugged = unplugged))
    }
}
