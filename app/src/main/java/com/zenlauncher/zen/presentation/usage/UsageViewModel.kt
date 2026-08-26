package com.zenlauncher.zen.presentation.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.apps.EssentialApps
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import com.zenlauncher.zen.domain.usage.Compulsion
import com.zenlauncher.zen.domain.usage.CompulsionDetector
import com.zenlauncher.zen.domain.usage.DistractionPolicy
import com.zenlauncher.zen.domain.usage.UsageLevel
import com.zenlauncher.zen.domain.usage.UsagePressure
import com.zenlauncher.zen.domain.usage.UsageReading
import com.zenlauncher.zen.domain.usage.UsageRepository
import com.zenlauncher.zen.domain.usage.UsagePattern
import com.zenlauncher.zen.domain.usage.UsagePatterns
import com.zenlauncher.zen.domain.usage.UsageSnapshot
import com.zenlauncher.zen.domain.usage.WeekVerdict
import com.zenlauncher.zen.domain.usage.WeeklyUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Una aplicacion en la lista del dia, ya con su rotulo resuelto. */
data class UsageAppRow(
    val packageName: String,
    val label: String,
    val openings: Int,
    val foregroundMillis: Long,
)

data class UsageUiState(
    val reading: UsageReading = NO_READING,
    val apps: List<UsageAppRow> = emptyList(),
    val hasAccess: Boolean = false,
) {
    val measured: Boolean get() = reading.measured
}

/** Una observacion sobre el habito, ya con el rotulo de su aplicacion resuelto. */
data class PatternRow(
    val pattern: UsagePattern,
    /** null en los patrones que no senalan a una aplicacion, o si se desinstalo. */
    val label: String?,
)

data class WeekUiState(
    val week: WeeklyUsage = WeeklyUsage(emptyList()),
    val patterns: List<PatternRow> = emptyList(),
    val verdict: WeekVerdict = WeekVerdict.BAJO_CONTROL,
    val hasAccess: Boolean = false,
    /**
     * Mientras se leen los siete dias.
     *
     * Hace falta como estado propio porque "todavia no lo se" y "no hay nada que
     * ensenar" son lo mismo en los datos y no lo son para el usuario: sin esto, la
     * pantalla decia "sin datos" durante el medio segundo que tarda la consulta.
     */
    val loading: Boolean = true,
)

/** Lo que hay que ensenar cuando salta el aviso. */
data class DistractionUiState(
    val compulsion: Compulsion,
    /** Rotulo de la aplicacion. null en picoteo, o si ya no esta instalada. */
    val appLabel: String?,
)

private val NO_READING = UsageReading(
    level = UsageLevel.CALMA,
    screenMillis = 0L,
    unlocks = 0,
    topApp = null,
    measured = false,
)

/**
 * El uso del movil: el pulso de la pantalla de inicio, la pantalla de detalle y el
 * aviso de distraccion.
 *
 * **Todo se mide al volver a la pantalla de inicio, y solo ahi.** No hay sondeo, ni
 * flujo que lata, ni servicio: el momento en que el dato cambia y el momento en que
 * alguien puede leerlo son el mismo —vuelves de una aplicacion—, asi que despertar en
 * cualquier otro no anadiria informacion y si gastaria bateria. Es tambien el unico
 * momento en el que un aviso puede llegar sin interrumpir nada: la pantalla anterior ya
 * la cerro el usuario.
 */
