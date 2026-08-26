package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.usage.RawUsageEvent
import com.zenlauncher.zen.domain.usage.UsageEventKind
import com.zenlauncher.zen.domain.usage.UsageTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageTimelineTest {

    private fun abre(packageName: String, at: Long) =
        RawUsageEvent(packageName, UsageEventKind.APP_ABIERTA, at)

    private fun cierra(packageName: String, at: Long) =
        RawUsageEvent(packageName, UsageEventKind.APP_CERRADA, at)

    private fun desbloqueo(at: Long) = RawUsageEvent(null, UsageEventKind.DESBLOQUEO, at)

    private fun minutos(value: Int) = value * 60_000L

    @Test
    fun `un tramo cerrado cuenta su duracion y una apertura`() {
        val snapshot = UsageTimeline.fold(
            events = listOf(abre("com.whatsapp", 0), cierra("com.whatsapp", minutos(5))),
            nowMillis = minutos(30),
            dayStartMillis = 0L,
        )

        assertEquals(minutos(5), snapshot.screenMillis)
        assertEquals(1, snapshot.apps.single().openings)
    }

    /**
     * Regresion: hay ROMs que no emiten el cierre de la aplicacion anterior al abrir
     * otra. Sin cierre implicito, la primera aplicacion del dia se quedaba abierta hasta
     * la noche y se llevaba la jornada entera.
     */
    @Test
    fun `abrir otra aplicacion cierra la anterior aunque no llegue su evento de cierre`() {
        val snapshot = UsageTimeline.fold(
            events = listOf(abre("com.whatsapp", 0), abre("com.instagram.android", minutos(3))),
            nowMillis = minutos(10),
            dayStartMillis = 0L,
        )

        val porPaquete = snapshot.apps.associateBy { it.packageName }
        assertEquals(minutos(3), porPaquete.getValue("com.whatsapp").foregroundMillis)
        assertEquals(minutos(7), porPaquete.getValue("com.instagram.android").foregroundMillis)
    }

    /**
     * Regresion: lo que sigue delante ahora mismo se cerraba en su ultimo evento, que no
     * existe, asi que una sentada de una hora en curso valia cero y el arrastre nunca
     * saltaba mientras estaba ocurriendo.
     */
    @Test
    fun `lo que sigue abierto cuenta hasta ahora`() {
        val snapshot = UsageTimeline.fold(
            events = listOf(abre("com.tiktok", 0)),
            nowMillis = minutos(48),
            dayStartMillis = 0L,
        )

        assertEquals(minutos(48), snapshot.screenMillis)
    }

    @Test
    fun `apagar la pantalla cierra el tramo`() {
        // Para Android la aplicacion sigue delante con el movil en el bolsillo; para el
        // usuario, no. Sin esto, dejar Instagram abierto y guardar el telefono sumaba
        // horas de "uso".
        val snapshot = UsageTimeline.fold(
            events = listOf(abre("com.instagram.android", 0), desbloqueo(minutos(2))),
            nowMillis = minutos(120),
            dayStartMillis = 0L,
        )

        assertEquals(minutos(2), snapshot.screenMillis)
    }

    @Test
    fun `los desbloqueos se cuentan aparte del tiempo`() {
        val snapshot = UsageTimeline.fold(
            events = listOf(desbloqueo(0), desbloqueo(minutos(1)), desbloqueo(minutos(2))),
            nowMillis = minutos(3),
            dayStartMillis = 0L,
        )

        assertEquals(3, snapshot.unlocks)
        assertEquals(0L, snapshot.screenMillis)
    }

    /**
     * Regresion: al cambiar de aplicacion el sistema emite pares de entrada y salida de
     * unos milisegundos sobre pantallas intermedias. Contandolos, abrir el cajon y tocar
     * una aplicacion valia tres aperturas y el detector de picoteo saltaba solo.
     */
    @Test
    fun `los parpadeos de menos de un segundo no son aperturas`() {
        val snapshot = UsageTimeline.fold(
            events = listOf(
                abre("com.transicion", 0),
                cierra("com.transicion", 300),
                abre("com.whatsapp", 400),
                cierra("com.whatsapp", minutos(4)),
            ),
            nowMillis = minutos(10),
            dayStartMillis = 0L,
        )

        assertEquals("com.whatsapp", snapshot.apps.single().packageName)
    }

    @Test
    fun `el propio launcher no cuenta como uso del movil`() {
        // Si contase, Zen seria siempre la aplicacion mas usada del dia: mirar la hora
        // no es consumo de movil.
        val snapshot = UsageTimeline.fold(
            events = listOf(abre("com.zenlauncher.zen", 0), cierra("com.zenlauncher.zen", minutos(9))),
            nowMillis = minutos(10),
            dayStartMillis = 0L,
            ignore = setOf("com.zenlauncher.zen"),
        )

        assertEquals(0L, snapshot.screenMillis)
        assertTrue(snapshot.apps.isEmpty())
    }

    @Test
    fun `los eventos desordenados se ordenan antes de plegar`() {
        val snapshot = UsageTimeline.fold(
            events = listOf(cierra("com.whatsapp", minutos(5)), abre("com.whatsapp", 0)),
            nowMillis = minutos(6),
            dayStartMillis = 0L,
        )

        assertEquals(minutos(5), snapshot.screenMillis)
    }

    @Test
    fun `las aplicaciones salen ordenadas de mas a menos tiempo`() {
        val snapshot = UsageTimeline.fold(
            events = listOf(
                abre("com.poco", 0),
                cierra("com.poco", minutos(2)),
                abre("com.mucho", minutos(2)),
                cierra("com.mucho", minutos(40)),
            ),
            nowMillis = minutos(41),
            dayStartMillis = 0L,
        )

        assertEquals(listOf("com.mucho", "com.poco"), snapshot.apps.map { it.packageName })
    }

    /**
     * Regresion vista en un Nothing Phone (2a), no en un test: Android emite un
     * `ACTIVITY_RESUMED` por **pantalla**, tambien al navegar dentro de una misma
     * aplicacion. Entrar en Ajustes y bajar tres niveles emitia `SettingsHomepage`,
     * `SubSettings`, `SubSettings`, `SubSettings`, y Zen lo contaba como cuatro
     * aperturas. Con la cuenta inflada, el aviso de picoteo saltaba sin que el usuario
     * hubiera saltado a ninguna parte.
     */
    @Test
    fun `navegar dentro de una aplicacion es una sola visita`() {
        val snapshot = UsageTimeline.fold(
            events = listOf(
                abre("com.android.settings", 0),
                cierra("com.android.settings", 1_000),
                abre("com.android.settings", 1_100),
                cierra("com.android.settings", 3_000),
                abre("com.android.settings", 3_100),
                abre("com.whatsapp", 7_000),
            ),
            nowMillis = minutos(1),
            dayStartMillis = 0L,
        )

        val ajustes = snapshot.apps.first { it.packageName == "com.android.settings" }
        assertEquals(1, ajustes.openings)
        // Y el traspaso entre pantallas cuenta como tiempo dentro de la aplicacion.
        assertEquals(7_000L, ajustes.foregroundMillis)
    }

    /**
     * La otra cara: unir no puede tragarse una salida de verdad. Por eso los tramos se
     * unen **antes** de descartar los paquetes ignorados; a la inversa, salir a la
     * pantalla de inicio y volver a la misma aplicacion quedaba como una sola visita.
     */
    @Test
    fun `salir a la pantalla de inicio y volver son dos visitas`() {
        val snapshot = UsageTimeline.fold(
            events = listOf(
                abre("com.instagram.android", 0),
                abre("com.zenlauncher.zen", 5_000),
                abre("com.instagram.android", 10_000),
            ),
            nowMillis = 20_000,
            dayStartMillis = 0L,
            ignore = setOf("com.zenlauncher.zen"),
        )

        assertEquals(2, snapshot.apps.single().openings)
    }

    /**
     * Regresion: apagar la pantalla cierra el tramo pero no abre ninguno, asi que los
     * dos tramos de la misma aplicacion quedaban pegados en la lista y se unian. Esa
     * visita se llevaba toda la noche con el movil en el bolsillo.
     */
    @Test
    fun `apagar la pantalla y volver a la misma aplicacion son dos visitas`() {
        val snapshot = UsageTimeline.fold(
            events = listOf(
                abre("com.instagram.android", 0),
                desbloqueo(5_000),
                abre("com.instagram.android", minutos(60)),
            ),
            nowMillis = minutos(61),
            dayStartMillis = 0L,
        )

        val instagram = snapshot.apps.single()
        assertEquals(2, instagram.openings)
        // Cinco segundos mas un minuto: la hora de bolsillo no cuenta.
        assertEquals(5_000L + minutos(1), instagram.foregroundMillis)
    }

    /**
     * El orden de los pasos: unir primero y filtrar despues. Al reves, tres subpantallas
     * de menos de un segundo se descartaban una a una y la aplicacion desaparecia del
     * dia en lugar de sumar el segundo y medio que duro.
     */
    @Test
    fun `las subpantallas cortas de una misma aplicacion suman en vez de desaparecer`() {
        val snapshot = UsageTimeline.fold(
            events = listOf(
                abre("com.android.settings", 0),
                cierra("com.android.settings", 400),
                abre("com.android.settings", 500),
                cierra("com.android.settings", 900),
                abre("com.android.settings", 1_000),
                cierra("com.android.settings", 1_400),
            ),
            nowMillis = minutos(1),
            dayStartMillis = 0L,
        )

        assertEquals(1_400L, snapshot.apps.single().foregroundMillis)
    }
}
