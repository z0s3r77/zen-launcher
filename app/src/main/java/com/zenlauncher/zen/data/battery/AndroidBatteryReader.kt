package com.zenlauncher.zen.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.zenlauncher.zen.domain.battery.BatteryCharging
import com.zenlauncher.zen.domain.battery.BatteryReader
import com.zenlauncher.zen.domain.battery.BatteryStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/**
 * `BATTERY_PROPERTY_CAPACITY` e `isCharging()` son publicas y no requieren permisos.
 */
class AndroidBatteryReader(context: Context) : BatteryReader {

    private val appContext = context.applicationContext
    private val batteryManager = appContext.getSystemService(BatteryManager::class.java)

    override fun current(): BatteryStatus {
        // ACTION_BATTERY_CHANGED es pegajosa: registrarse con receptor nulo devuelve al
        // momento el ultimo valor, con los mismos extras que recibe el receptor.
        val sticky = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        sticky?.readStatus()?.let { return it }

        val manager = batteryManager ?: return BatteryStatus.Unknown
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (percent !in 0..100) return BatteryStatus.Unknown
        return BatteryStatus(percent = percent, charging = manager.isCharging)
    }

    /**
     * El estado de carga sale de los extras de la difusion y no de
     * `BatteryManager.isCharging`: ver [BatteryCharging].
     */
    private fun Intent.readStatus(): BatteryStatus? {
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return BatteryStatus(
            percent = (level * 100f / scale).roundToInt().coerceIn(0, 100),
            charging = BatteryCharging.isCharging(
                status = getIntExtra(BatteryManager.EXTRA_STATUS, -1),
                plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            ),
        )
    }

    override fun observe(): Flow<BatteryStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(intent?.readStatus() ?: current())
            }
        }
        trySend(current())
        // ACTION_BATTERY_CHANGED es un broadcast protegido del sistema: NOT_EXPORTED es
        // lo correcto y ademas obligatorio desde Android 14.
        appContext.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { appContext.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
