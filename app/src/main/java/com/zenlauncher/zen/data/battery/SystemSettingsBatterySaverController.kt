package com.zenlauncher.zen.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import com.zenlauncher.zen.domain.battery.BatterySaverController
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Implementacion de v0.1: **observar y, como mucho, abrir los ajustes**.
 *
 * No hay forma publica de activar el ahorro de bateria desde una aplicacion normal
 * (ver [BatterySaverController]). Aqui no se intenta ningun atajo: ni shell, ni
 * reflexion sobre @SystemApi, ni servicios de accesibilidad.
 */
class SystemSettingsBatterySaverController(
    context: Context,
) : BatterySaverController {

    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(PowerManager::class.java)

    override val capability = BatterySaverController.Capability.OBSERVE_ONLY

    override fun currentlyEnabled(): Boolean = powerManager?.isPowerSaveMode == true

    override val isEnabled: Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(currentlyEnabled())
            }
        }
        trySend(currentlyEnabled())
        appContext.registerReceiver(
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { appContext.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    override fun requestEnable(): BatterySaverController.RequestResult {
        if (currentlyEnabled()) return BatterySaverController.RequestResult.AlreadyEnabled

        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Algunas ROM recortan pantallas de ajustes; sin actividad que resuelva, mejor
        // decirlo que lanzar y provocar un ActivityNotFoundException.
        val resolves = intent.resolveActivity(appContext.packageManager) != null
        return if (resolves) {
            BatterySaverController.RequestResult.RequiresUserAction(intent)
        } else {
            BatterySaverController.RequestResult.Unsupported
        }
    }
}
