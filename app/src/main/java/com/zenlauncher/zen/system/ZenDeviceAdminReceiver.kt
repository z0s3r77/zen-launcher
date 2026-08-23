package com.zenlauncher.zen.system

import android.app.admin.DeviceAdminReceiver

/**
 * Receptor exigido por el sistema para poder ser administrador de dispositivos.
 *
 * No implementa ninguna politica: Zen solo declara `force-lock`, y la unica razon de
 * pedirlo es poder apagar la pantalla con un doble toque sin usar el boton fisico.
 * Deliberadamente vacio: cualquier cosa que se anadiera aqui seria una capacidad que
 * el usuario no ha pedido.
 */
class ZenDeviceAdminReceiver : DeviceAdminReceiver()
