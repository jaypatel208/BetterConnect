package dev.jay.betterconnect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.jay.betterconnect.core.data.AutoConnector
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.data.GuidanceController
import dev.jay.betterconnect.core.data.RideLog
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.GuidanceState
import dev.jay.betterconnect.core.model.NavState
import dev.jay.betterconnect.core.model.RoutePlan
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the process alive while the cluster link is held, and - once [GuidanceController] has
 * an active route - while the guidance loop runs.
 *
 * Without this the heartbeat stops when the app is backgrounded and the cluster silently
 * goes stale - and because nothing is ever acknowledged, there would be no signal that it
 * had happened.
 */
@AndroidEntryPoint
class ClusterService : LifecycleService() {

    @Inject lateinit var controller: ClusterController

    @Inject lateinit var autoConnector: AutoConnector

    @Inject lateinit var guidanceController: GuidanceController

    @Inject lateinit var rideLog: RideLog

    override fun onCreate() {
        super.onCreate()
        createChannel()
        rideLog.attach(controller.log)
        autoConnector.start(lifecycleScope)

        lifecycleScope.launch {
            controller.state.collectLatest { state ->
                // Idle is the bootstrap state before connect() is ever called - it is not a
                // finished session. Stopping on it races startForeground() in onStartCommand()
                // and can tear the service down before it ever reaches the foreground state.
                if (state is ConnectionState.Disconnected) stopSelf()
            }
        }

        lifecycleScope.launch {
            combine(
                controller.state,
                guidanceController.activePlan,
                guidanceController.guidanceState,
                guidanceController.lastNavState,
            ) { state, plan, guidance, nav -> Notice(state, plan, guidance, nav) }
                .collectLatest { notice ->
                    // A route just started or ended: re-declare the foreground service type
                    // so LOCATION is only ever claimed while actually navigating.
                    startForegroundWithType(notice.guiding)
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(notice))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP_NAVIGATION) guidanceController.stop()
        startForegroundWithType(guidanceController.activePlan.value != null)
        // Not START_STICKY: a sticky restart redelivers with a null Intent, which for a
        // link-holding service means restarting with nothing to reconnect to. REDELIVER_INTENT
        // keeps whatever the original start Intent carried.
        return START_REDELIVER_INTENT
    }

    private fun startForegroundWithType(guiding: Boolean) {
        val notice = Notice(
            controller.state.value,
            guidanceController.activePlan.value,
            guidanceController.guidanceState.value,
            guidanceController.lastNavState.value,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (guiding) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            startForeground(NOTIFICATION_ID, buildNotification(notice), types)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(notice))
        }
    }

    private data class Notice(
        val connection: ConnectionState,
        val plan: RoutePlan?,
        val guidance: GuidanceState?,
        val nav: NavState?,
    ) {
        val guiding: Boolean get() = plan != null
    }

    private fun buildNotification(notice: Notice): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setSilent(true)

        val plan = notice.plan
        val guidance = notice.guidance
        val nav = notice.nav
        return if (plan != null && guidance != null && nav != null) {
            val traveled = (plan.distanceM - guidance.distanceRemainingM).coerceIn(0, plan.distanceM)
            builder
                .setContentTitle(nav.text.ifBlank { "Navigating" })
                .setContentText("${guidance.distanceRemainingM} m remaining")
                .setShortCriticalText(distanceLabel(guidance.distanceToTurnM))
                .setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgressSegments(
                            listOf(NotificationCompat.ProgressStyle.Segment(plan.distanceM.coerceAtLeast(1))),
                        )
                        .setProgress(traveled),
                )
                .setRequestPromotedOngoing(true)
                .addAction(0, "Stop", stopNavigationPendingIntent())
                .build()
        } else {
            builder
                .setContentTitle("Cluster link")
                .setContentText(describeLinkState(notice.connection))
                .build()
        }
    }

    private fun describeLinkState(state: ConnectionState): String = when (state) {
        ConnectionState.Idle -> "Idle"
        is ConnectionState.Connecting -> "Connecting to ${state.address}"
        is ConnectionState.Discovering -> "Discovering services"
        is ConnectionState.Ready -> "Linked - heartbeat running"
        is ConnectionState.Unsupported -> state.reason.message
        is ConnectionState.Disconnected -> "Disconnected"
    }

    private fun distanceLabel(metres: Int): String =
        if (metres >= 1000) "%.1f km".format(metres / 1000f) else "$metres m"

    private fun stopNavigationPendingIntent(): PendingIntent {
        val intent = Intent(this, ClusterService::class.java).setAction(ACTION_STOP_NAVIGATION)
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
        private const val ACTION_STOP_NAVIGATION = "dev.jay.betterconnect.action.STOP_NAVIGATION"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ClusterService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClusterService::class.java))
        }
    }
}