class UsageViewModel(
    private val usage: UsageRepository,
    private val installedApps: InstalledAppsRepository,
    private val preferences: PreferencesRepository,
    private val clock: ZenClock,
) : ViewModel() {

    private val snapshot = MutableStateFlow(UsageSnapshot.unmeasured(0L, 0L))
    private val access = MutableStateFlow(false)

    private val _distraction = MutableStateFlow<DistractionUiState?>(null)
    val distraction: StateFlow<DistractionUiState?> = _distraction.asStateFlow()

    private val _week = MutableStateFlow(WeekUiState())
    val week: StateFlow<WeekUiState> = _week.asStateFlow()

    /** Ultima lectura de la semana. Son siete consultas y no se repiten sin motivo. */
    private var lastWeekReadAt = 0L

    /**
     * Ultima lectura del dia completo. Recorrer los eventos de toda la jornada en cada
     * vuelta a la home seria releer miles de entradas para ver cambiar un minuto; la
     * ventana corta del detector si se consulta siempre, porque es la que decide si hay
     * que decir algo y es barata.
     */
    private var lastFullReadAt = 0L

    val state: StateFlow<UsageUiState> = combine(
        snapshot,
        access,
        installedApps.observeInstalledApps(),
    ) { day, granted, apps ->
        val labels = apps.associate { it.packageName to it.label }
        UsageUiState(
            reading = UsagePressure.read(day),
            apps = day.apps.map { app ->
                UsageAppRow(
                    packageName = app.packageName,
                    // Sin rotulo se ensena el paquete: una fila con tiempo y sin nombre
                    // es peor que una fila fea.
                    label = labels[app.packageName] ?: app.packageName,
                    openings = app.openings,
                    foregroundMillis = app.foregroundMillis,
                )
            },
            hasAccess = granted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UsageUiState(),
    )

    init {
        refresh()
    }

    /** Se llama al volver a primer plano, desde el ciclo de vida de la Activity. */
    fun refresh() {
        viewModelScope.launch {
            val now = clock.wallTimeMillis()
            val granted = usage.hasAccess()
            access.value = granted
            if (!granted) {
                snapshot.value = UsageSnapshot.unmeasured(now, now)
                return@launch
            }

            if (now - lastFullReadAt >= FULL_READ_INTERVAL_MILLIS || now < lastFullReadAt) {
                lastFullReadAt = now
                snapshot.value = usage.today(now)
            }
            evaluateDistraction(now)
        }
    }

    fun dismissDistraction() {
        _distraction.value = null
    }

    /**
     * Se llama al abrir la pantalla de la semana, **no** al volver a la home.
     *
     * Son siete consultas de eventos, una por dia: el coste esta bien pagado cuando
     * alguien entra a mirar su semana a proposito, y seria un despilfarro repetirlo cada
     * vez que se vuelve a la pantalla de inicio para ver la hora.
     */
    fun loadWeek() {
        viewModelScope.launch {
            val now = clock.wallTimeMillis()
            val granted = usage.hasAccess()
            if (!granted) {
                _week.value = WeekUiState(hasAccess = false, loading = false)
                return@launch
            }
            if (_week.value.hasData() && now - lastWeekReadAt < WEEK_READ_INTERVAL_MILLIS &&
                now >= lastWeekReadAt
            ) {
                return@launch
            }

            lastWeekReadAt = now
            val week = usage.week(now, WEEK_DAYS)
            // Sobre lo ya restringido no se opina: recomendar restringir lo que ya lo
            // esta es ruido con forma de consejo.
            val restricted = preferences.currentRestrictedPackages()
            val labels = installedApps.observeInstalledApps().first()
                .associate { it.packageName to it.label }

            _week.value = WeekUiState(
                week = week,
                patterns = UsagePatterns.of(week, exclude = restricted).map { pattern ->
                    PatternRow(pattern, pattern.packageName?.let { labels[it] })
                },
                verdict = UsagePatterns.verdict(week),
                hasAccess = true,
                loading = false,
            )
        }
    }

    private fun WeekUiState.hasData(): Boolean = !loading && week.hasData

    private suspend fun evaluateDistraction(nowMillis: Long) {
        // Ya hay un aviso en pantalla: volver a calcularlo solo podria cambiarlo por
        // otro debajo del dedo del usuario.
        if (_distraction.value != null) return

        val compulsion = CompulsionDetector.detect(
            openings = usage.recentOpenings(DETECTION_WINDOW_MILLIS, nowMillis),
            nowMillis = nowMillis,
            exempt = EssentialApps.candidatePackages,
        )

        val interrupt = DistractionPolicy.shouldInterrupt(
            compulsion = compulsion,
            lastShownAtMillis = preferences.lastDistractionAtMillis.first(),
            nowMillis = nowMillis,
            sessionActive = preferences.currentActiveSession() != null,
        )
        if (!interrupt || compulsion == null) return

        // La marca se escribe al ensenarlo, no al descartarlo: si el usuario sale de Zen
        // sin tocar nada, el aviso ya ocurrio y no puede repetirse al volver.
        preferences.setLastDistractionAt(nowMillis)
        _distraction.value = DistractionUiState(
            compulsion = compulsion,
            appLabel = compulsion.packageName?.let { packageName ->
                installedApps.observeInstalledApps().first()
                    .firstOrNull { it.packageName == packageName }?.label
            },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Un minuto: por debajo, el dia no ha cambiado lo bastante para redibujarlo. */
        const val FULL_READ_INTERVAL_MILLIS = 60_000L

        /**
         * Dos horas. Mas ancho que cualquier ventana de [CompulsionDetector] a proposito:
         * una sentada larga puede haber empezado antes de su ventana y seguir viva, y
         * consultando solo la hora del arrastre no se veia su apertura.
         */
        const val DETECTION_WINDOW_MILLIS = 2 * 60 * 60 * 1_000L

        /** Siete dias: es lo que suele conservar Android, y una semana se lee de un vistazo. */
        const val WEEK_DAYS = 7

        /** Cinco minutos: dentro de una misma visita, la semana no ha cambiado. */
        const val WEEK_READ_INTERVAL_MILLIS = 5 * 60_000L
    }
}
