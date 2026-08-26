package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.news.NewsHeadline
import com.zenlauncher.zen.domain.news.NewsPoint
import com.zenlauncher.zen.domain.news.NewsRefresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NewsRefreshTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(madrid)
            .toInstant()
            .toEpochMilli()

    private fun edition(fetchedAtMillis: Long) = NewsEdition(
        headline = NewsHeadline("Titular", "Bajada"),
        points = listOf(
            NewsPoint(
                index = "01",
                title = "Punto",
                summary = "Resumen",
                url = "https://noticiasdoxa.es/cluster/1/",
            ),
        ),
        fetchedAtMillis = fetchedAtMillis,
    )

    @Test
    fun `sin nada bajado se baja`() {
        assertTrue(NewsRefresh.shouldDownload(null, millis(2026, 8, 25, 9), madrid))
    }

    @Test
    fun `bajada hoy no se vuelve a bajar aunque pasen horas`() {
        val stored = edition(millis(2026, 8, 25, 7, 30))

        assertFalse(NewsRefresh.shouldDownload(stored, millis(2026, 8, 25, 23, 55), madrid))
    }

    /**
     * El corte es el dia natural y no veinticuatro horas desde la descarga. Con un
     * intervalo de 24 h, quien mira las noticias a las once de la noche leeria la
     * portada de ayer durante toda la manana siguiente.
     */
    @Test
    fun `bajada anoche se vuelve a bajar por la manana aunque no hayan pasado 24 horas`() {
        val stored = edition(millis(2026, 8, 24, 23, 30))

        assertTrue(NewsRefresh.shouldDownload(stored, millis(2026, 8, 25, 8, 0), madrid))
    }

    @Test
    fun `la de ayer no cuenta como de hoy`() {
        assertFalse(
            NewsRefresh.isFromToday(
                fetchedAtMillis = millis(2026, 8, 24, 10),
                nowMillis = millis(2026, 8, 25, 10),
                zone = madrid,
            ),
        )
    }

    /**
     * El reloj de pared puede ir hacia atras (cambio de hora, ajuste por red). Se
     * prefiere una descarga de mas a quedarse con una portada vieja para siempre.
     */
    @Test
    fun `un reloj que va hacia atras vuelve a bajar en vez de congelarse`() {
        val stored = edition(millis(2026, 8, 25, 10))

        assertTrue(NewsRefresh.shouldDownload(stored, millis(2026, 8, 24, 10), madrid))
    }
}
