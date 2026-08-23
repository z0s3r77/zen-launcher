package com.zenlauncher.zen.domain.notifications

/**
 * Que cuenta como aviso y que no.
 *
 * Un launcher que ensena un numero al lado de cada aplicacion tiene que ensenar el
 * numero **que el usuario contaria**: los mensajes sin leer. El sistema mezcla en la
 * misma lista cosas que no son avisos —el reproductor, una descarga, la VPN— y ademas
 * duplica cada grupo con una cabecera que repite lo que dicen sus hijas. Contarlo todo
 * daria un "7" en Spotify por estar sonando, que es justo el ruido que Zen quita.
 *
 * Funcion pura y en el dominio para poder fijar esas reglas sin dispositivo.
 */
object NotificationBadges {

    /** Las que merecen sumar en la marca de una aplicacion. */
    fun countable(notification: AppNotification): Boolean =
        !notification.ongoing && !notification.groupSummary && notification.hasContent

    /** Cuantos avisos tiene cada paquete. Los paquetes sin ninguno no aparecen. */
    fun countByPackage(notifications: List<AppNotification>): Map<String, Int> =
        notifications.filter(::countable)
            .groupingBy { it.packageName }
            .eachCount()

    /**
     * Las de un paquete, de la mas reciente a la mas antigua: al abrir la lista se
     * busca lo ultimo que ha llegado, no lo que lleva ahi desde ayer.
     */
    fun forPackage(notifications: List<AppNotification>, packageName: String): List<AppNotification> =
        notifications.filter { it.packageName == packageName && countable(it) }
            .sortedByDescending { it.postTime }
}
