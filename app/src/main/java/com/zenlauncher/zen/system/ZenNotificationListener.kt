package com.zenlauncher.zen.system

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.zenlauncher.zen.domain.notifications.AppNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Oyente de notificaciones. Tiene dos trabajos y ninguno de los dos es avisar.
 *
 * 1. **Metadatos del reproductor**: `MediaSessionManager.getActiveSessions` exige el
 *    nombre de un oyente habilitado como prueba de la concesion. No hay otra via
 *    publica para leer titulo, artista y caratula.
 * 2. **Marcas de notificacion en la pantalla de inicio**: el numero al lado de una
 *    aplicacion y la lista que se abre al tocarlo.
 *
 * El segundo trabajo es nuevo y hay que decir en que se diferencia de la barra de
 * estado que Zen oculta a proposito: aqui **nada interrumpe**. No hay sonido, ni
 * vibracion, ni algo que aparezca encima de lo que estabas haciendo; el numero espera
 * quieto a que lo mires, y solo lo ves si vas a la pantalla de inicio. Se cuentan solo
 * las notificaciones que el usuario contaria (ver `NotificationBadges`) y de las
 * aplicaciones restringidas no se ensena ninguna.
 *
 * Nada sale del dispositivo y nada se guarda: el estado vive en memoria, en
 * [notifications], y muere con el proceso. El acceso sigue siendo **opcional**: sin el,
 * el sistema no enlaza este servicio, la lista se queda vacia y todo lo demas funciona.
 */
class ZenNotificationListener : NotificationListenerService() {

    /**
     * Al enlazar llega el panel entero de golpe: sin esto, la lista se quedaria vacia
     * hasta que alguien publicase algo nuevo, que puede tardar horas.
     */
    override fun onListenerConnected() {
        publish()
    }

    /**
     * El sistema desenlaza al revocar el acceso. Vaciar aqui evita que la pantalla de
     * inicio siga ensenando marcas de un panel que ya no se puede leer.
     */
    override fun onListenerDisconnected() {
        state.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        publish()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        publish()
    }

    /**
     * Se relee el panel entero en vez de sumar y restar sobre la lista guardada: el
     * sistema tambien retira notificaciones sin avisar (al desbloquear, al limpiar
     * todo), y una cuenta incremental se quedaria alta para siempre.
     */
    private fun publish() {
        val current = try {
            activeNotifications
        } catch (error: SecurityException) {
            // Puede llegar un evento entre la revocacion y el desenlace.
            Log.w(TAG, "El sistema rechazo leer las notificaciones activas", error)
            null
        }
        state.value = current.orEmpty()
            // Las propias no: el aviso de fin de sesion no es un pendiente que atender.
            .filterNot { it.packageName == packageName }
            .map { it.toAppNotification() }
    }

    private fun StatusBarNotification.toAppNotification(): AppNotification {
        val extras = notification.extras
        return AppNotification(
            key = key,
            packageName = packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            postTime = postTime,
            ongoing = isOngoing,
            groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
    }

    companion object {
        private const val TAG = "ZenNotificationListener"

        private val state = MutableStateFlow<List<AppNotification>>(emptyList())

        /**
         * Estado compartido en un `companion` porque el sistema construye el servicio
         * cuando quiere y la aplicacion no tiene ninguna referencia a esa instancia.
         * Vive en el mismo proceso que la interfaz, asi que el flujo basta: no hay IPC
         * que montar ni un enlace que mantener vivo.
         */
        val notifications: StateFlow<List<AppNotification>> = state.asStateFlow()
    }
}
