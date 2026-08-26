package com.zenlauncher.zen

import android.app.Application

/**
 * `onTrimMemory` es la unica via por la que Android avisa a una aplicacion de que va
 * escaso de memoria, y para un launcher es informacion valiosa: el proceso que ocupa
 * menos es el que sobrevive, y un launcher muerto deja el telefono sin pantalla de
 * inicio hasta que vuelve a arrancar entero. Que se hace con cada aviso lo decide
 * `MemoryTrimPolicy`, y no es lo que parece: ver `LauncherMemory`.
 */
class ZenApplication : Application() {

    lateinit var container: ZenContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = ZenContainer(this)
        // Crear el canal es barato e idempotente; hacerlo aqui evita que el primer
        // aviso llegue sin canal si la sesion la cierra la alarma.
        container.notifications.ensureChannel()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        container.memory.onTrimMemory(level)
    }
}
