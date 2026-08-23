package com.zenlauncher.zen.domain.media

/**
 * Lectura del ajuste `enabled_notification_listeners`, que es la unica via publica para
 * saber si el usuario nos concedio el acceso al oyente de notificaciones.
 *
 * El ajuste es una cadena de componentes separados por `:`, con la forma
 * `paquete/clase`. Se compara **por paquete** y no por componente exacto porque el
 * nombre de la clase del servicio puede cambiar entre versiones de la aplicacion y el
 * sistema conserva la concesion.
 */
object NotificationListenerAccess {

    fun isGranted(setting: String?, packageName: String): Boolean =
        setting.orEmpty()
            .split(':')
            .filter { it.isNotBlank() }
            .any { it.substringBefore('/') == packageName }
}
