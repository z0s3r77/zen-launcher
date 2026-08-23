package com.zenlauncher.zen.domain.notifications

import android.content.Intent
import kotlinx.coroutines.flow.Flow

/**
 * Lo que hay ahora mismo en el panel de notificaciones del sistema.
 *
 * Depende del **mismo acceso opcional** que los metadatos del reproductor
 * (`BIND_NOTIFICATION_LISTENER_SERVICE`, concedido a mano en Ajustes de Android): no se
 * pide ningun permiso nuevo. Sin conceder, [observeNotifications] emite una lista vacia
 * y la pantalla lo dice en lugar de fingir que no hay avisos.
 */
interface NotificationsRepository {

    fun observeNotifications(): Flow<List<AppNotification>>

    /** Si hoy se pueden leer. */
    fun hasAccess(): Boolean

    /** Pantalla del sistema donde se concede o se revoca ese acceso. */
    fun accessIntent(): Intent
}
