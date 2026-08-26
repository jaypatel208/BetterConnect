package dev.jay.betterconnect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import dagger.hilt.android.AndroidEntryPoint
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.model.ConnectionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the process alive while the cluster link is held.
 *
 * Without this the heartbeat stops when the app is backgrounded and the cluster silently
 * goes stale - and because nothing is ever acknowledged, there would be no signal that it
 * had happened.
 */
@AndroidEntryPoint
class ClusterService : LifecycleService() {

    @Inject lateinit var controller: ClusterController

    override fun onCreate() {
        super.onCreate()
        createChannel()
        lifecycleScope.launch {
            controller.state.collectLatest { state ->
                notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                if (state is ConnectionState.Disconnected || state is ConnectionState.Idle) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(controller.state.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(controller.state.value))
        }
        return START_STICKY
    }

    private fun buildNotification(state: ConnectionState): Notification {
        val text = when (state) {
            ConnectionState.Idle -> "Idle"
            is ConnectionState.Connecting -> "Connecting to ${state.address}"
            is ConnectionState.Discovering -> "Discovering services"
            is ConnectionState.Ready -> "Linked - heartbeat running"
            is ConnectionState.Unsupported -> state.reason.message
            is ConnectionState.Disconnected -> "Disconnected"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cluster link")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Cluster link",
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "cluster_link"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ClusterService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClusterService::class.java))
        }
    }
}
