package com.zenlauncher.zen

import android.app.Application

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
}
