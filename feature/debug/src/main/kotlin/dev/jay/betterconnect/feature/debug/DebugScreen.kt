package dev.jay.betterconnect.feature.debug

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jay.betterconnect.core.domain.LogEntry
import dev.jay.betterconnect.core.model.GeneralVersion

/**
 * Not a NavKey/stack destination - the version-tap-x7 gesture on the Connect tab toggles this
 * as a full-screen overlay directly, since the app's only other navigation is the persistent
 * Connect/Navigate tab bar, not a back stack.
 */
@Composable
fun DebugRoute(onClose: () -> Unit, viewModel: DebugViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DebugScreen(
        state = state,
        onAction = viewModel::onAction,
        onClose = onClose,
        onExport = {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                viewModel.rideLogFile,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ride log"))
        },
    )
}

@Composable
fun DebugScreen(
    state: DebugUiState,
    onAction: (DebugAction) -> Unit,
    onClose: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Debug menu", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onClose) { Text("Close") }
        }

        BisectRow(
            label = "CONTROL read pump",
            checked = state.controlPumpEnabled,
            onCheckedChange = { onAction(DebugAction.SetControlPumpEnabled(it)) },
        )
        BisectRow(
            label = "GENERAL heartbeat",
            checked = state.generalSchedulerEnabled,
            onCheckedChange = { onAction(DebugAction.SetGeneralSchedulerEnabled(it)) },
        )

        Text(
            "GENERAL version",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GeneralVersion.entries.forEach { version ->
                FilterChip(
                    selected = state.generalVersion == version,
                    onClick = { onAction(DebugAction.SetGeneralVersion(version)) },
                    label = { Text(version.name) },
                )
            }
        }

        OutlinedButton(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("Export ride log") }

        HorizontalDivider()

        if (state.recent.isEmpty()) {
            Text(
                "Nothing logged this session yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(state.recent) { entry -> LogLine(entry) }
            }
        }
    }
}

@Composable
private fun BisectRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    Text(
        "${entry.timestampMs} [${entry.tag}] ${entry.message}",
        style = MaterialTheme.typography.bodySmall,
    )
}
