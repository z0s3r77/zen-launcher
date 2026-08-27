package com.zenlauncher.zen.fakes

import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.apps.AppRestrictionManager
import com.zenlauncher.zen.domain.apps.EnforcementLevel
import com.zenlauncher.zen.domain.battery.BatteryReader
import com.zenlauncher.zen.domain.battery.BatteryStatus
import android.content.Intent
import com.zenlauncher.zen.domain.media.MediaTransport
import com.zenlauncher.zen.domain.media.NowPlaying
import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.reading.ReadingSettings
import com.zenlauncher.zen.domain.notifications.AppNotification
import com.zenlauncher.zen.domain.notifications.NotificationsRepository
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.model.ZenSession
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import com.zenlauncher.zen.domain.weather.WeatherPlace
import com.zenlauncher.zen.domain.weather.WeatherReading
import com.zenlauncher.zen.domain.repository.SessionRepository
import com.zenlauncher.zen.domain.session.SessionAlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Reloj controlado por el test: los dos relojes se mueven por separado a proposito. */
class FakeZenClock(
    var wall: Long = 1_700_000_000_000,
    var elapsed: Long = 10_000,
) : ZenClock {
    override fun wallTimeMillis(): Long = wall

    override fun elapsedRealtimeMillis(): Long = elapsed

    /** Avanza ambos relojes de forma coherente, como haria un dispositivo real. */
    fun advance(millis: Long) {
        wall += millis
        elapsed += millis
    }
}

class FakeBatteryReader(
    var status: BatteryStatus = BatteryStatus(percent = 84, charging = false),
) : BatteryReader {

    /**
     * Cuantas veces alguien se ha puesto a observar la bateria.
     *
     * Lo mira `SessionViewModelTest`: observar de verdad registra un
     * `BroadcastReceiver` de `ACTION_BATTERY_CHANGED`, que es de los broadcasts que mas
     * emite Android, y no puede estar activo con la pantalla de inicio quieta.
     */
    var observers = 0
        private set

    override fun current(): BatteryStatus = status

    override fun observe(): Flow<BatteryStatus> =
        MutableStateFlow(status).onStart { observers++ }
}

/**
 * Reproductor de mentira. [accepts] simula que no hay ninguna sesion de medios activa,
 * el caso en el que el transporte real devuelve false y la interfaz no debe fingir nada.
 */
class FakeMediaTransport(
    var playing: Boolean = false,
    var accepts: Boolean = true,
    nowPlaying: NowPlaying? = null,
    private val metadataAccess: Boolean = nowPlaying != null,
) : MediaTransport {

    val commands = mutableListOf<String>()

    private val nowPlayingFlow = MutableStateFlow(nowPlaying)

    /** Simula un cambio de cancion o una pausa desde el propio reproductor. */
    fun emitNowPlaying(value: NowPlaying?) {
        nowPlayingFlow.value = value
    }

    override fun isPlaying(): Boolean = playing

    override fun observeNowPlaying(): Flow<NowPlaying?> = nowPlayingFlow.asStateFlow()

    override fun hasMetadataAccess(): Boolean = metadataAccess

    override fun metadataAccessIntent(): Intent = Intent("test.notification.listeners")

    override fun playPause(): Boolean = record("playPause") {
        playing = !playing
    }

    override fun next(): Boolean = record("next") {}

    override fun previous(): Boolean = record("previous") {}

    private fun record(command: String, effect: () -> Unit): Boolean {
        commands += command
        if (!accepts) return false
        effect()
        return true
    }
}

/**
 * Panel de notificaciones de mentira. [access] separa los dos casos que la interfaz
 * distingue: sin acceso no hay lista **y** hay que decirlo, que no es lo mismo que un
 * panel vacio.
 */
class FakeNotificationsRepository(
    posted: List<AppNotification> = emptyList(),
    private val access: Boolean = true,
) : NotificationsRepository {

    private val state = MutableStateFlow(posted)

    override fun observeNotifications(): Flow<List<AppNotification>> = state.asStateFlow()

    override fun hasAccess(): Boolean = access

    override fun accessIntent(): Intent = Intent("test.notification.listeners")

    /** Simula que llega o se retira una notificacion sin tocar la pantalla. */
    fun emit(notifications: List<AppNotification>) {
        state.value = notifications
    }
}

fun appNotification(
    packageName: String,
    title: String = "Aviso",
    text: String = "Contenido",
    postTime: Long = 1_700_000_000_000,
    ongoing: Boolean = false,
    groupSummary: Boolean = false,
    key: String = "$packageName|$title|$postTime",
) = AppNotification(
    key = key,
    packageName = packageName,
    title = title,
    text = text,
    postTime = postTime,
    ongoing = ongoing,
    groupSummary = groupSummary,
)

class FakeSessionRepository : SessionRepository {
    private val stored = MutableStateFlow<List<ZenSession>>(emptyList())

    var recordCallCount: Int = 0
        private set

    override fun observeAll(): Flow<List<ZenSession>> = stored.asStateFlow()

    override suspend fun all(): List<ZenSession> = stored.value

    override suspend fun find(id: String): ZenSession? = stored.value.firstOrNull { it.id == id }

    override suspend fun recordIfAbsent(session: ZenSession): Boolean {
        recordCallCount++
        if (stored.value.any { it.id == session.id }) return false
        stored.value = stored.value + session
        return true
    }

    override suspend fun deleteAll() {
        stored.value = emptyList()
    }
}

