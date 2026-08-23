package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.notifications.NotificationGrouping
import com.zenlauncher.zen.fakes.appNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGroupingTest {

    private val labels = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.google.android.gm" to "Gmail",
    )

    @Test
    fun `manda el tiempo y no la importancia que se pone la aplicacion`() {
        // El panel de Android ordena por importancia, que la propia aplicacion influye.
        // Aqui quien pueda gritar mas fuerte no sube puestos.
        val groups = NotificationGrouping.group(
            notifications = listOf(
                appNotification("com.google.android.gm", title = "Factura", postTime = 1_000),
                appNotification("com.whatsapp", title = "Ana", postTime = 8_000),
            ),
            labels = labels,
        )

        assertEquals(listOf("WhatsApp", "Gmail"), groups.map { it.label })
    }

    @Test
    fun `una aplicacion restringida no ensena sus avisos`() {
        // Seria la puerta trasera que la restriccion existe para cerrar.
        val groups = NotificationGrouping.group(
            notifications = listOf(appNotification("com.instagram.android", title = "5 me gusta")),
            labels = mapOf("com.instagram.android" to "Instagram"),
            hidden = setOf("com.instagram.android"),
        )

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `sin nombre instalado se cae al paquete antes que dejar un hueco`() {
        val groups = NotificationGrouping.group(
            notifications = listOf(appNotification("com.desconocida")),
            labels = emptyMap(),
        )

        assertEquals("com.desconocida", groups.single().label)
    }

    @Test
    fun `dentro de cada aplicacion tambien manda lo ultimo que llego`() {
        val groups = NotificationGrouping.group(
            notifications = listOf(
                appNotification("com.whatsapp", title = "Ayer", postTime = 1_000),
                appNotification("com.whatsapp", title = "Ahora", postTime = 9_000),
            ),
            labels = labels,
        )

        assertEquals(listOf("Ahora", "Ayer"), groups.single().notifications.map { it.title })
        assertEquals(2, groups.single().count)
    }
}
