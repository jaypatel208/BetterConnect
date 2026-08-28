package dev.jay.betterconnect

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jay.betterconnect.core.ble.BluetoothStateReceiver
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.model.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data object Home : NavKey

fun EntryProviderScope<NavKey>.homeEntry() {
    entry<Home> { HomeRoute() }
}

data class HomeUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val bluetoothOn: Boolean = true,
) {
    val statusText: String
        get() = when {
            !bluetoothOn -> "Bluetooth is off"
            connection is ConnectionState.Ready -> "Linked"
            connection is ConnectionState.Connecting -> "Connecting..."
            connection is ConnectionState.Discovering -> "Discovering services..."
            connection is ConnectionState.Disconnected -> "Disconnected"
            connection is ConnectionState.Unsupported -> "Unsupported: ${connection.reason}"
            else -> "Scanning"
        }
}

@HiltViewModel
class HomeViewModel @Inject constructor(private val controller: ClusterController) : ViewModel() {

    private val bluetoothOn = MutableStateFlow(true)

    val uiState: StateFlow<HomeUiState> = combine(controller.state, bluetoothOn) { state, on ->
        HomeUiState(connection = state, bluetoothOn = on)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun startScanning() = controller.scanner.start()

    fun observeBluetoothState(context: Context) {
        BluetoothStateReceiver.state(context)
            .onEach { state ->
                bluetoothOn.value = state != BluetoothAdapter.STATE_OFF &&
                    state != BluetoothAdapter.STATE_TURNING_OFF
            }
            .launchIn(viewModelScope)
    }
}

@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.startScanning()
        viewModel.observeBluetoothState(context)
        ClusterService.start(context)
    }

    HomeScreen(state = state)
}

@Composable
fun HomeScreen(state: HomeUiState, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Better Connect", style = MaterialTheme.typography.headlineMedium)
        Text(
            state.statusText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
