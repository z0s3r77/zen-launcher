package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.apps.EssentialApps
import com.zenlauncher.zen.fakes.installedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EssentialAppsTest {

    @Test
    fun `las esenciales salen en el orden de la pantalla, no en el de la lista recibida`() {
        val installed = listOf(
            installedApp("com.spotify.music", "Spotify"),
            installedApp("com.whatsapp", "WhatsApp"),
            installedApp("com.google.android.googlequicksearchbox", "Google"),
        )

        val resolved = EssentialApps.resolve(installed).map { it.label }

        assertEquals(listOf("Google", "WhatsApp", "Spotify"), resolved)
    }

    @Test
    fun `un hueco se resuelve con el primer candidato instalado`() {
        // El marcador cambia de paquete segun la ROM: si esta el de Google, gana ese;
        // si no, vale el de AOSP.
        val soloAosp = EssentialApps.resolve(listOf(installedApp("com.android.dialer", "Teléfono")))
        assertEquals(listOf("Teléfono"), soloAosp.map { it.label })

        val ambos = EssentialApps.resolve(
            listOf(
                installedApp("com.android.dialer", "Teléfono AOSP"),
                installedApp("com.google.android.dialer", "Teléfono"),
            ),
        )
        assertEquals(listOf("Teléfono"), ambos.map { it.label })
    }

    @Test
    fun `un hueco sin ningun candidato instalado no aparece`() {
        // Regresion: una entrada que no se puede abrir es peor que no tener entrada.
        val resolved = EssentialApps.resolve(listOf(installedApp("com.whatsapp", "WhatsApp")))

        assertEquals(listOf("WhatsApp"), resolved.map { it.label })
    }

    @Test
    fun `una aplicacion que cubre dos huecos aparece una sola vez`() {
        val messagesEverywhere = listOf(
            installedApp("com.google.android.apps.messaging", "Mensajes"),
            installedApp("com.whatsapp", "WhatsApp"),
        )

        val resolved = EssentialApps.resolve(messagesEverywhere + messagesEverywhere)

        assertEquals(listOf("WhatsApp", "Mensajes"), resolved.map { it.label })
    }

    @Test
    fun `sin nada instalado no se inventa ninguna aplicacion`() {
        assertTrue(EssentialApps.resolve(emptyList()).isEmpty())
    }

    @Test
    fun `los ocho huecos que pidio el usuario siguen estando`() {
        assertEquals(
            listOf(
                "buscar", "whatsapp", "telefono", "reloj",
                "ajustes", "mensajes", "musica", "banco",
            ),
            EssentialApps.slots.map { it.id },
        )
    }
}
