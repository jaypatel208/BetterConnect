package dev.jay.betterconnect.feature.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.jay.betterconnect.core.designsystem.component.Advisory
import dev.jay.betterconnect.core.designsystem.component.DeviceRow
import dev.jay.betterconnect.core.designsystem.component.ListHeading
import dev.jay.betterconnect.core.designsystem.theme.StatusColors
import kotlinx.serialization.Serializable

@Serializable
data object Devices : NavKey

fun EntryProviderScope<NavKey>.devicesEntry(onConnected: () -> Unit) {
    entry<Devices> { DevicesRoute(onConnected = onConnected) }
}

@Composable
fun DevicesRoute(onConnected: () -> Unit, viewModel: DevicesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.observeBluetoothState(context)
        viewModel.startScanningIfIdle()
    }

    LaunchedEffect(state.connectedAddress) {
        if (state.connectedAddress != null) onConnected()
    }

    DevicesScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun DevicesScreen(
    state: DevicesUiState,
    onAction: (DevicesAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            // Missing this costs a wasted trip: the symptom looks like random disconnects.
            Advisory(
                "Only one app can hold the cluster's BLE link. Force-stop Bajaj Ride Connect " +
                    "before connecting here, or both will fight over it.",
            )
        }

        if (!state.bluetoothEnabled) {
            item { Advisory("Bluetooth is off.", color = StatusColors.Error) }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(onClick = { onAction(DevicesAction.ToggleScan) }) {
                    Text(if (state.scanning) "Stop scan" else "Scan")
                }
            }
        }

        item {
            AnimatedVisibility(state.scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 4.dp))
            }
        }

        if (state.candidates.isNotEmpty()) {
            item { ListHeading("Likely cluster") }
            items(state.candidates, key = { it.address }) { device ->
                DeviceRow(
                    device = device,
                    connected = device.address == state.connectedAddress,
                    onClick = { onAction(DevicesAction.Connect(device.address)) },
                )
            }
        }

        if (state.others.isNotEmpty()) {
            item { ListHeading("Other devices") }
            items(state.others, key = { it.address }) { device ->
                DeviceRow(
                    device = device,
                    connected = device.address == state.connectedAddress,
                    onClick = { onAction(DevicesAction.Connect(device.address)) },
                )
            }
        }

        if (state.devices.isEmpty()) {
            item {
                Text(
                    if (state.scanning) "Scanning..." else "No devices yet. Start a scan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}
