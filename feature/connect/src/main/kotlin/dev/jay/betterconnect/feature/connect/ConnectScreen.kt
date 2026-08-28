package dev.jay.betterconnect.feature.connect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jay.betterconnect.core.designsystem.component.Advisory
import dev.jay.betterconnect.core.designsystem.component.SectionCard
import dev.jay.betterconnect.core.designsystem.component.StatusPill
import dev.jay.betterconnect.core.designsystem.theme.MonoText
import dev.jay.betterconnect.core.designsystem.theme.StatusColors
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.DeviceInfo

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
                DeviceCard(device, state, onAction)
            }
        }

        if (state.others.isNotEmpty()) {
            item { ListHeading("Other devices") }
            items(state.others, key = { it.address }) { device ->
                DeviceCard(device, state, onAction)
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
private fun ListHeading(text: String) {
    Text(
        text.uppercase(),
        style = MonoText.small,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
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

@Composable
private fun DeviceCard(device: DeviceInfo, state: ConnectUiState, onAction: (ConnectAction) -> Unit) {
    val connected = device.address == state.connectedAddress
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAction(ConnectAction.Connect(device.address)) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        ),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignalMeter(device.rssi)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.name ?: "(no name)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    device.address,
                    style = MonoText.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    if (device.bonded) Tag("BONDED")
                    if (device.isCandidate) Tag("CANDIDATE", StatusColors.Ok)
                    if (!device.connectable) Tag("NOT CONNECTABLE", StatusColors.Warn)
                }
            }
            if (device.rssi != -127) {
                Text(
                    "${device.rssi}",
                    style = MonoText.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MonoText.small,
        color = color,
        modifier = Modifier
            .padding(end = 6.dp, top = 4.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Four bars from RSSI. -127 means "bonded, never seen in a scan". */
@Composable
private fun SignalMeter(rssi: Int) {
    val bars = when {
        rssi == -127 -> 0
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        else -> 1
    }
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(24.dp)) {
        repeat(4) { index ->
            Box(
                Modifier
                    .padding(end = 2.dp)
                    .width(4.dp)
                    .height((6 + index * 6).dp)
                    .background(
                        if (index <
                            bars
                        ) {
                            StatusColors.Ok
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

internal fun ConnectionState.describe(): Pair<String, Color> = when (this) {
    ConnectionState.Idle -> "IDLE" to StatusColors.Idle
    is ConnectionState.Connecting -> "CONNECTING" to StatusColors.Warn
    is ConnectionState.Discovering -> "DISCOVERING" to StatusColors.Warn
    is ConnectionState.Ready -> "READY" to StatusColors.Ok
    is ConnectionState.Unsupported -> "UNSUPPORTED" to StatusColors.Error
    is ConnectionState.Disconnected -> "DISCONNECTED" to StatusColors.Idle
}
