package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.usage.AppUsage
import com.zenlauncher.zen.domain.usage.UsageLevel
import com.zenlauncher.zen.domain.usage.UsagePressure
import com.zenlauncher.zen.domain.usage.UsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsagePressureTest {

    private fun snapshot(
        minutes: Long = 0,
        unlocks: Int = 0,
        apps: List<AppUsage> = emptyList(),
    ) = UsageSnapshot(
        dayStartMillis = 0L,
        nowMillis = minutes * 60_000L,
        screenMillis = minutes * 60_000L,
        unlocks = unlocks,
        apps = apps,
    )

    @Test
    fun `un dia corto y con pocos desbloqueos es calma`() {
        assertEquals(UsageLevel.CALMA, UsagePressure.read(snapshot(minutes = 20, unlocks = 8)).level)
    }

    @Test
    fun `el tiempo por si solo sube el escalon`() {
        assertEquals(UsageLevel.NORMAL, UsagePressure.read(snapshot(minutes = 61)).level)
        assertEquals(UsageLevel.ALTA, UsagePressure.read(snapshot(minutes = 151)).level)
        assertEquals(UsageLevel.EXCESO, UsagePressure.read(snapshot(minutes = 301)).level)
    }

    /**
     * La razon de que haya dos varas: coger el telefono ciento veinte veces para mirar
     * nada es una conducta compulsiva aunque el total del dia sea de media hora. Con
     * una sola vara —el tiempo— este dia salia en calma.
     */
    @Test
    fun `muchos desbloqueos suben el escalon aunque el tiempo sea poco`() {
        val reading = UsagePressure.read(snapshot(minutes = 25, unlocks = 120))

        assertEquals(UsageLevel.EXCESO, reading.level)
        assertTrue(reading.worthShowing)
    }

    @Test
    fun `manda la peor de las dos varas`() {
        // Cuatro horas de pantalla con solo diez desbloqueos: una pelicula. El tiempo
        // manda igualmente, porque el escalon es el maximo y no el promedio.
        assertEquals(UsageLevel.ALTA, UsagePressure.read(snapshot(minutes = 240, unlocks = 10)).level)
    }

    /**
     * Regresion: sin acceso concedido, el hueco venia con ceros y `read` lo leia como
     * un dia ejemplar. El pulso no puede felicitar por un dia que no ha medido.
     */
    @Test
    fun `sin medida no hay escalon ni pulso`() {
        val reading = UsagePressure.read(UsageSnapshot.unmeasured(nowMillis = 1L, dayStartMillis = 0L))

        assertEquals(UsageLevel.CALMA, reading.level)
        assertFalse(reading.measured)
        assertFalse(reading.worthShowing)
    }

    @Test
    fun `el pulso solo se pinta a partir de uso alto`() {
        assertFalse(UsagePressure.read(snapshot(minutes = 61)).worthShowing)
        assertTrue(UsagePressure.read(snapshot(minutes = 151)).worthShowing)
    }

    /**
     * Y tambien cuando el reloj va bien pero una sola aplicacion se lo esta comiendo: el
     * pulso lo decide la cara, no el escalon por tiempo. Ver `UsageMood`.
     */
    @Test
    fun `una aplicacion acaparando saca el pulso aunque el escalon sea normal`() {
        val reading = UsagePressure.read(
            snapshot(
                minutes = 128,
                apps = listOf(AppUsage("com.instagram.android", 8, 98 * 60_000L)),
            ),
        )

        assertEquals(UsageLevel.NORMAL, reading.level)
        assertTrue(reading.worthShowing)
    }

    @Test
    fun `la aplicacion mas usada es la que mas tiempo se llevo, no la mas abierta`() {
        val reading = UsagePressure.read(
            snapshot(
                minutes = 200,
                apps = listOf(
                    AppUsage("com.mucho.abierta", openings = 40, foregroundMillis = 10 * 60_000L),
                    AppUsage("com.mucho.tiempo", openings = 2, foregroundMillis = 90 * 60_000L),
                ),
            ),
        )

        assertEquals("com.mucho.tiempo", reading.topApp?.packageName)
    }
}
