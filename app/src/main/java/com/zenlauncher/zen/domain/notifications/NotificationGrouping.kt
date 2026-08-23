package com.zenlauncher.zen.domain.notifications

/**
 * Las notificaciones de una aplicacion, ya listas para pintarse.
 *
 * [label] es el nombre visible de la aplicacion; si no esta instalada o el sistema no
 * lo da, se cae al nombre del paquete antes que ensenar un hueco.
 */
data class NotificationGroup(
    val packageName: String,
    val label: String,
    val notifications: List<AppNotification>,
) {
    val count: Int get() = notifications.size

    /** Marca el orden del grupo: lo ultimo que llego manda. */
    val mostRecent: Long get() = notifications.maxOfOrNull { it.postTime } ?: 0L
}

/**
 * Agrupa por aplicacion lo que el sistema entrega en una lista plana.
 *
 * El panel de notificaciones de Android ordena por importancia, que es una senal que la
 * propia aplicacion influye. Aqui manda el tiempo y nada mas: quien pueda gritar mas
 * fuerte no sube puestos.
 */
object NotificationGrouping {

    fun group(
        notifications: List<AppNotification>,
        labels: Map<String, String>,
        hidden: Set<String> = emptySet(),
    ): List<NotificationGroup> = notifications
        .filter { NotificationBadges.countable(it) && it.packageName !in hidden }
        .groupBy { it.packageName }
        .map { (packageName, items) ->
            NotificationGroup(
                packageName = packageName,
                label = labels[packageName] ?: packageName,
                notifications = items.sortedByDescending { it.postTime },
            )
        }
        .sortedByDescending { it.mostRecent }
}
