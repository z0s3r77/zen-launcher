package com.zenlauncher.zen

import android.content.Context
import com.zenlauncher.zen.core.SystemZenClock
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.data.apps.LauncherAppsRepository
import com.zenlauncher.zen.data.apps.LocalAppRestrictionManager
import com.zenlauncher.zen.data.battery.AndroidBatteryReader
import com.zenlauncher.zen.data.battery.SystemSettingsBatterySaverController
import com.zenlauncher.zen.data.db.SqliteSessionRepository
import com.zenlauncher.zen.data.media.MediaSessionTransport
import com.zenlauncher.zen.data.notifications.ListenerNotificationsRepository
import com.zenlauncher.zen.data.prefs.DataStorePreferencesRepository
import com.zenlauncher.zen.domain.apps.AppRestrictionManager
import com.zenlauncher.zen.domain.apps.SeedEssentialFavourites
import com.zenlauncher.zen.domain.battery.BatteryReader
import com.zenlauncher.zen.domain.battery.BatterySaverController
import com.zenlauncher.zen.domain.media.MediaTransport
import com.zenlauncher.zen.domain.notifications.NotificationsRepository
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import com.zenlauncher.zen.domain.repository.SessionRepository
import com.zenlauncher.zen.domain.session.DefaultZenSessionManager
import com.zenlauncher.zen.domain.session.SessionAlarmScheduler
import com.zenlauncher.zen.domain.session.ZenSessionManager
import com.zenlauncher.zen.domain.system.ScreenLocker
import com.zenlauncher.zen.system.AlarmSessionScheduler
import com.zenlauncher.zen.system.DeviceAdminScreenLocker
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

    val preferences: PreferencesRepository by lazy {
        DataStorePreferencesRepository(appContext)
    }

    val sessions: SessionRepository by lazy { SqliteSessionRepository(appContext) }

    val installedApps: InstalledAppsRepository by lazy { LauncherAppsRepository(appContext) }

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
