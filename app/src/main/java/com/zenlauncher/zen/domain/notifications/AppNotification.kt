package com.zenlauncher.zen.domain.notifications

/**
 * Una notificacion publicada por otra aplicacion, reducida a lo que Zen ensena.
 *
 * No se guarda el `Notification` del sistema ni su `PendingIntent`: Zen no dispara la
 * accion de la notificacion, solo abre la aplicacion que la publico. Asi el modelo es
 * un dato plano, comprobable sin Android, y no hay forma de que un toque acabe en una
 * pantalla que el usuario no pidio.
 *
 * [ongoing] y [groupSummary] viajan aqui en lugar de filtrarse al leerlas para que la
 * decision de que cuenta como aviso viva en el dominio, donde se puede probar; ver
 * [NotificationBadges].
 */
data class AppNotification(
    /** Identificador del sistema: distingue dos avisos con el mismo texto. */
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    /** Persistente: reproductor, descarga, VPN. No es un aviso, es un estado. */
    val ongoing: Boolean = false,
    /** Cabecera de un grupo: repite lo que ya dicen sus hijas. */
    val groupSummary: Boolean = false,
) {
    /** Sin titulo ni texto no hay nada que leer: una fila vacia es peor que ninguna. */
    val hasContent: Boolean get() = title.isNotBlank() || text.isNotBlank()
}
