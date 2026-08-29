package dev.jay.betterconnect.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jay.betterconnect.core.ble.FullPermissions
import dev.jay.betterconnect.core.link.DeviceRepository
import dev.jay.betterconnect.core.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects to the last-used cluster once, at process start, so the rider does not have to
 * re-open the device list on every launch.
 *
 * Fires only from [ConnectionState.Idle] - a later [ConnectionState.Disconnected] is already
 * handled by `ClusterLink`'s own scheduled reconnect (see `BleClusterTransport`'s handling of
 * `LinkCommand.ScheduleReconnect`), and calling [ClusterController.connect] again here would
 * race that retry rather than help it.
 */
@Singleton
class AutoConnector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: ClusterController,
    private val devices: DeviceRepository,
) {
    private var attempted = false

    fun start(scope: CoroutineScope) {
        combine(controller.state, devices.lastAddress) { state, lastAddress -> state to lastAddress }
            .onEach { (state, lastAddress) ->
                if (attempted) return@onEach
                if (state !is ConnectionState.Idle) return@onEach
                if (lastAddress == null) return@onEach
                if (!FullPermissions.allRequiredGranted(context)) return@onEach
                attempted = true
                controller.connect(lastAddress)
            }
            .launchIn(scope)
    }
}
