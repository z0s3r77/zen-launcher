package com.zenlauncher.zen.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.zenlauncher.zen.domain.usage.AppOpening
import com.zenlauncher.zen.domain.usage.RawUsageEvent
import com.zenlauncher.zen.domain.usage.UsageEventKind
import com.zenlauncher.zen.domain.usage.UsageRepository
import com.zenlauncher.zen.domain.usage.UsageSnapshot
import com.zenlauncher.zen.domain.usage.UsageTimeline
import com.zenlauncher.zen.domain.usage.WeeklyUsage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * `UsageStatsManager.queryEvents` es la unica via publica para saber que aplicacion
 * estuvo delante y cuanto.
 *
 * Se usa `queryEvents` y no `queryAndAggregateUsageStats`: el agregado viene en cubos
 * de intervalo fijo, no distingue aperturas de tiempo y no sirve para mirar los ultimos
 * quince minutos, que es justo lo que necesita el detector de conductas.
 *
 * **Todo el calculo esta fuera de aqui**, en [UsageTimeline]. Esta clase solo traduce
 * las constantes de Android a los tipos del dominio; asi los casos raros —el cierre que
 * no llega, la aplicacion que sigue abierta— se prueban en la JVM.
 */
class UsageStatsRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : UsageRepository {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(UsageStatsManager::class.java)
    private val appOps = appContext.getSystemService(AppOpsManager::class.java)

    /**
     * Lo que no cuenta como uso del movil.
     *
     * Zen, evidentemente: mirar la hora en la pantalla de inicio no es consumo, y
     * contarlo haria del propio launcher la aplicacion mas usada del dia.
     *
     * **Y las demas pantallas de inicio, que es lo que no se veia venir.** Zen no
     * implementa Recientes, asi que Android sigue usando el de la ROM: cada gesto de
     * recientes pone en primer plano `RecentsActivity`, que pertenece al launcher de
     * fabrica. Medido en un Nothing Phone (2a): 66 "aperturas" al dia de
     * `com.nothing.launcher`, suficientes para salir recomendado como habito a corregir.
     * Navegar por el sistema no es usar una aplicacion.
     *
     * Se resuelve por intent y no con una lista de nombres escritos a mano: el launcher
     * de fabrica se llama distinto en cada ROM.
     */
    private val ignore: Set<String> by lazy {
        buildSet {
            add(appContext.packageName)
            // La interfaz del sistema tampoco: lo que asoma de ella son dialogos y
            // permisos, nunca algo en lo que alguien se quede. Es un nombre fijo de la
            // plataforma, no de un fabricante.
            add("com.android.systemui")
            addAll(homePackages())
        }
    }

