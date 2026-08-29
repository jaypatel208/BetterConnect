package dev.jay.betterconnect.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jay.betterconnect.core.designsystem.theme.BetterConnectTheme
import dev.jay.betterconnect.core.designsystem.theme.MonoText
import dev.jay.betterconnect.core.designsystem.theme.StatusColors
import dev.jay.betterconnect.core.model.GpsStatus
import dev.jay.betterconnect.core.protocol.DecodeResult
import dev.jay.betterconnect.core.protocol.DecodedFrame
import dev.jay.betterconnect.core.protocol.DistanceField
import dev.jay.betterconnect.core.protocol.TbtEncoder
import dev.jay.betterconnect.core.protocol.TbtFrame

/**
 * What the cluster should be showing, rendered from the bytes we are actually sending.
 *
 * This is fed by decoding the outgoing frame rather than by the UI state that produced it.
 * That distinction is the whole point: a packing mistake shows up here, on a desk, instead
 * of as a wrong arrow on a bike with nothing to explain it.
 */
@Composable
fun VirtualCluster(
    frame: TbtFrame?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(StatusColors.ClusterPanel, RoundedCornerShape(20.dp))
            .border(1.dp, StatusColors.ClusterPanelEdge, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        when (val result = frame?.decode()) {
            null -> ClusterIdle()
            is DecodeResult.Valid -> ClusterContent(result.frame)
            is DecodeResult.BadChecksum -> ClusterFault(
                "CHECKSUM ${result.actual} != ${result.expected}",
            )
            is DecodeResult.BadSize -> ClusterFault("BAD SIZE ${result.size}")
        }
    }
}

@Composable
private fun ClusterIdle() {
    Column(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CLUSTER CLEAR", style = MonoText.label, color = StatusColors.ClusterInkDim)
        Spacer(Modifier.height(6.dp))
        Text(
            "48 zero bytes - nothing displayed",
            style = MonoText.small,
            color = StatusColors.ClusterInkDim,
        )
    }
}

@Composable
private fun ClusterFault(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("FRAME REJECTED", style = MonoText.label, color = StatusColors.Error)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MonoText.small, color = StatusColors.Error)
    }
}

@Composable
private fun ClusterContent(frame: DecodedFrame) {
    // The blink is part of the protocol, not decoration: it is how the cluster signals
    // that the manoeuvre is within 100 m.
    val alpha by if (frame.blinking) {
        rememberInfiniteTransition(label = "blink").animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
            label = "blinkAlpha",
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.alpha(alpha)) {
                ManeuverGlyph(symbolCode = frame.symbolCode, size = 92.dp)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = frame.turn.display(),
                    color = StatusColors.ClusterInk,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                if (frame.roundaboutExit > 0) {
                    Text(
                        "EXIT ${frame.roundaboutExit}",
                        style = MonoText.label,
                        color = StatusColors.Warn,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = frame.text.ifBlank { "-" },
            color = StatusColors.ClusterInk,
            fontSize = 17.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ClusterStat(
                "ETA",
                "%d:%02d %s".format(frame.etaHour12, frame.etaMinute, if (frame.isPm) "PM" else "AM"),
            )
            ClusterStat("LEFT", frame.total.display())
            ClusterStat(
                "GPS",
                when (frame.gpsStatus) {
                    GpsStatus.ACTIVE -> "OK"
                    GpsStatus.SEARCHING -> "SEARCHING"
                    GpsStatus.OFF -> "NO FIX"
                },
            )
            ClusterStat("ICON", "${frame.symbolChar} (0x%02X)".format(frame.symbolCode))
        }
    }
}

@Composable
private fun ClusterStat(label: String, value: String) {
    Column {
        Text(label, style = MonoText.small, color = StatusColors.ClusterInkDim)
        Text(value, style = MonoText.label, color = StatusColors.ClusterInk)
    }
}

internal fun DistanceField.display(): String = if (isMetres) "$whole m" else "$whole.%02d km".format(fraction)

@Preview(name = "Cluster - turn", widthDp = 400)
@Composable
private fun PreviewCluster() {
    BetterConnectTheme {
        VirtualCluster(
            frame = TbtFrame(
                TbtEncoder().encode(
                    dev.jay.betterconnect.core.model.NavState(
                        symbol = dev.jay.betterconnect.core.model.Symbol.LEFT,
                        distanceToTurnM = 500,
                        distanceLeftM = 12_300,
                        etaSeconds = 900,
                        text = "SP RING ROAD",
                    ),
                ),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
