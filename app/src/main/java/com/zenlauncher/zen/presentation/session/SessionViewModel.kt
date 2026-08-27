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

/**
 * Lo que ensena la pantalla de sesion **mientras la sesion corre**.
 *
 * Aqui vivian ademas `preferredDuration` y dos campos del ahorro de bateria. Se
 * sacaron: los dos del ahorro no los leia nadie —solo Ajustes, que tiene su propio
 * estado— y la duracion preferida la usa la pantalla de preparacion, que no necesita
 * ni cronometro ni bateria. Mientras estuvieron aqui, colectar este estado obligaba a
 * mantener vivo el receptor de `ACTION_POWER_SAVE_MODE_CHANGED` para nada.
 */
data class SessionUiState(
    val nowMillis: Long = 0L,
    val active: ActiveSession? = null,
    val progress: SessionProgress? = null,
    val battery: BatteryStatus = BatteryStatus.Unknown,
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

    /**
     * **Si hay sesion, y nada mas.** Es lo unico que la Activity necesita saber para
     * decidir si la sesion sustituye a la pantalla entera.
     *
     * Existe aparte de [state] por el motivo mas importante de todo este fichero: la
     * Activity colecta esto **siempre**, en la home y en cualquier otra pantalla. Antes
     * colectaba [state], que empieza por un `tickerFlow` de un segundo, asi que el
     * launcher despertaba el hilo principal una vez por segundo —para siempre, tambien
     * con la pantalla de inicio quieta y sin ninguna sesion— para recomponer un reloj
     * que solo cambia cada minuto. Ademas arrastraba el receptor de bateria (ver
     * [state]).
     *
     * Este flujo solo emite cuando la sesion empieza o termina, que son un punado de
     * veces al dia.
     */
    val active: StateFlow<ActiveSession?> = sessionManager.activeSession
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    /**
     * La duracion elegida, para la pantalla de preparacion.
     *
     * Aparte de [state] porque preparar una sesion no necesita ni cronometro ni bateria:
     * colectando el estado completo, abrir "empezar una sesion" encendia el latido de un
     * segundo y registraba el receptor de bateria solo para pintar cual de los cinco
     * botones esta seleccionado.
     */
    val preferredDuration: StateFlow<ZenDuration> = preferences.preferredDuration
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ZenDuration.Default,
        )

    /**
     * El cronometro y la bateria, **solo mientras se mira la sesion**.
     *
     * Lo colecta unicamente `ActiveSessionScreen`, asi que tanto el latido de un segundo
     * como el receptor de `ACTION_BATTERY_CHANGED` existen exactamente durante la sesion
     * y ni un momento mas. Ese receptor es de los que mas se emiten en Android —cada
     * cambio de nivel, de temperatura o de voltaje—, y aqui si tiene a quien informar:
     * la sesion ensena el porcentaje.
     */
    val state: StateFlow<SessionUiState> = combine(
        tickerFlow(ONE_SECOND_MILLIS, clock),
        sessionManager.activeSession,
        battery.observe(),
    ) { now, active, batteryStatus ->
        SessionUiState(
            nowMillis = now,
            active = active,
            progress = active?.let { sessionManager.progressNow(it) },
            battery = batteryStatus,
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
