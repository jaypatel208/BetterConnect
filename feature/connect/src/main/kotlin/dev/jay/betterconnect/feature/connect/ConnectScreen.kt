package dev.jay.betterconnect.feature.connect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jay.betterconnect.core.designsystem.component.Advisory
import dev.jay.betterconnect.core.designsystem.component.DeviceRow
import dev.jay.betterconnect.core.designsystem.component.ListHeading
import dev.jay.betterconnect.core.designsystem.component.SectionCard
import dev.jay.betterconnect.core.designsystem.component.StatusPill
import dev.jay.betterconnect.core.designsystem.component.describe
import dev.jay.betterconnect.core.designsystem.theme.StatusColors
import dev.jay.betterconnect.core.model.ConnectionState

@Composable
fun ConnectRoute(viewModel: ConnectViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectScreen(state, viewModel::onAction)
}

@Composable
fun ConnectScreen(
    state: ConnectUiState,
    onAction: (ConnectAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ConnectionCard(state, onAction) }

        item {
            // Missing this costs a wasted trip: the symptom looks like random disconnects.
            Advisory(
                "Only one app can hold the cluster's BLE link. Force-stop Bajaj Ride Connect " +
                    "before connecting here, or both will fight over it.",
            )
        }

        item { DemoModeCard(state, onAction) }

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
                OutlinedButton(onClick = { onAction(ConnectAction.ToggleScan) }) {
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
                    onClick = { onAction(ConnectAction.Connect(device.address)) },
                )
            }
        }

        if (state.others.isNotEmpty()) {
            item { ListHeading("Other devices") }
            items(state.others, key = { it.address }) { device ->
                DeviceRow(
                    device = device,
                    connected = device.address == state.connectedAddress,
                    onClick = { onAction(ConnectAction.Connect(device.address)) },
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

@Composable
private fun ConnectionCard(state: ConnectUiState, onAction: (ConnectAction) -> Unit) {
    val (label, color) = state.connection.describe()
    SectionCard(
        title = "Link",
        subtitle = state.connectedAddress ?: state.lastAddress ?: "No device selected",
        trailing = { StatusPill(label, color) },
    ) {
        if (state.connection is ConnectionState.Unsupported) {
            Advisory(state.connection.reason.message, color = StatusColors.Error)
            Spacer(Modifier.height(10.dp))
        }
        if (state.connection is ConnectionState.Ready || state.connection is ConnectionState.Connecting) {
            Button(
                onClick = { onAction(ConnectAction.Disconnect) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Disconnect") }
        } else if (state.lastAddress != null) {
            OutlinedButton(
                onClick = { onAction(ConnectAction.Connect(state.lastAddress)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Reconnect to ${state.lastAddress}") }
        }
    }
}

@Composable
private fun DemoModeCard(state: ConnectUiState, onAction: (ConnectAction) -> Unit) {
    SectionCard(
        title = "Demo mode",
        subtitle = "Drive every screen with an in-process fake cluster, no bike needed",
        trailing = {
            Switch(
                checked = state.demoMode,
                onCheckedChange = { onAction(ConnectAction.SetDemoMode(it)) },
            )
        },
    ) {
        Text(
            "Frames are decoded and shown on the virtual cluster exactly as the real " +
                "transport would send them. This is the same fake the test suite uses.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.demoMode) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onAction(ConnectAction.Connect("DEMO")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Connect to fake cluster") }
        }
    }
}
