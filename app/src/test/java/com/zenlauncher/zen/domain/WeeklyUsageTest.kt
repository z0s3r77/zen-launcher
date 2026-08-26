package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.usage.AppUsage
import com.zenlauncher.zen.domain.usage.UsageSnapshot
import com.zenlauncher.zen.domain.usage.WeeklyUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyUsageTest {

    private fun minutos(value: Long) = value * 60_000L

    private fun dia(
        index: Int,
        minutes: Long,
        unlocks: Int = 10,
        apps: List<AppUsage> = emptyList(),
    ) = UsageSnapshot(
        dayStartMillis = index * 86_400_000L,
        nowMillis = index * 86_400_000L + minutos(minutes),
        screenMillis = minutos(minutes),
        unlocks = unlocks,
        apps = apps,
    )

    private fun sinMedir(index: Int) =
        UsageSnapshot.unmeasured(index * 86_400_000L, index * 86_400_000L)

    /**
     * Dividir siempre entre siete diluiria la media en un telefono que solo conserva
     * tres dias, y ensenaria una semana tranquila que nunca ocurrio.
     */
    @Test
    fun `la media es por dia medido, no por dia de la semana`() {
        val week = WeeklyUsage(listOf(sinMedir(0), sinMedir(1), dia(2, 60), dia(3, 120)))

        assertEquals(minutos(90), week.averageMillis)
        assertEquals(minutos(180), week.totalMillis)
    }

    @Test
    fun `sin ningun dia medido no hay datos y no se divide entre cero`() {
        val week = WeeklyUsage(listOf(sinMedir(0), sinMedir(1)))

        assertFalse(week.hasData)
        assertEquals(0L, week.averageMillis)
        assertEquals(0, week.averageUnlocks)
        assertTrue(week.apps.isEmpty())
    }

    @Test
    fun `una aplicacion suma su tiempo y sus aperturas de todos los dias`() {
        val week = WeeklyUsage(
            listOf(
                dia(0, 60, apps = listOf(AppUsage("com.instagram.android", 10, minutos(40)))),
                dia(1, 60, apps = listOf(AppUsage("com.instagram.android", 20, minutos(50)))),
            ),
        )

        val instagram = week.apps.single()
        assertEquals(30, instagram.openings)
        assertEquals(minutos(90), instagram.foregroundMillis)
        // Y las medias diarias se sacan sobre los dias medidos.
        assertEquals(15, week.dailyOpenings(instagram))
        assertEquals(minutos(45), week.dailyMillis(instagram))
    }

    @Test
    fun `el reparto se calcula sobre el total de la semana`() {
        val week = WeeklyUsage(
            listOf(
                dia(0, 100, apps = listOf(
                    AppUsage("com.instagram.android", 5, minutos(75)),
                    AppUsage("com.whatsapp", 5, minutos(25)),
                )),
                dia(1, 100, apps = listOf(
                    AppUsage("com.instagram.android", 5, minutos(75)),
                    AppUsage("com.whatsapp", 5, minutos(25)),
                )),
            ),
        )

        assertEquals(75, week.shareOf(week.apps.first()))
    }

    @Test
    fun `las aplicaciones salen ordenadas de mas a menos tiempo`() {
        val week = WeeklyUsage(
            listOf(
                dia(0, 100, apps = listOf(
                    AppUsage("com.poco", 50, minutos(10)),
                    AppUsage("com.mucho", 2, minutos(90)),
                )),
                dia(1, 0),
            ),
        )

        assertEquals(listOf("com.mucho", "com.poco"), week.apps.map { it.packageName })
    }

    @Test
    fun `hoy es el ultimo dia de la lista`() {
        val week = WeeklyUsage(listOf(dia(0, 10), dia(1, 20), dia(2, 30)))

        assertEquals(minutos(30), week.today?.screenMillis)
    }
}