class FakePreferencesRepository(
    initialRestricted: Set<String> = emptySet(),
    initialSeeded: Boolean = true,
) : PreferencesRepository {

    private val restricted = MutableStateFlow(initialRestricted)
    private val favourites = MutableStateFlow<List<String>>(emptyList())
    private val seeded = MutableStateFlow(initialSeeded)
    private val duration = MutableStateFlow(ZenDuration.Default)
    private val active = MutableStateFlow<ActiveSession?>(null)
    private val pendingSummary = MutableStateFlow<String?>(null)
    private val lastDistraction = MutableStateFlow<Long?>(null)
    private val place = MutableStateFlow<WeatherPlace?>(null)
    private val weather = MutableStateFlow<WeatherReading?>(null)
    private val weatherAttempt = MutableStateFlow<Long?>(null)
    private val news = MutableStateFlow<NewsEdition?>(null)
    private val reading = MutableStateFlow(ReadingSettings())

    override val restrictedPackages: Flow<Set<String>> = restricted.asStateFlow()
    override val favouritePackages: Flow<List<String>> = favourites.asStateFlow()
    override val favouritesSeeded: Flow<Boolean> = seeded.asStateFlow()
    override val preferredDuration: Flow<ZenDuration> = duration.asStateFlow()
    override val activeSession: Flow<ActiveSession?> = active.asStateFlow()
    override val pendingSummarySessionId: Flow<String?> = pendingSummary.asStateFlow()
    override val lastDistractionAtMillis: Flow<Long?> = lastDistraction.asStateFlow()
    override val weatherPlace: Flow<WeatherPlace?> = place.asStateFlow()
    override val lastWeather: Flow<WeatherReading?> = weather.asStateFlow()
    override val lastWeatherAttemptAtMillis: Flow<Long?> = weatherAttempt.asStateFlow()
    override val lastNews: Flow<NewsEdition?> = news.asStateFlow()
    override val readingSettings: Flow<ReadingSettings> = reading.asStateFlow()

    override suspend fun setRestricted(packageName: String, restricted: Boolean) {
        this.restricted.value = if (restricted) {
            this.restricted.value + packageName
        } else {
            this.restricted.value - packageName
        }
    }

    override suspend fun setFavourites(packages: List<String>) {
        favourites.value = packages
    }

    override suspend fun markFavouritesSeeded() {
        seeded.value = true
    }

    override suspend fun setPreferredDuration(duration: ZenDuration) {
        this.duration.value = duration
    }

    override suspend fun setWeatherPlace(place: WeatherPlace?) {
        this.place.value = place
        // Como en el repositorio de verdad: cambiar o quitar la ciudad invalida lo
        // traido para la anterior.
        weather.value = null
        weatherAttempt.value = null
    }

    override suspend fun setLastWeather(reading: WeatherReading) {
        weather.value = reading
    }

    override suspend fun setLastWeatherAttemptAt(millis: Long) {
        weatherAttempt.value = millis
    }

    override suspend fun setLastNews(edition: NewsEdition) {
        news.value = edition
    }

    override suspend fun setReadingSettings(settings: ReadingSettings) {
        reading.value = settings
    }

    override suspend fun putActiveSession(session: ActiveSession) {
        active.value = session
    }

    override suspend fun clearActiveSession() {
        active.value = null
    }

    override suspend fun setPendingSummary(sessionId: String) {
        pendingSummary.value = sessionId
    }

    override suspend fun clearPendingSummary() {
        pendingSummary.value = null
    }

    override suspend fun setLastDistractionAt(millis: Long) {
        lastDistraction.value = millis
    }

    override suspend fun currentActiveSession(): ActiveSession? = active.value

    override suspend fun currentRestrictedPackages(): Set<String> = restricted.value
}

class RecordingAlarmScheduler : SessionAlarmScheduler {
    var scheduled: ActiveSession? = null
        private set
    var cancelCount: Int = 0
        private set

    override fun schedule(session: ActiveSession) {
        scheduled = session
    }

    override fun cancel() {
        cancelCount++
        scheduled = null
    }
}

/** Cuenta las llamadas a enforce/release para comprobar que el punto de v0.2 se usa. */
class RecordingRestrictionManager(
    private val preferences: PreferencesRepository,
) : AppRestrictionManager {

    var enforceCount: Int = 0
        private set
    var releaseCount: Int = 0
        private set

    override val restrictedPackages: Flow<Set<String>> = preferences.restrictedPackages

    override val enforcementLevel: EnforcementLevel = EnforcementLevel.VISIBILITY_ONLY

    override suspend fun setRestricted(packageName: String, restricted: Boolean) {
        preferences.setRestricted(packageName, restricted)
    }

    override fun visibleApps(all: List<InstalledApp>, restricted: Set<String>) =
        all.filterNot { it.packageName in restricted }

    override suspend fun enforce(session: ActiveSession) {
        enforceCount++
    }

    override suspend fun release() {
        releaseCount++
    }
}

class FakeInstalledAppsRepository(
    apps: List<InstalledApp> = emptyList(),
) : InstalledAppsRepository {

    private val state = MutableStateFlow(apps)

    val launched = mutableListOf<InstalledApp>()

    override fun observeInstalledApps(): Flow<List<InstalledApp>> =
        state.map { list -> list.sortedBy { it.sortKey } }

    override suspend fun launchableApps(): List<InstalledApp> = state.first()

    override fun launch(app: InstalledApp): Boolean {
        launched += app
        return true
    }

    override suspend fun launchPackage(packageName: String): Boolean {
        val app = state.value.firstOrNull { it.packageName == packageName } ?: return false
        launched += app
        return true
    }
}

fun installedApp(packageName: String, label: String = packageName) = InstalledApp(
    packageName = packageName,
    label = label,
    componentName = "$packageName/.Main",
)
