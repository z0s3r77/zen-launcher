package com.zenlauncher.zen.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.repository.PreferencesRepository
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

    override suspend fun clearActiveSession() {
        store.edit { prefs ->
            prefs.remove(Keys.ActiveId)
            prefs.remove(Keys.ActiveStartedWall)
            prefs.remove(Keys.ActiveStartedElapsed)
            prefs.remove(Keys.ActivePlanned)
            prefs.remove(Keys.ActiveInitialBattery)
            prefs.remove(Keys.ActiveInitialCharging)
            prefs.remove(Keys.ActiveRestrictedCount)
        }
    }

    override suspend fun setPendingSummary(sessionId: String) {
        store.edit { prefs -> prefs[Keys.PendingSummary] = sessionId }
    }

    override suspend fun clearPendingSummary() {
        store.edit { prefs -> prefs.remove(Keys.PendingSummary) }
    }

    override suspend fun currentActiveSession(): ActiveSession? = data.first().readActiveSession()

    override suspend fun currentRestrictedPackages(): Set<String> =
        data.first()[Keys.Restricted].orEmpty()

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
