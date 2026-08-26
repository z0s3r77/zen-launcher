package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.usage.AppOpening
import com.zenlauncher.zen.domain.usage.CompulsionDetector
import com.zenlauncher.zen.domain.usage.CompulsionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompulsionDetectorTest {

    private val ahora = 10_000_000L

    private fun minutos(value: Int) = value * 60_000L

    private fun apertura(
        packageName: String,
        haceMinutos: Int,
        duraMinutos: Int = 1,
    ) = AppOpening(
        packageName = packageName,
        atMillis = ahora - minutos(haceMinutos),
        foregroundMillis = minutos(duraMinutos),
    )

    @Test
    fun `un rato tranquilo no dispara nada`() {
        val openings = listOf(
            apertura("com.whatsapp", haceMinutos = 40, duraMinutos = 3),
            apertura("com.instagram.android", haceMinutos = 12, duraMinutos = 4),
        )

        assertNull(CompulsionDetector.detect(openings, ahora))
    }

    @Test
    fun `una sentada larga es un arrastre`() {
        val openings = listOf(apertura("com.instagram.android", haceMinutos = 50, duraMinutos = 48))

        val compulsion = CompulsionDetector.detect(openings, ahora)

        assertEquals(CompulsionKind.ARRASTRE, compulsion?.kind)
        assertEquals("com.instagram.android", compulsion?.packageName)
        assertEquals(minutos(48), compulsion?.foregroundMillis)
    }

    /**
     * Regresion: el arrastre se filtraba por cuando **empezaba** la sentada, asi que una
     * que arranco hace hora y media y sigue viva quedaba fuera de la ventana de una hora
     * —justo el caso que hay que cazar—. Ahora se filtra por cuando acaba.
     */
    @Test
    fun `un arrastre que empezo antes de la ventana pero sigue vivo cuenta`() {
        val openings = listOf(
            AppOpening("com.tiktok", atMillis = ahora - minutos(95), foregroundMillis = minutos(95)),
        )

        assertEquals(CompulsionKind.ARRASTRE, CompulsionDetector.detect(openings, ahora)?.kind)
    }

    @Test
    fun `abrir lo mismo una y otra vez es repeticion`() {
        val openings = (1..6).map { apertura("com.instagram.android", haceMinutos = it * 4) }

        val compulsion = CompulsionDetector.detect(openings, ahora)

        assertEquals(CompulsionKind.REPETICION, compulsion?.kind)
        assertEquals(6, compulsion?.openings)
        assertEquals(CompulsionDetector.REPEAT_WINDOW_MINUTES, compulsion?.windowMinutes)
    }

    @Test
    fun `las aperturas fuera de la ventana no cuentan para la repeticion`() {
        // Seis aperturas, pero repartidas en hora y media: eso es usar el telefono, no
        // una recaida. Solo tres caen dentro de la media hora.
        val openings = (1..6).map { apertura("com.instagram.android", haceMinutos = it * 15) }

        assertNull(CompulsionDetector.detect(openings, ahora))
    }

    @Test
    fun `saltar de aplicacion en aplicacion es picoteo`() {
        val openings = (1..14).map { apertura("com.app$it", haceMinutos = it % 14) }

        val compulsion = CompulsionDetector.detect(openings, ahora)

        assertEquals(CompulsionKind.PICOTEO, compulsion?.kind)
        // En picoteo no sobra una aplicacion, sobra el salto: senalar a una seria
        // senalar a la equivocada.
        assertNull(compulsion?.packageName)
        assertEquals(14, compulsion?.openings)
    }

    /**
     * Regresion: una aplicacion exenta abierta doce veces se salia por la puerta del
     * arrastre y de la repeticion, pero se colaba por la de picoteo como si fueran doce
     * aplicaciones distintas. Picotear exige mas de una.
     */
    @Test
    fun `abrir doce veces la misma aplicacion exenta no es picoteo`() {
        val openings = (1..13).map { apertura("com.google.android.dialer", haceMinutos = it % 13) }

        assertNull(
            CompulsionDetector.detect(
                openings,
                ahora,
                exempt = setOf("com.google.android.dialer"),
            ),
        )
    }

    @Test
    fun `una aplicacion exenta no genera arrastre`() {
        // Cincuenta minutos de navegador GPS conduciendo son tiempo de pantalla y no son
        // una recaida.
        val openings = listOf(apertura("com.google.android.apps.maps", haceMinutos = 50, duraMinutos = 50))

        assertNull(
            CompulsionDetector.detect(
                openings,
                ahora,
                exempt = setOf("com.google.android.apps.maps"),
            ),
        )
    }

    @Test
    fun `el arrastre manda sobre la repeticion`() {
        val openings = (1..6).map { apertura("com.instagram.android", haceMinutos = it * 4) } +
            apertura("com.tiktok", haceMinutos = 55, duraMinutos = 45)

        assertEquals(CompulsionKind.ARRASTRE, CompulsionDetector.detect(openings, ahora)?.kind)
    }

    /**
     * Regresion vista en el dispositivo: el aviso salto con "12 APERTURAS · 1m", que se
     * lee solo como lo que era. Doce visitas que suman un minuto son avisos, consultas
     * de un segundo y transiciones del sistema; picotear es saltar **y quedarse un rato
     * en total**.
     */
    @Test
    fun `doce visitas que suman un minuto no son picoteo`() {
        val openings = (1..13).map {
            AppOpening("com.app$it", atMillis = ahora - minutos(it % 13), foregroundMillis = 5_000L)
        }

        assertNull(CompulsionDetector.detect(openings, ahora))
    }

    @Test
    fun `doce visitas con tiempo de verdad si son picoteo`() {
        val openings = (1..13).map {
            AppOpening("com.app$it", atMillis = ahora - minutos(it % 13), foregroundMillis = minutos(1))
        }

        assertEquals(CompulsionKind.PICOTEO, CompulsionDetector.detect(openings, ahora)?.kind)
    }
}
