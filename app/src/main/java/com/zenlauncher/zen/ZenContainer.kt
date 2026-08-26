package com.zenlauncher.zen

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.zenlauncher.zen.core.SystemZenClock
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.data.apps.CachedInstalledApps
import com.zenlauncher.zen.data.apps.LauncherAppsRepository
import com.zenlauncher.zen.data.apps.LocalAppRestrictionManager
import com.zenlauncher.zen.data.battery.AndroidBatteryReader
import com.zenlauncher.zen.data.battery.SystemSettingsBatterySaverController
import com.zenlauncher.zen.data.db.SqliteSessionRepository
import com.zenlauncher.zen.data.media.MediaSessionTransport
import com.zenlauncher.zen.data.notes.FileAttachmentStore
import com.zenlauncher.zen.data.news.DoxaNews
import com.zenlauncher.zen.data.notes.SqliteNotesRepository
import com.zenlauncher.zen.data.voice.OnDeviceDictation
import com.zenlauncher.zen.data.notifications.ListenerNotificationsRepository
import com.zenlauncher.zen.data.prefs.DataStorePreferencesRepository
import com.zenlauncher.zen.data.reading.AndroidPdfTextSource
import com.zenlauncher.zen.data.reading.FileBookCoverStore
import com.zenlauncher.zen.data.reading.SqliteBookRepository
import com.zenlauncher.zen.data.usage.UsageStatsRepository
import com.zenlauncher.zen.data.weather.OpenMeteoWeather
import com.zenlauncher.zen.domain.apps.AppRestrictionManager
import com.zenlauncher.zen.domain.apps.SeedEssentialFavourites
import com.zenlauncher.zen.domain.battery.BatteryReader
import com.zenlauncher.zen.domain.battery.BatterySaverController
import com.zenlauncher.zen.domain.media.MediaTransport
import com.zenlauncher.zen.domain.notes.AttachmentStore
import com.zenlauncher.zen.domain.notes.Dictation
import com.zenlauncher.zen.domain.notes.EmbeddingModel
import com.zenlauncher.zen.domain.notes.HeuristicIdeaDevelopmentModel
import com.zenlauncher.zen.domain.notes.IdeaDevelopmentModel
import com.zenlauncher.zen.domain.notes.LexicalEmbedder
import com.zenlauncher.zen.domain.notes.NoteIndexer
import com.zenlauncher.zen.domain.news.NewsRepository
import com.zenlauncher.zen.domain.notes.NotesRepository
import com.zenlauncher.zen.domain.notifications.NotificationsRepository
import com.zenlauncher.zen.domain.reading.BookCoverStore
import com.zenlauncher.zen.domain.reading.BookImporter
import com.zenlauncher.zen.domain.reading.BookRepository
import com.zenlauncher.zen.domain.reading.PdfTextSource
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import com.zenlauncher.zen.domain.repository.SessionRepository
import com.zenlauncher.zen.domain.session.DefaultZenSessionManager
import com.zenlauncher.zen.domain.session.SessionAlarmScheduler
import com.zenlauncher.zen.domain.session.ZenSessionManager
import com.zenlauncher.zen.domain.system.ScreenLocker
import com.zenlauncher.zen.domain.usage.UsageRepository
import com.zenlauncher.zen.domain.weather.WeatherRepository
import com.zenlauncher.zen.system.AlarmSessionScheduler
import com.zenlauncher.zen.system.DeviceAdminScreenLocker
import com.zenlauncher.zen.system.LauncherMemory
import com.zenlauncher.zen.system.ZenNotifications

/**
 * Contenedor de dependencias hecho a mano.
 *
 * Con un solo modulo y una decena de objetos, Hilt anadiria un plugin, generacion de
 * codigo y tiempo de compilacion sin resolver ningun problema que exista aqui. Todo se
 * construye perezosamente y las implementaciones concretas solo se nombran en este
 * fichero: para pasar a Device Owner en v0.2 basta con cambiar estas lineas.
 */
class ZenContainer(context: Context) {

    private val appContext = context.applicationContext

    val clock: ZenClock by lazy { SystemZenClock() }

    /**
     * Scope atado al proceso, no a ninguna pantalla.
     *
     * Existe para el trabajo que **no puede** morir con la pantalla que lo lanzo:
     * guardar una nota mientras se navega a la home, o limpiar sus imagenes al
     * descartarla. `SupervisorJob` para que un fallo en una de esas tareas no cancele
     * las demas.
     */
    val appScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    val preferences: PreferencesRepository by lazy {
        DataStorePreferencesRepository(appContext)
    }

    val sessions: SessionRepository by lazy { SqliteSessionRepository(appContext) }

    /**
     * La lectura de verdad, envuelta en la cache compartida.
     *
     * Media docena de pantallas observan las aplicaciones instaladas; sin
     * [CachedInstalledApps] cada una abria su propia consulta por IPC. Ver esa clase.
     */
    private val launcherApps: CachedInstalledApps by lazy {
        CachedInstalledApps(LauncherAppsRepository(appContext), appScope)
    }

    val installedApps: InstalledAppsRepository get() = launcherApps

