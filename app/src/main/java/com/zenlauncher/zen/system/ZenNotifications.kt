package com.zenlauncher.zen.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.model.formatDurationCompact
import com.zenlauncher.zen.presentation.ZenActivity

/**
 * La unica notificacion de Zen: la sesion ha terminado.
 *
 * Canal de importancia baja y sin sonido a proposito. Una app que existe para dejar de
 * reclamar atencion no puede permitirse un aviso llamativo, y tampoco hay servicio en
 * primer plano con cuenta atras viva: seria una fuente de estimulo permanente.
 */
class ZenNotifications(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_SESSION,
            appContext.getString(R.string.notification_channel_session),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = appContext.getString(R.string.notification_channel_session_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager?.createNotificationChannel(channel)
    }

    /** No hace nada si el usuario no concedio el permiso: no es un camino de error. */
    fun notifySessionFinished(actualDurationMillis: Long, completed: Boolean) {
        if (!canPost()) return
        ensureChannel()

        val open = PendingIntent.getActivity(
            appContext,
            REQUEST_OPEN,
            Intent(appContext, ZenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = appContext.getString(
            if (completed) R.string.notification_session_completed
            else R.string.notification_session_ended,
        )

        val notification = Notification.Builder(appContext, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification_zen)
            .setContentTitle(title)
            .setContentText(formatDurationCompact(actualDurationMillis))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()

        manager?.notify(NOTIFICATION_SESSION_FINISHED, notification)
    }

    private fun canPost(): Boolean {
        val granted = appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && manager?.areNotificationsEnabled() == true
    }

    private companion object {
        const val CHANNEL_SESSION = "zen_session"
        const val NOTIFICATION_SESSION_FINISHED = 1
        const val REQUEST_OPEN = 100
    }
}
