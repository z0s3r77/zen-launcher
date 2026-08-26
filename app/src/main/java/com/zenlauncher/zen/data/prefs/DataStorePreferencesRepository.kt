package com.zenlauncher.zen.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.data.news.NewsJson
import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.reading.ReadingSettings
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import com.zenlauncher.zen.domain.weather.WeatherCondition
import com.zenlauncher.zen.domain.weather.WeatherPlace
import com.zenlauncher.zen.domain.weather.WeatherReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.zenDataStore: DataStore<Preferences> by preferencesDataStore(name = "zen_prefs")

/**
 * El [store] se inyecta en lugar de construirse aqui dentro.
 *
 * DataStore exige **una sola instancia por fichero en todo el proceso**: dos instancias
 * sobre el mismo fichero lo corrompen. El delegado `preferencesDataStore` garantiza esa
 * unicidad en produccion, pero al ser un singleton de proceso haria que los tests
 * compartieran estado entre si. Recibirlo por constructor mantiene ambas cosas.
 */
class DataStorePreferencesRepository(
    private val store: DataStore<Preferences>,
) : PreferencesRepository {

    constructor(context: Context) : this(context.applicationContext.zenDataStore)

    /**
     * Un fichero de preferencias corrupto no debe impedir arrancar el launcher: si es
     * el unico home del dispositivo, un fallo aqui dejaria el telefono sin pantalla de
     * inicio. Se degrada a valores por defecto.
     */
    private val data: Flow<Preferences> = store.data.catch { cause ->
        if (cause is IOException) emit(emptyPreferences()) else throw cause
    }

    override val restrictedPackages: Flow<Set<String>> =
        data.map { it[Keys.Restricted].orEmpty() }

    override val favouritePackages: Flow<List<String>> =
        data.map { prefs -> prefs[Keys.Favourites]?.decodeList().orEmpty() }

    override val preferredDuration: Flow<ZenDuration> = data.map { prefs ->
        ZenDuration.ofMinutesOrNull(prefs[Keys.PreferredMinutes]) ?: ZenDuration.Default
    }

    override val favouritesSeeded: Flow<Boolean> =
        data.map { it[Keys.FavouritesSeeded] == true }

    override val activeSession: Flow<ActiveSession?> = data.map { it.readActiveSession() }

    override val pendingSummarySessionId: Flow<String?> = data.map { it[Keys.PendingSummary] }

    override val lastDistractionAtMillis: Flow<Long?> = data.map { it[Keys.LastDistraction] }

    override val weatherPlace: Flow<WeatherPlace?> = data.map { it.readWeatherPlace() }

    override val lastWeather: Flow<WeatherReading?> = data.map { it.readWeatherReading() }

    override val lastWeatherAttemptAtMillis: Flow<Long?> = data.map { it[Keys.WeatherAttemptAt] }

    override val lastNews: Flow<NewsEdition?> = data.map { prefs ->
        prefs[Keys.News]?.let(NewsJson::decode)
    }

    /**
     * Se guardan escalones sueltos y no un objeto serializado.
     *
     * Cuatro enteros en cuatro claves se leen y se escriben sin traer ninguna libreria
     * de serializacion, y un ajuste que falte o que llegue fuera de rango cae en su valor
     * por defecto en lugar de tirar todos los demas: `ReadingSettings` acota cada
     * escalon, asi que un fichero de preferencias tocado a mano no puede dejar el lector
     * con letra de tamano cero.
     */
    override val readingSettings: Flow<ReadingSettings> = data.map { prefs ->
        ReadingSettings(serif = prefs[Keys.ReadingSerif] != false)
            // Cada escalon pasa por su `with...`, que es quien lo acota: un valor fuera
            // de rango en el fichero de preferencias vuelve al extremo mas cercano en
            // lugar de dejar el lector con letra de tamano cero.
            .withText(prefs[Keys.ReadingText] ?: ReadingSettings.DEFAULT_TEXT)
            .withLeading(prefs[Keys.ReadingLeading] ?: ReadingSettings.DEFAULT_LEADING)
            .withMargin(prefs[Keys.ReadingMargin] ?: ReadingSettings.DEFAULT_MARGIN)
    }

    override suspend fun setRestricted(packageName: String, restricted: Boolean) {
        store.edit { prefs ->
            val current = prefs[Keys.Restricted].orEmpty()
            prefs[Keys.Restricted] =
                if (restricted) current + packageName else current - packageName
        }
    }

    override suspend fun setFavourites(packages: List<String>) {
        store.edit { prefs -> prefs[Keys.Favourites] = packages.encodeList() }
    }

    override suspend fun markFavouritesSeeded() {
        store.edit { prefs -> prefs[Keys.FavouritesSeeded] = true }
    }

    override suspend fun setPreferredDuration(duration: ZenDuration) {
        store.edit { prefs -> prefs[Keys.PreferredMinutes] = duration.wholeMinutes }
    }

    override suspend fun putActiveSession(session: ActiveSession) {
        store.edit { prefs ->
            prefs[Keys.ActiveId] = session.id
            prefs[Keys.ActiveStartedWall] = session.startedAtWallMillis
            prefs[Keys.ActiveStartedElapsed] = session.startedAtElapsedMillis
            prefs[Keys.ActivePlanned] = session.plannedDurationMillis
            prefs[Keys.ActiveInitialBattery] = session.initialBatteryPercent
            prefs[Keys.ActiveInitialCharging] = session.initialCharging
            prefs[Keys.ActiveRestrictedCount] = session.restrictedAppsCount
        }
    }

    /**
     * Se borra con `-=` y no con `remove`: `MutablePreferences.remove` devuelve el valor
     * que habia, y sobre una clave ausente eso es un null que Kotlin desempaqueta a un
     * `long` primitivo y revienta con NullPointerException. Aqui pasa siempre que se
     * limpie algo que no estaba —cerrar una sesion ya cerrada, quitar una ciudad que
     * nunca se puso— y una excepcion en el arranque del launcher deja el telefono sin
     * pantalla de inicio. `-=` devuelve Unit y no mira lo borrado.
     */
    override suspend fun clearActiveSession() {
        store.edit { prefs ->
            prefs -= Keys.ActiveId
            prefs -= Keys.ActiveStartedWall
            prefs -= Keys.ActiveStartedElapsed
            prefs -= Keys.ActivePlanned
            prefs -= Keys.ActiveInitialBattery
            prefs -= Keys.ActiveInitialCharging
            prefs -= Keys.ActiveRestrictedCount
        }
    }

    override suspend fun setPendingSummary(sessionId: String) {
        store.edit { prefs -> prefs[Keys.PendingSummary] = sessionId }
    }

    override suspend fun setLastDistractionAt(millis: Long) {
        store.edit { prefs -> prefs[Keys.LastDistraction] = millis }
    }

    override suspend fun clearPendingSummary() {
        store.edit { prefs -> prefs -= Keys.PendingSummary }
    }

    override suspend fun setWeatherPlace(place: WeatherPlace?) {
        store.edit { prefs ->
            if (place == null) {
                prefs -= Keys.WeatherPlaceName
                prefs -= Keys.WeatherLatitude
                prefs -= Keys.WeatherLongitude
                // Y con la ciudad se va lo traido: un dato de la ciudad anterior
                // sobreviviendo a su borrado saldria en la franja sin nada que lo
                // explique, y ademas seria de un sitio donde el usuario ya no esta.
                prefs -= Keys.WeatherDegrees
                prefs -= Keys.WeatherCondition
                prefs -= Keys.WeatherObservedAt
                prefs -= Keys.WeatherAttemptAt
            } else {
                prefs[Keys.WeatherPlaceName] = place.name
                prefs[Keys.WeatherLatitude] = place.latitude
                prefs[Keys.WeatherLongitude] = place.longitude
                // Cambiar de ciudad invalida lo anterior por el mismo motivo, y ademas
                // fuerza a pedir de nuevo en la siguiente vuelta a la home.
                prefs -= Keys.WeatherDegrees
                prefs -= Keys.WeatherCondition
                prefs -= Keys.WeatherObservedAt
                prefs -= Keys.WeatherAttemptAt
            }
        }
    }

    override suspend fun setLastWeather(reading: WeatherReading) {
        store.edit { prefs ->
            prefs[Keys.WeatherDegrees] = reading.degrees
            prefs[Keys.WeatherObservedAt] = reading.observedAtMillis
            val condition = reading.condition
            if (condition == null) {
                prefs -= Keys.WeatherCondition
            } else {
                prefs[Keys.WeatherCondition] = condition.name
            }
        }
    }

    override suspend fun setLastWeatherAttemptAt(millis: Long) {
        store.edit { prefs -> prefs[Keys.WeatherAttemptAt] = millis }
    }

    // Se sustituye entera: la portada de hoy reemplaza a la de ayer y no se guarda un
    // historico. Nadie vuelve a la portada de anteayer, y guardarla seria hacer crecer
    // sin tope el fichero que el launcher lee en cada arranque.
    override suspend fun setReadingSettings(settings: ReadingSettings) {
        store.edit { prefs ->
            prefs[Keys.ReadingText] = settings.textStep
            prefs[Keys.ReadingLeading] = settings.leadingStep
            prefs[Keys.ReadingMargin] = settings.marginStep
            prefs[Keys.ReadingSerif] = settings.serif
        }
    }

    override suspend fun setLastNews(edition: NewsEdition) {
        store.edit { prefs -> prefs[Keys.News] = NewsJson.encode(edition) }
    }

    override suspend fun currentActiveSession(): ActiveSession? = data.first().readActiveSession()

    override suspend fun currentRestrictedPackages(): Set<String> =
        data.first()[Keys.Restricted].orEmpty()

    private fun Preferences.readWeatherPlace(): WeatherPlace? {
        val name = this[Keys.WeatherPlaceName] ?: return null
        val latitude = this[Keys.WeatherLatitude] ?: return null
        val longitude = this[Keys.WeatherLongitude] ?: return null
        return WeatherPlace(name = name, latitude = latitude, longitude = longitude)
    }

    private fun Preferences.readWeatherReading(): WeatherReading? {
        val degrees = this[Keys.WeatherDegrees] ?: return null
        val observedAt = this[Keys.WeatherObservedAt] ?: return null
        return WeatherReading(
            degrees = degrees,
            // El nombre guardado puede no existir si algun dia se quita un valor del
            // enum: se lee sin glifo en lugar de reventar al arrancar el launcher.
            condition = this[Keys.WeatherCondition]?.let { name ->
                WeatherCondition.entries.firstOrNull { it.name == name }
            },
            observedAtMillis = observedAt,
        )
    }

    private fun Preferences.readActiveSession(): ActiveSession? {
        val id = this[Keys.ActiveId] ?: return null
        val startedWall = this[Keys.ActiveStartedWall] ?: return null
        val planned = this[Keys.ActivePlanned] ?: return null
        return ActiveSession(
            id = id,
            startedAtWallMillis = startedWall,
            startedAtElapsedMillis = this[Keys.ActiveStartedElapsed] ?: 0L,
            plannedDurationMillis = planned,
            initialBatteryPercent = this[Keys.ActiveInitialBattery] ?: -1,
            initialCharging = this[Keys.ActiveInitialCharging] ?: false,
            restrictedAppsCount = this[Keys.ActiveRestrictedCount] ?: 0,
        )
    }

    private object Keys {
        val Restricted = stringSetPreferencesKey("restricted_packages")

        /** Lista, no Set: el orden de los favoritos lo decide el usuario. */
        val Favourites = stringPreferencesKey("favourite_packages")
        val FavouritesSeeded = booleanPreferencesKey("favourites_seeded")
        val PreferredMinutes = intPreferencesKey("preferred_duration_minutes")

        val ActiveId = stringPreferencesKey("active_session_id")
        val ActiveStartedWall = longPreferencesKey("active_started_wall")
        val ActiveStartedElapsed = longPreferencesKey("active_started_elapsed")
        val ActivePlanned = longPreferencesKey("active_planned_duration")
        val ActiveInitialBattery = intPreferencesKey("active_initial_battery")
        val ActiveInitialCharging = booleanPreferencesKey("active_initial_charging")
        val ActiveRestrictedCount = intPreferencesKey("active_restricted_count")
        val PendingSummary = stringPreferencesKey("pending_summary_session_id")
        val LastDistraction = longPreferencesKey("last_distraction_at")

        val WeatherPlaceName = stringPreferencesKey("weather_place_name")
        val WeatherLatitude = doublePreferencesKey("weather_place_latitude")
        val WeatherLongitude = doublePreferencesKey("weather_place_longitude")
        val WeatherDegrees = intPreferencesKey("weather_degrees")
        val WeatherCondition = stringPreferencesKey("weather_condition")
        val WeatherObservedAt = longPreferencesKey("weather_observed_at")
        val WeatherAttemptAt = longPreferencesKey("weather_attempt_at")

        /** La portada entera en JSON. Ver `NewsJson`. */
        val News = stringPreferencesKey("news_front_page")

        val ReadingText = intPreferencesKey("reading_text_step")
        val ReadingLeading = intPreferencesKey("reading_leading_step")
        val ReadingMargin = intPreferencesKey("reading_margin_step")
        val ReadingSerif = booleanPreferencesKey("reading_serif")
    }
}

/**
 * Los nombres de paquete no admiten `\n`, asi que sirve de separador sin necesidad de
 * traer una libreria de serializacion solo para esto.
 */
private const val LIST_SEPARATOR = "\n"

private fun List<String>.encodeList(): String = joinToString(LIST_SEPARATOR)

private fun String.decodeList(): List<String> =
    if (isEmpty()) emptyList() else split(LIST_SEPARATOR).filter { it.isNotBlank() }
