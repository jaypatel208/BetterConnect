package dev.jay.betterconnect.core.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The `full` flavour's permission surface, split by whether the app can function without it.
 *
 * `required` gates onboarding: the cluster link and the guidance loop cannot run at all
 * without these. `enhanced` backs the call/message-alert features (`MISSED_CALL`,
 * `ALERTS_INFO`) and is requested in the same flow but never blocks reaching the app - the
 * two Settings-granted roles those features also need have no runtime dialog at all, see
 * [SpecialAccess].
 */
object FullPermissions {

    val required: List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val enhanced: List<String> = listOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
    )

    fun missingRequired(context: Context): List<String> = required.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    fun allRequiredGranted(context: Context): Boolean = missingRequired(context).isEmpty()

    fun missingEnhanced(context: Context): List<String> = enhanced.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
}
