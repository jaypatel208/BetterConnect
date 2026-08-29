package dev.jay.betterconnect.feature.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.link.DeviceRepository
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.DeviceInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectUiState(
    val scanning: Boolean = false,
    val bluetoothEnabled: Boolean = true,
    val devices: ImmutableList<DeviceInfo> = persistentListOf(),
    val connection: ConnectionState = ConnectionState.Idle,
    val demoMode: Boolean = false,
    val lastAddress: String? = null,
) {
    val candidates: List<DeviceInfo> get() = devices.filter { it.isCandidate }
    val others: List<DeviceInfo> get() = devices.filterNot { it.isCandidate }
    val connectedAddress: String?
        get() = (connection as? ConnectionState.Ready)?.address
}

sealed interface ConnectAction {
    data object ToggleScan : ConnectAction
    data class Connect(val address: String) : ConnectAction
    data object Disconnect : ConnectAction
    data class SetDemoMode(val enabled: Boolean) : ConnectAction
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val controller: ClusterController,
    private val devices: DeviceRepository,
) : ViewModel() {

    val uiState: StateFlow<ConnectUiState> = combine(
        controller.scanner.results,
        controller.scanner.scanning,
        controller.state,
        controller.demoMode,
        devices.lastAddress,
    ) { results, scanning, connection, demo, lastAddress ->
        ConnectUiState(
            scanning = scanning,
            bluetoothEnabled = controller.scanner.bluetoothEnabled,
            devices = results.toImmutableList(),
            connection = connection,
            demoMode = demo,
            lastAddress = lastAddress,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectUiState())

    fun onAction(action: ConnectAction) {
        when (action) {
            ConnectAction.ToggleScan ->
                if (controller.scanner.scanning.value) {
                    controller.scanner.stop()
                } else {
                    controller.scanner.start()
                }

            is ConnectAction.Connect -> {
                controller.scanner.stop()
                controller.connect(action.address)
                viewModelScope.launch { devices.setLastAddress(action.address) }
            }

            ConnectAction.Disconnect -> controller.disconnect()

            is ConnectAction.SetDemoMode -> controller.setDemoMode(action.enabled)
        }
    }

    override fun onCleared() {
        controller.scanner.stop()
        super.onCleared()
    }
}
