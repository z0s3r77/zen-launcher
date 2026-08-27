package com.zenlauncher.zen.presentation.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.domain.apps.AppRestrictionManager
import com.zenlauncher.zen.domain.notifications.NotificationGroup
import com.zenlauncher.zen.domain.notifications.NotificationGrouping
import com.zenlauncher.zen.domain.notifications.NotificationsRepository
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val groups: List<NotificationGroup> = emptyList(),
    /** false cuando el acceso no esta concedido: la pantalla lo dice en vez de callar. */
    val hasAccess: Boolean = false,
)

class NotificationsViewModel(
    private val notifications: NotificationsRepository,
    private val installedApps: InstalledAppsRepository,
    restrictions: AppRestrictionManager,
) : ViewModel() {

    val state: StateFlow<NotificationsUiState> = combine(
        notifications.observeNotifications(),
        installedApps.observeInstalledApps(),
        restrictions.restrictedPackages,
    ) { posted, apps, restricted ->
        NotificationsUiState(
            groups = NotificationGrouping.group(
                notifications = posted,
                labels = apps.associate { it.packageName to it.label },
                // Una restringida desaparece de Zen por completo: ensenar sus avisos
                // seria la puerta trasera que la restriccion existe para cerrar.
                hidden = restricted,
            ),
            hasAccess = notifications.hasAccess(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = NotificationsUiState(hasAccess = notifications.hasAccess()),
    )

    /**
     * Tocar un aviso abre su aplicacion, no la accion que la notificacion trae dentro:
     * Zen no guarda el `PendingIntent`, asi que no hay forma de acabar en una pantalla
     * que no se pidio.
     */
    fun open(packageName: String) {
        viewModelScope.launch { installedApps.launchPackage(packageName) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
