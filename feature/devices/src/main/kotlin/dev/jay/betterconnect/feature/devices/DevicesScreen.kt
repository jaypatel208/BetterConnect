package dev.jay.betterconnect.feature.devices

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jay.betterconnect.core.designsystem.component.Advisory
import dev.jay.betterconnect.core.designsystem.component.DeviceRow
import dev.jay.betterconnect.core.designsystem.component.ListHeading
import dev.jay.betterconnect.core.designsystem.component.SectionCard
import dev.jay.betterconnect.core.designsystem.component.StatusPill
import dev.jay.betterconnect.core.designsystem.component.describe
import dev.jay.betterconnect.core.model.ConnectionState

/**
 * This is the whole "Connect" tab's content - not a screen you navigate to and back from.
 * Reachable at all times alongside Navigate, independent of connection state, per the
 * official-app-style split the rider-facing product is meant to have.
 */
@Composable
fun DevicesRoute(viewModel: DevicesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The system's own "turn on Bluetooth?" dialog - the app had no way to prompt this at
    // all before, which is exactly why a Bluetooth-off scan looked like a dead button.
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* BluetoothStateReceiver picks up the resulting state-changed broadcast. */ }

    LaunchedEffect(Unit) {
        viewModel.observeBluetoothState(context)
        viewModel.startScanningIfIdle()
    }

    DevicesScreen(
        state = state,
        onAction = viewModel::onAction,
        onRequestEnableBluetooth = {
            val hasConnectPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            if (hasConnectPermission) {
                runCatching {
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            }
        },
    )
}

@Composable
fun DevicesScreen(
    state: DevicesUiState,
    onAction: (DevicesAction) -> Unit,
    modifier: Modifier = Modifier,
    onRequestEnableBluetooth: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            val (label, color) = state.connection.describe()
            SectionCard(
                title = "Cluster link",
                subtitle = statusText(state.connection),
                trailing = { StatusPill(label, color) },
            ) {}
        }

        // Persistent and first on screen, not a line buried below the fold - this is the
        // single most-missed piece of state in the app: Scan looks identical whether it's
        // working or Bluetooth is simply off underneath it.
        if (!state.bluetoothEnabled) {
            item {
                SectionCard(
                    title = "Bluetooth is off",
                    subtitle = "Turn it on to scan for the cluster.",
                ) {
                    Button(onClick = onRequestEnableBluetooth, modifier = Modifier.fillMaxWidth()) {
                        Text("Turn on Bluetooth")
                    }
                }
            }
        }

        item {
            // Missing this costs a wasted trip: the symptom looks like random disconnects.
            Advisory(
                "Only one app can hold the cluster's BLE link. Force-stop Bajaj Ride Connect " +
                    "before connecting here, or both will fight over it.",
            )
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
                if (state.bluetoothEnabled) {
                    OutlinedButton(onClick = { onAction(DevicesAction.ToggleScan) }) {
                        Text(if (state.scanning) "Stop scan" else "Scan")
                    }
                } else {
                    // Always does something observable when pressed - never present-but-inert.
                    OutlinedButton(onClick = onRequestEnableBluetooth) {
                        Text("Turn on Bluetooth to scan")
                    }
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

private fun statusText(connection: ConnectionState): String = when (connection) {
    ConnectionState.Idle -> "Not connected yet"
    is ConnectionState.Connecting -> "Connecting to ${connection.address}"
    is ConnectionState.Discovering -> "Discovering services"
    is ConnectionState.Ready -> "Linked - ready to navigate"
    is ConnectionState.Unsupported -> connection.reason.message
    is ConnectionState.Disconnected -> "Disconnected"
}
