package com.zenlauncher.zen.domain.repository

import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.reading.ReadingSettings
import com.zenlauncher.zen.domain.weather.WeatherPlace
import com.zenlauncher.zen.domain.weather.WeatherReading
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
     * Cuando se enseno por ultima vez el aviso de distraccion, o null si nunca.
     *
     * Se persiste y no se guarda en memoria porque el proceso del launcher muere y
     * revive constantemente: con la marca en memoria, cualquier vuelta a la pantalla de
     * inicio tras una limpieza del sistema volvia a considerar que era la primera vez y
     * el aviso reaparecia sin respetar ninguna espera.
     */
    val lastDistractionAtMillis: Flow<Long?>

    /**
     * Id de la ultima sesion terminada que el usuario todavia no ha visto.
     *
     * Se persiste porque una sesion puede cerrarse desde la alarma con la pantalla
     * apagada: sin esto, el resumen solo aparecia cuando se terminaba desde la propia
     * interfaz, que es justo el caso menos frecuente.
     */
    val pendingSummarySessionId: Flow<String?>

    /**
     * La ciudad de la que se ensena el tiempo, o null si el usuario no eligio ninguna.
     *
     * Null es el estado de fabrica y significa que no se pinta nada ni se sale a la red.
     * El tiempo es lo unico de Zen que usa internet, asi que tiene que estar apagado
     * mientras nadie lo pida.
     */
    val weatherPlace: Flow<WeatherPlace?>

    /**
     * Lo ultimo que se supo del tiempo.
     *
     * Se persiste porque el proceso del launcher muere y revive constantemente: sin
     * esto, cada arranque en frio dejaria la franja vacia hasta que volviera la
     * respuesta de la red. La marca de tiempo viaja con el dato para poder dejar de
     * ensenarlo cuando envejece (ver `WeatherRefresh`).
     */
    val lastWeather: Flow<WeatherReading?>

    /** Cuando se intento traer el tiempo por ultima vez, con acierto o sin el. */
    val lastWeatherAttemptAtMillis: Flow<Long?>

    /**
     * La ultima portada de noticias que se bajo, o null si no se bajo ninguna.
     *
     * Se persiste por dos razones. Una es la de siempre: el proceso del launcher muere
     * y revive constantemente, y sin esto cada arranque en frio volveria a la red. La
     * otra es la regla de esta funcion: **se baja una vez al dia**, asi que lo guardado
     * no es una cache que acelera, es la portada de hoy y punto (ver `NewsRefresh`).
     *
     * Y sigue ahi cuando la de hoy no se puede bajar: leer la de ayer sabiendo que es
     * de ayer es mejor que una pantalla en blanco, que es lo que un movil sin cobertura
     * daria si esto no se guardara.
     */
    val lastNews: Flow<NewsEdition?>

    suspend fun setRestricted(packageName: String, restricted: Boolean)

    suspend fun setFavourites(packages: List<String>)

    suspend fun markFavouritesSeeded()

    suspend fun setPreferredDuration(duration: ZenDuration)

    suspend fun putActiveSession(session: ActiveSession)

    suspend fun clearActiveSession()

    suspend fun setPendingSummary(sessionId: String)

    suspend fun setLastDistractionAt(millis: Long)

    suspend fun clearPendingSummary()

    /** null borra la ciudad: se deja de ensenar el tiempo y de salir a la red. */
    suspend fun setWeatherPlace(place: WeatherPlace?)

    suspend fun setLastWeather(reading: WeatherReading)

    suspend fun setLastWeatherAttemptAt(millis: Long)

    suspend fun setLastNews(edition: NewsEdition)

    /**
     * Como se ve el texto en el lector de libros.
     *
     * Del lector y no de cada libro: quien encuentra su tamano de letra lo quiere en
     * todos. Ver [com.zenlauncher.zen.domain.reading.ReadingSettings].
     */
    val readingSettings: Flow<ReadingSettings>

    suspend fun setReadingSettings(settings: ReadingSettings)

    /** Lectura puntual, para caminos sin composicion (receptor de alarma). */
    suspend fun currentActiveSession(): ActiveSession?

    suspend fun currentRestrictedPackages(): Set<String>
}
