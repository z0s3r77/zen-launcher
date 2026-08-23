package com.zenlauncher.zen.system

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.system.ScreenLocker

class DeviceAdminScreenLocker(context: Context) : ScreenLocker {

    private val appContext = context.applicationContext
    private val policyManager = appContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(appContext, ZenDeviceAdminReceiver::class.java)

    override fun canLock(): Boolean = policyManager?.isAdminActive(admin) == true

    override fun lock(): Boolean {
        if (!canLock()) return false
        return try {
            policyManager?.lockNow()
            true
        } catch (error: SecurityException) {
            // El administrador puede revocarse entre la comprobacion y la llamada.
            Log.w(TAG, "El sistema rechazo el bloqueo", error)
            false
        }
    }

    override fun enableIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                appContext.getString(R.string.device_admin_explanation),
            )

    override fun disableIntent(): Intent =
        Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val TAG = "DeviceAdminScreenLocker"
    }
}
