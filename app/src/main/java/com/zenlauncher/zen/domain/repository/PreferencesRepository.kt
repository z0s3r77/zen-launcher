package com.zenlauncher.zen.domain.repository

import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.ZenDuration
import kotlinx.coroutines.flow.Flow

/** Preferencias del usuario y sesion activa. Todo sobrevive al reinicio de la app. */
interface PreferencesRepository {

    val restrictedPackages: Flow<Set<String>>

    /** Paquetes que el usuario ha elegido ver en el launcher, en orden. */
    val favouritePackages: Flow<List<String>>

    val preferredDuration: Flow<ZenDuration>

    /**
     * Si ya se sembro la pantalla de inicio con las aplicaciones esenciales.
     *
     * Hace falta un booleano aparte porque "el usuario no ha elegido nada todavia" y
     * "el usuario los quito todos a proposito" son la misma lista vacia, y volver a
     * sembrar en el segundo caso seria pelearse con el usuario.
     */
    val favouritesSeeded: Flow<Boolean>

    val activeSession: Flow<ActiveSession?>

    /**
     * Id de la ultima sesion terminada que el usuario todavia no ha visto.
     *
     * Se persiste porque una sesion puede cerrarse desde la alarma con la pantalla
     * apagada: sin esto, el resumen solo aparecia cuando se terminaba desde la propia
     * interfaz, que es justo el caso menos frecuente.
     */
    val pendingSummarySessionId: Flow<String?>

    suspend fun setRestricted(packageName: String, restricted: Boolean)

    suspend fun setFavourites(packages: List<String>)

    suspend fun markFavouritesSeeded()

    suspend fun setPreferredDuration(duration: ZenDuration)

    suspend fun putActiveSession(session: ActiveSession)

    suspend fun clearActiveSession()

    suspend fun setPendingSummary(sessionId: String)

    suspend fun clearPendingSummary()

    /** Lectura puntual, para caminos sin composicion (receptor de alarma). */
    suspend fun currentActiveSession(): ActiveSession?

    suspend fun currentRestrictedPackages(): Set<String>
}
