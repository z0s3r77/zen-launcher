package com.zenlauncher.zen.domain.system

import android.content.Intent

/**
 * Apagar la pantalla desde la aplicacion.
 *
 * Solo hay dos vias publicas en Android 16, comprobado contra `android-36.1/android.jar`:
 * `AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN`, descartada por decision de producto,
 * y `DevicePolicyManager.lockNow()` con un **administrador de dispositivos** activo.
 *
 * El administrador de dispositivos NO es Device Owner: se concede con un dialogo normal
 * del sistema, se revoca desde Ajustes cuando se quiera y no requiere ni adb ni
 * restablecer el telefono. La unica politica que Zen declara es `force-lock`.
 */
interface ScreenLocker {

    /** Si hoy se puede bloquear, es decir, si el administrador esta activo. */
    fun canLock(): Boolean

    /** @return false si el sistema lo rechaza; nunca lanza. */
    fun lock(): Boolean

    /** Dialogo del sistema para conceder el permiso. */
    fun enableIntent(): Intent

    /** Pantalla de ajustes donde el usuario puede revocarlo. */
    fun disableIntent(): Intent
}
