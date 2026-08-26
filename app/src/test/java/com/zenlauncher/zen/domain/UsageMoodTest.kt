package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.usage.AppUsage
import com.zenlauncher.zen.domain.usage.UsageFace
import com.zenlauncher.zen.domain.usage.UsageLevel
import com.zenlauncher.zen.domain.usage.UsageMood
import com.zenlauncher.zen.domain.usage.UsageReading
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageMoodTest {

    private fun minutos(value: Long) = value * 60_000L

    private fun lectura(
        level: UsageLevel,
        minutes: Long = 30,
        topApp: AppUsage? = null,
        measured: Boolean = true,
    ) = UsageReading(
        level = level,
        screenMillis = minutos(minutes),
        unlocks = 10,
        topApp = topApp,
        measured = measured,
    )

    @Test
    fun `un dia tranquilo y repartido sonrie`() {
        assertEquals(UsageFace.BIEN, UsageMood.face(lectura(UsageLevel.CALMA)))
    }

    @Test
    fun `cada escalon tiene su cara`() {
        assertEquals(UsageFace.REGULAR, UsageMood.face(lectura(UsageLevel.NORMAL, minutes = 90)))
        assertEquals(UsageFace.MAL, UsageMood.face(lectura(UsageLevel.ALTA, minutes = 200)))
        assertEquals(UsageFace.ALARMA, UsageMood.face(lectura(UsageLevel.EXCESO, minutes = 400)))
    }

    /**
     * La tercera vara, la que no da el escalon por tiempo: dos horas repartidas entre
     * correo, banco y mensajes no son dos horas en una sola aplicacion hecha para que te
     * quedes, y el reloj dice lo mismo en los dos casos.
     */
    @Test
    fun `una aplicacion que acapara empeora la cara aunque el reloj vaya normal`() {
        val repartido = lectura(
            UsageLevel.NORMAL,
            minutes = 100,
            topApp = AppUsage("com.whatsapp", 20, minutos(30)),
        )
        val acaparado = lectura(
            UsageLevel.NORMAL,
            minutes = 100,
            topApp = AppUsage("com.instagram.android", 20, minutos(80)),
        )

        assertEquals(UsageFace.REGULAR, UsageMood.face(repartido))
        assertEquals(UsageFace.MAL, UsageMood.face(acaparado))
    }

    @Test
    fun `acaparar tambien empeora un dia en calma`() {
        val lectura = lectura(
            UsageLevel.CALMA,
            minutes = 50,
            topApp = AppUsage("com.instagram.android", 5, minutos(46)),
        )

        assertEquals(UsageFace.REGULAR, UsageMood.face(lectura))
    }

    /**
     * No se acusa al 80% de veinte minutos: acaparar exige porcentaje **y** tiempo de
     * verdad. Sin el segundo, abrir una sola aplicacion diez minutos en todo el dia
     * salia como problema.
     */
    @Test
    fun `llevarse casi todo de muy poco no es acaparar`() {
        val lectura = lectura(
            UsageLevel.CALMA,
            minutes = 20,
            topApp = AppUsage("com.instagram.android", 2, minutos(19)),
        )

        assertEquals(UsageFace.BIEN, UsageMood.face(lectura))
    }

    /**
     * Poner `:)` sin haber medido nada seria felicitar por un dia que no ha ocurrido, y
     * es justo la mentira que el resto de la pantalla evita.
     */
    @Test
    fun `sin medida la cara no opina`() {
        assertEquals(
            UsageFace.DESCONOCIDO,
            UsageMood.face(lectura(UsageLevel.CALMA, measured = false)),
        )
    }

    @Test
    fun `sin tiempo de pantalla no se divide entre cero`() {
        val lectura = lectura(
            UsageLevel.CALMA,
            minutes = 0,
            topApp = AppUsage("com.instagram.android", 1, minutos(50)),
        )

        assertEquals(UsageFace.BIEN, UsageMood.face(lectura))
    }

    @Test
    fun `cada cara tiene su glifo`() {
        assertEquals(":)", UsageFace.BIEN.glyph)
        assertEquals(":|", UsageFace.REGULAR.glyph)
        assertEquals(":(", UsageFace.MAL.glyph)
        assertEquals(":O", UsageFace.ALARMA.glyph)
        assertEquals(":?", UsageFace.DESCONOCIDO.glyph)
    }
}
