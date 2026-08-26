package dev.jay.betterconnect.feature.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jay.betterconnect.core.designsystem.component.MetricRow
import dev.jay.betterconnect.core.designsystem.component.SectionCard
import dev.jay.betterconnect.core.designsystem.theme.MonoText
import dev.jay.betterconnect.core.designsystem.theme.StatusColors
import dev.jay.betterconnect.core.model.GattService
import dev.jay.betterconnect.core.protocol.ClusterProtocol

@Composable
fun InspectRoute(viewModel: InspectViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    InspectScreen(
        state = state,
        onCopy = {
            scope.launch {
                clipboard.setClipEntry(
                    androidx.compose.ui.platform.ClipEntry(
                        android.content.ClipData.newPlainText("GATT table", viewModel.exportText()),
                    ),
                )
            }
        },
        onResetStats = viewModel::resetStats,
    )
}

@Composable
fun InspectScreen(
    state: InspectUiState,
    onCopy: () -> Unit,
    onResetStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { VerdictBanner(state) }

        item {
            SectionCard(title = "Link") {
                MetricRow("State", state.connection.describe().first)
                MetricRow("Address", state.dump?.address ?: "-")
                MetricRow(
                    "Negotiated MTU",
                    state.mtu?.toString() ?: "-",
                    valueColor = if (state.mtu == null) {
                        MaterialTheme.colorScheme.onSurface
                    } else if (state.mtuAdequate) StatusColors.Ok else StatusColors.Error,
                )
                MetricRow("Required MTU", "${ClusterProtocol.MIN_MTU} (48 byte frame + 3)")
            }
        }

        item {
            SectionCard(
                title = "Writes",
                subtitle = "The cluster never acknowledges, so these counters are the only feedback",
                trailing = {
                    OutlinedButton(onClick = onResetStats) { Text("Reset") }
                },
            ) {
                MetricRow("Sent", state.stats.sent.toString(), valueColor = StatusColors.Ok)
                MetricRow("Dropped (busy)", state.stats.dropped.toString(), valueColor = StatusColors.Warn)
                MetricRow("Failed", state.stats.failed.toString(), valueColor = StatusColors.Error)
                MetricRow("Not ready", state.stats.notReady.toString(), valueColor = StatusColors.Idle)
            }
        }

        item {
            SectionCard(
                title = "GATT table",
                subtitle = "Replaces needing a separate BLE explorer",
                trailing = { OutlinedButton(onClick = onCopy) { Text("Copy") } },
            ) {
                val services = state.dump?.services.orEmpty()
                if (services.isEmpty()) {
                    Text(
                        "Nothing discovered yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    services.forEach { ServiceBlock(it) }
                }
            }
        }
    }
}

@Composable
private fun VerdictBanner(state: InspectUiState) {
    val color = when (state.verdict) {
        Verdict.SUPPORTED -> StatusColors.Ok
        Verdict.NOT_CONNECTED -> StatusColors.Idle
        else -> StatusColors.Error
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Text(
            state.verdict.headline,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            state.verdict.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ServiceBlock(service: GattService) {
    var expanded by remember { mutableStateOf(true) }
    val isCluster = service.uuid.equals(ClusterProtocol.SERVICE_UUID.toString(), ignoreCase = true)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { expanded = !expanded },
    ) {
        Row {
            Text(
                if (expanded) "▾ " else "▸ ",
                style = MonoText.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                service.uuid,
                style = MonoText.label,
                color = if (isCluster) StatusColors.Ok else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (isCluster) {
            Text(
                "   cluster vendor service",
                style = MonoText.small,
                color = StatusColors.Ok,
            )
        }
        if (expanded) {
            service.characteristics.forEach { characteristic ->
                Column(Modifier.padding(start = 18.dp, top = 6.dp)) {
                    Text(
                        characteristic.uuid,
                        style = MonoText.small,
                        color = if (characteristic.isTbtInfo) {
                            StatusColors.Ok
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        buildString {
                            append(characteristic.properties.joinToString(" "))
                            if (characteristic.isTbtInfo) append("   <-- TBT_INFO")
                        },
                        style = MonoText.small,
                        color = if (characteristic.isTbtInfo) StatusColors.Ok else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
