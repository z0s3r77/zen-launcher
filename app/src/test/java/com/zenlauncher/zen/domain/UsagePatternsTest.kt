package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.usage.AppUsage
import com.zenlauncher.zen.domain.usage.PatternAction
import com.zenlauncher.zen.domain.usage.PatternKind
import com.zenlauncher.zen.domain.usage.UsagePatterns
import com.zenlauncher.zen.domain.usage.UsageSnapshot
import com.zenlauncher.zen.domain.usage.WeekVerdict
import com.zenlauncher.zen.domain.usage.WeeklyUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsagePatternsTest {

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

    /** Dos dias iguales en los que Instagram se lleva la mayor parte. */
    private fun semanaConLadrona(days: Int = 2, openings: Int = 5) = WeeklyUsage(
        (0 until days).map { index ->
            dia(
                index,
                minutes = 180,
                apps = listOf(
                    AppUsage("com.instagram.android", openings, minutos(120)),
                    AppUsage("com.whatsapp", 8, minutos(60)),
                ),
            )
        },
    )

    /**
     * Un solo dia no es un habito: llamar ladrona a una aplicacion porque ayer viste una
     * serie seria adivinar.
     */
    @Test
    fun `con un solo dia medido no se concluye nada`() {
        val week = WeeklyUsage(listOf(sinMedir(0), semanaConLadrona().days.first()))

        assertTrue(UsagePatterns.of(week).isEmpty())
    }

    @Test
    fun `una aplicacion que se lleva la semana sale como ladrona y se puede restringir`() {
        val patterns = UsagePatterns.of(semanaConLadrona())

        val ladrona = patterns.first()
        assertEquals(PatternKind.LADRONA, ladrona.kind)
        assertEquals("com.instagram.android", ladrona.packageName)
        assertEquals(66, ladrona.value)
        assertEquals(minutos(120), ladrona.dailyMillis)
        // Una recomendacion que no lleva a ninguna parte es un sermon.
        assertEquals(PatternAction.RESTRINGIR, ladrona.action)
    }

    /**
     * Las dos condiciones y no una: en una semana muy tranquila la aplicacion mas usada
     * se lleva el 60% de casi nada, y eso no es una ladrona de tiempo.
     */
    @Test
    fun `llevarse un buen porcentaje de casi nada no es ser ladrona`() {
        val week = WeeklyUsage(
            (0..1).map { index ->
                dia(index, 20, apps = listOf(AppUsage("com.instagram.android", 3, minutos(18))))
            },
        )

        assertTrue(UsagePatterns.of(week).none { it.kind == PatternKind.LADRONA })
    }

    @Test
    fun `una aplicacion que se abre sin parar sale como repetida`() {
        val week = WeeklyUsage(
            (0..1).map { index ->
                dia(index, 120, apps = listOf(
                    AppUsage("com.instagram.android", 4, minutos(100)),
                    AppUsage("com.whatsapp", 50, minutos(20)),
                ))
            },
        )

        val repetida = UsagePatterns.of(week).first { it.kind == PatternKind.REPETIDA }
        assertEquals("com.whatsapp", repetida.packageName)
        assertEquals(50, repetida.value)
    }

    /**
     * Decir "Instagram se lleva el 66%" y debajo "abres Instagram 40 veces al dia" es el
     * mismo hallazgo escrito dos veces, y hace parecer que hay dos problemas.
     */
    @Test
    fun `nunca hay dos observaciones sobre la misma aplicacion`() {
        val patterns = UsagePatterns.of(semanaConLadrona(openings = 60))

        assertEquals(1, patterns.count { it.packageName == "com.instagram.android" })
    }

    @Test
    fun `sobre lo ya restringido no se opina`() {
        val patterns = UsagePatterns.of(
            semanaConLadrona(),
            exclude = setOf("com.instagram.android"),
        )

        assertNull(patterns.firstOrNull { it.packageName == "com.instagram.android" })
    }

    @Test
    fun `una semana que va claramente a mas se dice`() {
        val week = WeeklyUsage(
            listOf(dia(0, 60), dia(1, 60), dia(2, 150), dia(3, 150)),
        )

        val subiendo = UsagePatterns.of(week).first { it.kind == PatternKind.SUBIENDO }
        assertEquals(150, subiendo.value)
        // No hay accion: es una observacion y se queda en observacion.
        assertEquals(PatternAction.NINGUNA, subiendo.action)
    }

    /**
     * Se comparan medias y no totales: con tres dias antes y dos despues, los totales
     * dirian que la semana baja siempre.
     */
    @Test
    fun `una semana estable no sube`() {
        val week = WeeklyUsage(listOf(dia(0, 100), dia(1, 100), dia(2, 100), dia(3, 100)))

        assertTrue(UsagePatterns.of(week).none { it.kind == PatternKind.SUBIENDO })
    }

    @Test
    fun `nunca se dan mas de tres observaciones`() {
        val week = WeeklyUsage(
            listOf(dia(0, 60), dia(1, 60)) + (2..3).map { index ->
                dia(index, 300, apps = listOf(
                    AppUsage("com.instagram.android", 60, minutos(200)),
                    AppUsage("com.whatsapp", 80, minutos(50)),
                    AppUsage("com.tiktok", 90, minutos(50)),
                ))
            },
        )

        assertTrue(UsagePatterns.of(week).size <= UsagePatterns.MAX_PATTERNS)
    }

    /**
     * El veredicto reusa los umbrales del dia sobre la media diaria: asi la semana no
     * tiene una segunda tabla de numeros magicos que pueda desincronizarse.
     */
    @Test
    fun `el veredicto sale de la media diaria`() {
        assertEquals(
            WeekVerdict.BAJO_CONTROL,
            UsagePatterns.verdict(WeeklyUsage(listOf(dia(0, 40), dia(1, 40)))),
        )
        assertEquals(
            WeekVerdict.ATENCION,
            UsagePatterns.verdict(WeeklyUsage(listOf(dia(0, 160), dia(1, 160)))),
        )
        assertEquals(
            WeekVerdict.FUERA_DE_MANO,
            UsagePatterns.verdict(WeeklyUsage(listOf(dia(0, 320), dia(1, 320)))),
        )
    }

    @Test
    fun `sin datos el veredicto no acusa`() {
        assertEquals(
            WeekVerdict.BAJO_CONTROL,
            UsagePatterns.verdict(WeeklyUsage(listOf(sinMedir(0)))),
        )
    }
}
