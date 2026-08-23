package com.zenlauncher.zen.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.session.SessionAlarmScheduler

/**
 * Una sola alarma por sesion, cancelada al terminar.
 *
 * Degrada sola: si el usuario no concedio alarmas exactas se usa una inexacta, y si
 * ni siquiera esa llega, la sesion se cierra igualmente la proxima vez que se abre
 * Zen (`ZenSessionManager.resolveExpired`). La correccion del cronometro nunca depende
 * de que la alarma dispare.
 */
class AlarmSessionScheduler(
    context: Context,
) : SessionAlarmScheduler {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(session: ActiveSession) {
        val manager = alarmManager ?: return
        val triggerAt = session.plannedEndWallMillis
        val operation = pendingIntent()

        try {
            if (manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (error: SecurityException) {
            // El permiso puede revocarse entre la comprobacion y la llamada.
            Log.w(TAG, "Sin permiso para alarma exacta; se usa inexacta", error)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    override fun cancel() {
        alarmManager?.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        REQUEST_SESSION_END,
        Intent(appContext, SessionEndReceiver::class.java)
            .setAction(SessionEndReceiver.ACTION_SESSION_END),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val TAG = "AlarmSessionScheduler"
        const val REQUEST_SESSION_END = 200
    }
}