    /** Acceso de uso: opcional, lo concede el usuario a mano. Ver [UsageRepository]. */
    val usage: UsageRepository by lazy { UsageStatsRepository(appContext) }

    /**
     * El tiempo. **La unica dependencia de Zen que sale a internet**, y solo lo hace si
     * el usuario eligio una ciudad. Perezosa como todo lo demas: en un telefono sin
     * ciudad elegida, este objeto no llega a construirse.
     */
    val weather: WeatherRepository by lazy { OpenMeteoWeather(clock) }

    /**
     * La portada de noticias. **La segunda dependencia que sale a internet**, y solo lo
     * hace al abrir su pantalla y una vez al dia. Perezosa como todo lo demas: quien no
     * toca NOTICIAS no llega a construir este objeto ni abre una conexion.
     */
    val news: NewsRepository by lazy { DoxaNews(clock) }

    /**
     * La memoria del propio launcher. No mata procesos ajenos —no se puede, ver la
     * clase—: solo decide cuando Zen suelta lo que puede reconstruir.
     */
    val memory: LauncherMemory by lazy { LauncherMemory(listOf(launcherApps)) }

    val notes: NotesRepository by lazy { SqliteNotesRepository(appContext) }

    val noteAttachments: AttachmentStore by lazy { FileAttachmentStore(appContext, clock) }

    /**
     * Motor de vectores, nivel 0: Kotlin puro y cero megabytes.
     *
     * Es la unica linea que hay que cambiar para pasar a EmbeddingGemma: el indice, las
     * conexiones y el buscador hablan con [EmbeddingModel], no con esta clase. Al
     * cambiarlo, los vectores del motor anterior dejan de encontrarse solos —cada uno
     * se guarda con el id de quien lo genero— y `NoteIndexer.sync` reindexa por tandas.
     */
    val embedder: EmbeddingModel by lazy { LexicalEmbedder() }

    val noteIndexer: NoteIndexer by lazy { NoteIndexer(notes, embedder, clock) }

    /**
     * Genera preguntas y enfoques para "Desarrollar una idea".
     *
     * Sin LLM local instalado, es heuristica anclada a datos reales de la propia idea
     * (ver [HeuristicIdeaDevelopmentModel]). El punto de enchufe para un modelo de
     * verdad es esta interfaz, igual que [embedder] lo es para el motor de vectores.
     */
    val ideaDevelopment: IdeaDevelopmentModel by lazy { HeuristicIdeaDevelopmentModel() }

    // Reconocedor de voz del propio dispositivo: sin red, sin modelo empotrado y sin
    // descarga. Si el telefono no lo trae, la fila de dictar no llega a pintarse.
    val dictation: Dictation by lazy { OnDeviceDictation(appContext) }

    val books: BookRepository by lazy { SqliteBookRepository(appContext) }

    val bookCovers: BookCoverStore by lazy { FileBookCoverStore(appContext) }

    /**
     * El lector de PDF del propio Android. **Cero librerias**: `PdfRenderer` sabe extraer
     * el texto desde Android 15 (ver [AndroidPdfTextSource]).
     *
     * Es tambien el punto de enchufe del OCR: un dia que haya reconocimiento de texto
     * local, se envuelve esta implementacion o se sustituye, y ni el analisis estructural
     * ni el lector se enteran.
     */
    val pdfText: PdfTextSource by lazy { AndroidPdfTextSource(appContext) }

    /**
     * Importar un libro vive en el contenedor y no en un ViewModel: leer 400 hojas tarda
     * decenas de segundos y tiene que sobrevivir a que el usuario cierre la biblioteca y
     * se vaya a mirar la hora. Ver [BookImporter].
     */
    val bookImporter: BookImporter by lazy {
        BookImporter(
            pdf = pdfText,
            books = books,
            covers = bookCovers,
            clock = clock,
            scope = appScope,
        )
    }

    val battery: BatteryReader by lazy { AndroidBatteryReader(appContext) }

    val mediaTransport: MediaTransport by lazy { MediaSessionTransport(appContext) }

    // Mismo acceso concedido que los metadatos del reproductor: no hay permiso nuevo.
    val postedNotifications: NotificationsRepository by lazy {
        ListenerNotificationsRepository(appContext)
    }

    val batterySaver: BatterySaverController by lazy {
        SystemSettingsBatterySaverController(appContext)
    }

    // v0.2: sustituir por DevicePolicyAppRestrictionManager (setPackagesSuspended).
    val restrictions: AppRestrictionManager by lazy {
        LocalAppRestrictionManager(preferences)
    }

    val seedEssentialFavourites: SeedEssentialFavourites by lazy {
        SeedEssentialFavourites(preferences, installedApps, restrictions)
    }

    val notifications: ZenNotifications by lazy { ZenNotifications(appContext) }

    val screenLocker: ScreenLocker by lazy { DeviceAdminScreenLocker(appContext) }

    private val alarms: SessionAlarmScheduler by lazy { AlarmSessionScheduler(appContext) }

    val sessionManager: ZenSessionManager by lazy {
        DefaultZenSessionManager(
            preferences = preferences,
            sessions = sessions,
            battery = battery,
            restrictions = restrictions,
            alarms = alarms,
            clock = clock,
        )
    }
}
