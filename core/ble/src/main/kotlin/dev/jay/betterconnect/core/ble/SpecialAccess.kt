package dev.jay.betterconnect.core.ble

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * [CallScreeningRoleService] and [ClusterNotificationListener] are Settings-granted roles,
 * not runtime permissions - Android has no API to pop a system dialog for notification
 * access, so the app can only deep-link to Settings and re-check on resume. The
 * call-screening role is the one exception: [RoleManager] does have a request-role intent
 * that shows a system dialog.
 */
object SpecialAccess {

    fun isNotificationAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun notificationAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun isCallScreeningRoleHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    fun callScreeningRoleRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }
}
