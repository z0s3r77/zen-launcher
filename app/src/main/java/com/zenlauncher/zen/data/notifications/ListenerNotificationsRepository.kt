package com.zenlauncher.zen.data.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import com.zenlauncher.zen.domain.media.NotificationListenerAccess
import com.zenlauncher.zen.domain.notifications.AppNotification
import com.zenlauncher.zen.domain.notifications.NotificationsRepository
import com.zenlauncher.zen.system.ZenNotificationListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

/**
 * Lee lo que el oyente ya tiene en memoria.
 *
 * No consulta al sistema por su cuenta: el servicio es el unico que puede llamar a
 * `getActiveNotifications`, y preguntar dos veces solo daria dos versiones distintas de
 * la misma lista. Aqui solo se expone su flujo y se responde por la concesion.
 */
class ListenerNotificationsRepository(context: Context) : NotificationsRepository {

    private val appContext = context.applicationContext

    override fun observeNotifications(): Flow<List<AppNotification>> =
        ZenNotificationListener.notifications.onStart {
            // El estado vive en el proceso, asi que al arrancar en frio esta vacio y no
            // se llena hasta que el sistema enlaza el oyente. Tras una actualizacion de
            // la aplicacion ese enlace puede no llegar solo, y entonces las marcas se
            // quedarian a cero hasta la siguiente notificacion, que puede tardar horas.
            // `requestRebind` es la via oficial para pedirlo; si ya esta enlazado, no
            // hace nada.
            if (hasAccess() && ZenNotificationListener.notifications.value.isEmpty()) {
                requestRebind()
            }
        }

    private fun requestRebind() {
        try {
            NotificationListenerService.requestRebind(
                ComponentName(appContext, ZenNotificationListener::class.java),
            )
        } catch (error: SecurityException) {
            // El acceso pudo revocarse entre la comprobacion y la llamada.
            Log.w(TAG, "El sistema rechazo volver a enlazar el oyente", error)
        }
    }

    override fun hasAccess(): Boolean = NotificationListenerAccess.isGranted(
        setting = Settings.Secure.getString(
            appContext.contentResolver,
            ENABLED_NOTIFICATION_LISTENERS,
        ),
        packageName = appContext.packageName,
    )

    override fun accessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val TAG = "ZenNotifications"

        /** `Settings.Secure.ENABLED_NOTIFICATION_LISTENERS` es `@hide`; ver MediaSessionTransport. */
        const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
    }
}