    private fun homePackages(): Set<String> = try {
        appContext.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                0,
            )
            .mapTo(mutableSetOf()) { it.activityInfo.packageName }
    } catch (error: RuntimeException) {
        // Sin esto no se pierde la medida, solo la limpieza: mas vale contar de mas que
        // dejar al launcher sin poder leer nada.
        Log.w(TAG, "No se pudieron resolver las pantallas de inicio", error)
        emptySet()
    }

    override fun hasAccess(): Boolean {
        // `PACKAGE_USAGE_STATS` no se comprueba con checkSelfPermission: no se concede
        // en la instalacion sino como operacion de AppOps, desde Ajustes de Android. Sin
        // ella `queryEvents` devuelve una lista vacia en lugar de fallar, que es
        // exactamente lo que hay que distinguir de "hoy no has usado el movil".
        // `checkOpNoThrow` y no `unsafeCheckOpNoThrow`: la familia `unsafe*` esta
        // `@Deprecated` en android-36 —lo estuvo al reves durante unas cuantas
        // versiones— y esta es la vigente.
        val mode = appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName,
        ) ?: return false
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun accessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            // Algunas ROMs abren la lista completa y otras aterrizan en la ficha de la
            // aplicacion si se les da el paquete; el dato sobra donde no se usa.
            .setData(Uri.fromParts("package", appContext.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override suspend fun today(nowMillis: Long): UsageSnapshot = withContext(io) {
        val dayStart = startOfDay(nowMillis)
        if (!hasAccess()) return@withContext UsageSnapshot.unmeasured(nowMillis, dayStart)

        UsageTimeline.fold(
            events = read(dayStart, nowMillis),
            nowMillis = nowMillis,
            dayStartMillis = dayStart,
            ignore = ignore,
        )
    }

    override suspend fun week(nowMillis: Long, days: Int): WeeklyUsage = withContext(io) {
        if (!hasAccess()) return@withContext WeeklyUsage(emptyList())

        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        // Del mas antiguo al mas reciente: la grafica se lee de izquierda a derecha y el
        // ultimo hueco es el de hoy.
        val snapshots = (days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            // `atStartOfDay` por fecha y no sumar 24 horas: la noche del cambio de hora
            // dura 23 o 25, y sumando milisegundos ese dia empezaba desplazado.
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(
                date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                nowMillis,
            )
            val events = if (end > start) read(start, end) else emptyList()
            // Sin un solo evento, el sistema ya no guarda ese dia. No es un dia sin
            // movil: es un dia que no se puede ver, y la grafica lo dibuja distinto.
            if (events.isEmpty()) {
                UsageSnapshot.unmeasured(end, start)
            } else {
                UsageTimeline.fold(events, end, start, ignore)
            }
        }
        WeeklyUsage(snapshots)
    }

    override suspend fun recentOpenings(
        windowMillis: Long,
        nowMillis: Long,
    ): List<AppOpening> = withContext(io) {
        if (!hasAccess()) return@withContext emptyList()

        UsageTimeline.spans(
            events = read(nowMillis - windowMillis, nowMillis),
            nowMillis = nowMillis,
            ignore = ignore,
        )
    }

    /**
     * Medianoche local, no "hace veinticuatro horas".
     *
     * La pregunta que contesta la pantalla es "cuanto llevo hoy", y esa se responde
     * contra el dia del calendario del usuario: a las nueve de la manana, una ventana
     * de veinticuatro horas seguiria arrastrando la noche anterior.
     */
    private fun startOfDay(nowMillis: Long): Long =
        Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    private fun read(fromMillis: Long, toMillis: Long): List<RawUsageEvent> {
        val query = try {
            manager?.queryEvents(fromMillis, toMillis)
        } catch (error: SecurityException) {
            // El acceso pudo revocarse entre la comprobacion y la llamada. Un launcher
            // no se cae por eso: se queda sin medida, que es el estado por defecto.
            Log.w(TAG, "El sistema rechazo la consulta de uso", error)
            null
        } ?: return emptyList()

        val events = mutableListOf<RawUsageEvent>()
        // Se recogen aparte porque no todos los telefonos emiten los dos: sin bloqueo de
        // pantalla configurado no hay KEYGUARD_HIDDEN, y contando los dos a la vez un
        // telefono con bloqueo sumaria cada desbloqueo dos veces.
        val keyguard = mutableListOf<Long>()
        val interactive = mutableListOf<Long>()

        val event = UsageEvents.Event()
        while (query.getNextEvent(event)) {
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    events += RawUsageEvent(
                        event.packageName,
                        UsageEventKind.APP_ABIERTA,
                        event.timeStamp,
                    )

                UsageEvents.Event.ACTIVITY_PAUSED ->
                    events += RawUsageEvent(
                        event.packageName,
                        UsageEventKind.APP_CERRADA,
                        event.timeStamp,
                    )

                UsageEvents.Event.KEYGUARD_HIDDEN -> keyguard += event.timeStamp
                UsageEvents.Event.SCREEN_INTERACTIVE -> interactive += event.timeStamp
            }
        }

        val unlocks = keyguard.ifEmpty { interactive }
        unlocks.mapTo(events) { RawUsageEvent(null, UsageEventKind.DESBLOQUEO, it) }
        return events
    }

    private companion object {
        const val TAG = "ZenUsage"
    }
}
