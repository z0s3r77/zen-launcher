package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.media.MediaSessionRanking
import com.zenlauncher.zen.domain.media.PlaybackKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSessionRankingTest {

    private data class Session(
        val name: String,
        val kind: PlaybackKind,
        val lastUpdate: Long = 0L,
    )

    private fun pick(vararg sessions: Session) = MediaSessionRanking.pick(
        candidates = sessions.toList(),
        kind = Session::kind,
        lastUpdate = Session::lastUpdate,
    )

    @Test
    fun `una sesion inactiva no tapa al reproductor pausado`() {
        // Regresion exacta del dispositivo: la grabadora de Nothing registra un
        // reproductor en estado NONE y salia "News Reporter - Intro" en lugar de la
        // cancion de Spotify, que estaba pausada.
        val elegida = pick(
            Session("grabadora", PlaybackKind.INACTIVE, lastUpdate = 900),
            Session("spotify", PlaybackKind.PAUSED, lastUpdate = 100),
        )

        assertEquals("spotify", elegida?.name)
    }

    @Test
    fun `la que suena gana a la que esta en pausa`() {
        val elegida = pick(
            Session("spotify", PlaybackKind.PAUSED, lastUpdate = 900),
            Session("navegador", PlaybackKind.PLAYING, lastUpdate = 100),
        )

        assertEquals("navegador", elegida?.name)
    }

    @Test
    fun `entre dos en el mismo estado gana la que se movio mas tarde`() {
        val elegida = pick(
            Session("vieja", PlaybackKind.PAUSED, lastUpdate = 100),
            Session("reciente", PlaybackKind.PAUSED, lastUpdate = 900),
        )

        assertEquals("reciente", elegida?.name)
    }

    @Test
    fun `si no hay ninguna sesion utilizable no se ensena nada`() {
        assertNull(pick(Session("grabadora", PlaybackKind.INACTIVE)))
        assertNull(pick())
    }
}
