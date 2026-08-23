package com.zenlauncher.zen.core

import android.os.SystemClock

/**
 * Dos relojes, porque ninguno basta por si solo:
 *
 * - [wallTimeMillis] sobrevive al reinicio pero puede saltar si el usuario o la red
 *   cambian la hora del dispositivo.
 * - [elapsedRealtimeMillis] es monotono y no se puede manipular, pero se reinicia
 *   con el dispositivo.
 *
 * Las sesiones guardan ambos y el calculo elige cual es fiable en cada situacion.
 * Es una interfaz para poder falsearlo en los tests sin Robolectric.
 */
interface ZenClock {
    fun wallTimeMillis(): Long

    fun elapsedRealtimeMillis(): Long
}

class SystemZenClock : ZenClock {
    override fun wallTimeMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
