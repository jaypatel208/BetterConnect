package dev.jay.betterconnect.feature.devices

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jay.betterconnect.core.ble.BluetoothStateReceiver
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.link.DeviceRepository
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.DeviceInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevicesUiState(
    val scanning: Boolean = false,
    val bluetoothEnabled: Boolean = true,
    val devices: ImmutableList<DeviceInfo> = persistentListOf(),
    val connection: ConnectionState = ConnectionState.Idle,
    val lastAddress: String? = null,
) {
    val candidates: List<DeviceInfo> get() = devices.filter { it.isCandidate }
    val others: List<DeviceInfo> get() = devices.filterNot { it.isCandidate }
    val connectedAddress: String? get() = (connection as? ConnectionState.Ready)?.address
}

sealed interface DevicesAction {
    data object ToggleScan : DevicesAction
    data class Connect(val address: String) : DevicesAction
}

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val controller: ClusterController,
    private val devices: DeviceRepository,
) : ViewModel() {

    // Seeded from the adapter's current state, not a hardcoded `true` - BluetoothStateReceiver
    // only emits on a *change* broadcast, so if Bluetooth is already off when this screen
    // opens, this would otherwise stay wrong (and the off-banner would stay hidden) until the
    // user toggled Bluetooth again.
    private val bluetoothOn = MutableStateFlow(controller.scanner.bluetoothEnabled)

    val uiState: StateFlow<DevicesUiState> = combine(
        controller.scanner.results,
        controller.scanner.scanning,
        controller.state,
        devices.lastAddress,
        bluetoothOn,
    ) { results, scanning, connection, lastAddress, btOn ->
        DevicesUiState(
            scanning = scanning,
            bluetoothEnabled = btOn,
            devices = results.toImmutableList(),
            connection = connection,
            lastAddress = lastAddress,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesUiState())

    /**
     * Called once when the screen first appears. A no-op if a scan is already running **or**
     * Bluetooth is off - `BleScanner.start()` used to silently no-op in that second case with
     * no signal back to the UI at all, which is exactly what made "press Scan, nothing
     * happens" so hard to diagnose. The Bluetooth-off banner is what tells the user why now.
     */
    fun startScanningIfIdle() {
        if (!bluetoothOn.value) return
        if (!controller.scanner.scanning.value) controller.scanner.start()
    }

    fun observeBluetoothState(context: Context) {
        BluetoothStateReceiver.state(context)
            .onEach { state ->
                bluetoothOn.value = state != BluetoothAdapter.STATE_OFF &&
                    state != BluetoothAdapter.STATE_TURNING_OFF
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: DevicesAction) {
        when (action) {
            DevicesAction.ToggleScan ->
                if (controller.scanner.scanning.value) {
                    controller.scanner.stop()
                } else if (bluetoothOn.value) {
                    controller.scanner.start()
                }

            is DevicesAction.Connect -> {
                controller.scanner.stop()
                controller.connect(action.address)
                viewModelScope.launch { devices.setLastAddress(action.address) }
            }
        }
    }

    override fun onCleared() {
        controller.scanner.stop()
        super.onCleared()
    }
}
