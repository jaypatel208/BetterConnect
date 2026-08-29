package dev.jay.betterconnect.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jay.betterconnect.core.designsystem.theme.BetterConnectTheme
import dev.jay.betterconnect.core.designsystem.theme.MonoText
import dev.jay.betterconnect.core.designsystem.theme.StatusColors
import dev.jay.betterconnect.core.model.DeviceInfo

/** A section-list heading, uppercased mono label. */
@Composable
fun ListHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MonoText.small,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 6.dp),
    )
}

/** One scanned or bonded device. Tap to connect. */
@Composable
fun DeviceRow(
    device: DeviceInfo,
    connected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    if (device.bonded) DeviceTag("BONDED")
                    if (device.isCandidate) DeviceTag("CANDIDATE", StatusColors.Ok)
                    if (!device.connectable) DeviceTag("NOT CONNECTABLE", StatusColors.Warn)
                }
            }
            if (device.rssi != DeviceInfo.RSSI_UNKNOWN) {
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
private fun DeviceTag(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
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

/** Four bars from RSSI. [DeviceInfo.RSSI_UNKNOWN] means "bonded, never seen in a scan". */
@Composable
fun SignalMeter(rssi: Int, modifier: Modifier = Modifier) {
    val bars = when {
        rssi == DeviceInfo.RSSI_UNKNOWN -> 0
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        else -> 1
    }
    Row(modifier = modifier.height(24.dp), verticalAlignment = Alignment.Bottom) {
        repeat(4) { index ->
            Box(
                Modifier
                    .padding(end = 2.dp)
                    .width(4.dp)
                    .height((6 + index * 6).dp)
                    .background(
                        if (index < bars) {
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

@Preview(name = "Device row - light")
@Preview(name = "Device row - dark", uiMode = 0x20)
@Composable
private fun DeviceRowPreview() {
    BetterConnectTheme {
        DeviceRow(
            device = DeviceInfo(
                address = "AA:BB:CC:DD:EE:FF",
                name = "Pulsar N160 UG",
                rssi = -58,
                bonded = true,
                connectable = true,
            ),
            connected = false,
            onClick = {},
        )
    }
}
