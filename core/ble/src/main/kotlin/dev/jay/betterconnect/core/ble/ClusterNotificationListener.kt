package dev.jay.betterconnect.core.ble

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Registered now so "Notification access" is visible and grantable from the onboarding
 * flow. Reading a posted notification's text into `ALERTS_INFO` (`0410`) custom text is
 * future work - this never acts on a notification, it only observes.
 */
class ClusterNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit
}
