package com.zenlauncher.zen.domain.battery

import android.content.Intent
import kotlinx.coroutines.flow.Flow

/**
 * Frontera hacia v0.2.
 *
 * **Android no permite a una aplicacion normal activar el ahorro de bateria.**
 * Comprobado contra `android-36.1/android.jar`: `PowerManager` publico expone
 * `isPowerSaveMode()` y `ACTION_POWER_SAVE_MODE_CHANGED`, pero
 * `setPowerSaveModeEnabled()` no esta en el SDK publico (es @SystemApi y exige
 * `DEVICE_POWER`). `Settings.ACTION_VOICE_CONTROL_BATTERY_SAVER_MODE` existe pero solo
 * puede lanzarse desde una sesion de asistente de voz.
 *
 * Por eso v0.1 **lee** el estado y, como mucho, **abre los ajustes** para que lo active
 * el usuario. No hay atajo seguro y no se intenta ninguno.
 */
interface BatterySaverController {

    val isEnabled: Flow<Boolean>

    fun currentlyEnabled(): Boolean

    /** Que puede hacer realmente esta implementacion. */
    val capability: Capability

    /**
     * Intenta activarlo. En v0.1 devuelve siempre [RequestResult.RequiresUserAction]
     * con el intent de ajustes, o [RequestResult.AlreadyEnabled].
     */
    fun requestEnable(): RequestResult

    enum class Capability {
        /** Solo lectura + envio a ajustes. Lo unico posible sin privilegios. */
        OBSERVE_ONLY,

        /** Reservado a v0.2. */
        CAN_TOGGLE,
    }

    sealed interface RequestResult {
        data object AlreadyEnabled : RequestResult

        /** El usuario debe activarlo; [intent] abre la pantalla correcta. */
        data class RequiresUserAction(val intent: Intent) : RequestResult

        /** Ni siquiera hay pantalla de ajustes a la que enviar (ROM recortada). */
        data object Unsupported : RequestResult
    }
}
