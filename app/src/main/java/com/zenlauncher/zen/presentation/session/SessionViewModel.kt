package com.zenlauncher.zen.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.battery.BatteryReader
import com.zenlauncher.zen.domain.battery.BatterySaverController
import com.zenlauncher.zen.domain.battery.BatteryStatus
import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.SessionProgress
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.model.ZenSession
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import com.zenlauncher.zen.domain.repository.SessionRepository
import com.zenlauncher.zen.domain.session.ZenSessionManager
import com.zenlauncher.zen.presentation.util.ONE_SECOND_MILLIS
import com.zenlauncher.zen.presentation.util.tickerFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionUiState(
    val nowMillis: Long = 0L,
    val active: ActiveSession? = null,
    val progress: SessionProgress? = null,
    val battery: BatteryStatus = BatteryStatus.Unknown,
    val preferredDuration: ZenDuration = ZenDuration.Default,
    val batterySaverEnabled: Boolean = false,
    val batterySaverCapability: BatterySaverController.Capability =
        BatterySaverController.Capability.OBSERVE_ONLY,
)

class SessionViewModel(
    private val sessionManager: ZenSessionManager,
    private val preferences: PreferencesRepository,
    private val sessions: SessionRepository,
    private val battery: BatteryReader,
    private val batterySaver: BatterySaverController,
    private val clock: ZenClock,
) : ViewModel() {

    /**
     * Ultima sesion cerrada y aun no vista, para la pantalla de resumen.
     *
     * Se deriva de una preferencia persistida en lugar de un estado en memoria: la
     * sesion puede cerrarla la alarma con la pantalla apagada y con Zen fuera de
     * primer plano, e incluso con el proceso muerto. El resumen debe esperar al
     * usuario, no perderse.
     */
    val finished: StateFlow<ZenSession?> = preferences.pendingSummarySessionId
        .map { id -> id?.let { sessions.find(it) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    private val _confirmingFinish = MutableStateFlow(false)
    val confirmingFinish: StateFlow<Boolean> = _confirmingFinish.asStateFlow()

    val state: StateFlow<SessionUiState> = combine(
        tickerFlow(ONE_SECOND_MILLIS, clock),
        sessionManager.activeSession,
        battery.observe(),
        preferences.preferredDuration,
        batterySaver.isEnabled,
    ) { now, active, batteryStatus, preferred, saverEnabled ->
        SessionUiState(
            nowMillis = now,
            active = active,
            progress = active?.let { sessionManager.progressNow(it) },
            battery = batteryStatus,
            preferredDuration = preferred,
            batterySaverEnabled = saverEnabled,
            batterySaverCapability = batterySaver.capability,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SessionUiState(nowMillis = clock.wallTimeMillis()),
    )

    /**
     * Cierra la sesion en cuanto el cronometro llega a cero con la pantalla abierta.
     * `resolveExpired` es idempotente, asi que no compite con la alarma.
     */
    fun onTimerReachedZero() {
        viewModelScope.launch { sessionManager.resolveExpired() }
    }

    fun start(duration: ZenDuration) {
        viewModelScope.launch {
            // Empezar una sesion nueva descarta un resumen sin ver: ya no interesa.
            preferences.clearPendingSummary()
            preferences.setPreferredDuration(duration)
            sessionManager.start(duration)
        }
    }

    /** Abandonar exige confirmacion: es el momento de maxima tentacion. */
    fun requestFinish() {
        _confirmingFinish.value = true
    }

    fun cancelFinish() {
        _confirmingFinish.value = false
    }

    fun confirmFinish() {
        viewModelScope.launch {
            _confirmingFinish.value = false
            sessionManager.finishNow()
        }
    }

    fun consumeSummary() {
        viewModelScope.launch { preferences.clearPendingSummary() }
    }

    fun requestBatterySaver(): BatterySaverController.RequestResult = batterySaver.requestEnable()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
