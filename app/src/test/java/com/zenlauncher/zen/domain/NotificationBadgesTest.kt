package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.notifications.NotificationBadges
import com.zenlauncher.zen.fakes.appNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBadgesTest {

    @Test
    fun `cuenta por aplicacion los avisos pendientes`() {
        val counts = NotificationBadges.countByPackage(
            listOf(
                appNotification("com.whatsapp", title = "Ana"),
                appNotification("com.whatsapp", title = "Luis"),
                appNotification("com.google.android.gm", title = "Factura"),
            ),
        )

        assertEquals(mapOf("com.whatsapp" to 2, "com.google.android.gm" to 1), counts)
    }

    @Test
    fun `el reproductor sonando no es un aviso pendiente`() {
        // Regresion: contarlo todo daba un "1" permanente en Spotify por estar sonando,
        // que es justo el ruido que Zen quita.
        val counts = NotificationBadges.countByPackage(
            listOf(appNotification("com.spotify.music", title = "Nosotros", ongoing = true)),
        )

        assertTrue(counts.isEmpty())
    }

    @Test
    fun `la cabecera de un grupo no se cuenta dos veces`() {
        // Android publica un resumen ademas de cada mensaje: sumarlo daria tres avisos
        // donde el usuario ve dos.
        val counts = NotificationBadges.countByPackage(
            listOf(
                appNotification("com.whatsapp", title = "Ana"),
                appNotification("com.whatsapp", title = "Luis"),
                appNotification("com.whatsapp", title = "2 mensajes", groupSummary = true),
            ),
        )

        assertEquals(mapOf("com.whatsapp" to 2), counts)
    }

    @Test
    fun `un aviso sin titulo ni texto no suma`() {
        assertFalse(
            NotificationBadges.countable(
                appNotification("com.a", title = "", text = ""),
            ),
        )
    }

    @Test
    fun `los avisos de una aplicacion llegan del mas reciente al mas antiguo`() {
        val viejo = appNotification("com.whatsapp", title = "Ayer", postTime = 1_000)
        val nuevo = appNotification("com.whatsapp", title = "Ahora", postTime = 9_000)

        val ordered = NotificationBadges.forPackage(
            listOf(viejo, nuevo, appNotification("com.otra", postTime = 5_000)),
            packageName = "com.whatsapp",
        )

        assertEquals(listOf("Ahora", "Ayer"), ordered.map { it.title })
    }
}
