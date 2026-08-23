package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.media.NotificationListenerAccess.isGranted
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationListenerAccessTest {

    private val zen = "com.zenlauncher.zen"

    @Test
    fun `sin ajuste no hay acceso`() {
        assertFalse(isGranted(null, zen))
        assertFalse(isGranted("", zen))
    }

    @Test
    fun `reconoce el componente entre varios oyentes concedidos`() {
        val setting = "com.otra/app.Listener:com.zenlauncher.zen/com.zenlauncher.zen.system.ZenNotificationListener"

        assertTrue(isGranted(setting, zen))
    }

    @Test
    fun `se compara por paquete, no por clase`() {
        // El nombre del servicio puede cambiar entre versiones y el sistema conserva la
        // concesion: comparar el componente entero daria un falso negativo.
        assertTrue(isGranted("com.zenlauncher.zen/otra.Clase", zen))
    }

    @Test
    fun `otro paquete con nombre parecido no cuenta`() {
        assertFalse(isGranted("com.zenlauncher.zenith/app.Listener", zen))
    }

    @Test
    fun `las entradas vacias de la lista no rompen la lectura`() {
        assertTrue(isGranted(":com.zenlauncher.zen/app.L::", zen))
        assertFalse(isGranted("::", zen))
    }
}
