package com.zenlauncher.zen.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zenlauncher.zen.ZenApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Cierra la sesion cuando vence su tiempo y avisa.
 *
 * `resolveExpired` es idempotente, asi que no pasa nada si la UI ya la habia cerrado
 * al volver a primer plano justo antes de que llegara la alarma.
 */
class SessionEndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SESSION_END) return

        val container = (context.applicationContext as? ZenApplication)?.container ?: return
        val pending = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Un BroadcastReceiver dispone de unos 10 s; se corta antes para no
                // provocar un ANR si el almacenamiento va lento.
                withTimeout(RESOLVE_TIMEOUT_MILLIS) {
                    val finished = container.sessionManager.resolveExpired()
                    if (finished != null) {
                        container.notifications.notifySessionFinished(
                            actualDurationMillis = finished.actualDurationMillis,
                            completed = finished.completed,
                        )
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo cerrar la sesion desde la alarma", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SESSION_END = "com.zenlauncher.zen.action.SESSION_END"
        private const val TAG = "SessionEndReceiver"
        private const val RESOLVE_TIMEOUT_MILLIS = 8_000L
    }
}
