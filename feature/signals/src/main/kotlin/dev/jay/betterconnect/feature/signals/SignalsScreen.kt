package dev.jay.betterconnect.feature.signals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jay.betterconnect.core.designsystem.component.Advisory
import dev.jay.betterconnect.core.designsystem.component.HexBlock
import dev.jay.betterconnect.core.designsystem.component.SectionCard
import dev.jay.betterconnect.core.designsystem.component.VirtualCluster
import dev.jay.betterconnect.core.designsystem.theme.MonoText
import dev.jay.betterconnect.core.designsystem.theme.StatusColors
import dev.jay.betterconnect.core.link.SendMode
import dev.jay.betterconnect.core.model.SymbolCatalog

@Composable
fun SignalsRoute(viewModel: SignalsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignalsScreen(state, viewModel::onAction)
}

@Composable
fun SignalsScreen(
    state: SignalsUiState,
    onAction: (SignalsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            VirtualCluster(frame = state.currentFrame)
        }

        if (!state.canSend) {
            item {
                Advisory(
                    "Not connected. Connect on the Link tab, or switch on Demo mode to " +
                        "drive the virtual cluster without a bike.",
                )
            }
        }

        item { ModeCard(state, onAction) }

        item {
            SectionCard(
                title = "Symbol sweep",
                subtitle = state.selectedLetter?.let { "$it - ${state.selectedLabel}" }
                    ?: "Tap a letter to send it as byte 1",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Blinking (lowercase)", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = state.blinking,
                        onCheckedChange = { onAction(SignalsAction.SetBlinking(it)) },
                    )
                }
                Spacer(Modifier.height(10.dp))
                LetterGrid(state, onAction)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onAction(SignalsAction.Clear) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear cluster (48 zero bytes)") }
            }
        }

        item { ConfigCard(state, onAction) }

        item {
            SectionCard(
                title = "Outgoing frame",
                subtitle = "Changed bytes highlighted",
            ) {
                val frame = state.currentFrame
                if (frame == null) {
                    Text(
                        "Nothing being sent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    HexBlock(hex = frame.toHex(), changedIndices = state.changedBytes)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "sent ${state.stats.sent}   dropped ${state.stats.dropped}   " +
                            "failed ${state.stats.failed}   notReady ${state.stats.notReady}",
                        style = MonoText.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCard(state: SignalsUiState, onAction: (SignalsAction) -> Unit) {
    SectionCard(
        title = "Send mode",
        subtitle = "Heartbeat re-asserts every 350 ms; one-shot sends exactly once",
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SendMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.sendMode == mode,
                    onClick = { onAction(SignalsAction.SetMode(mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, SendMode.entries.size),
                ) {
                    Text(if (mode == SendMode.HEARTBEAT) "Heartbeat" else "One shot")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "One-shot answers a question the APK cannot: does the cluster latch a frame, " +
                "or does the display decay without repetition?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LetterGrid(state: SignalsUiState, onAction: (SignalsAction) -> Unit) {
    // A plain Column of Rows rather than LazyVerticalGrid: this sits inside a LazyColumn,
    // and nesting lazy scrollables in the same axis is not allowed.
    SymbolCatalog.sweepLetters.chunked(6).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            row.forEach { letter ->
                LetterTile(
                    letter = letter,
                    selected = state.selectedLetter == letter,
                    documented = SymbolCatalog.isDocumented(letter),
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(SignalsAction.SendLetter(letter)) },
                )
            }
            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun LetterTile(
    letter: Char,
    selected: Boolean,
    documented: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = when {
        selected -> MaterialTheme.colorScheme.primary
        documented -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        documented -> MaterialTheme.colorScheme.onSurface
        // Undocumented codes are exactly what a sweep is for: mark them, do not hide them.
        else -> StatusColors.Warn
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(container, RoundedCornerShape(12.dp))
            .border(
                width = if (documented) 0.dp else 1.dp,
                color = if (documented) container else StatusColors.Warn.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter.toString(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConfigCard(state: SignalsUiState, onAction: (SignalsAction) -> Unit) {
    val config = state.config
    SectionCard(
        title = "Frame fields",
        subtitle = "Held constant while the symbol byte changes",
    ) {
        Text(
            "Distance to turn: ${config.distanceToTurnM} m" +
                if (config.distanceToTurnM <= 100) "  (blink threshold)" else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = config.distanceToTurnM.toFloat(),
            onValueChange = {
                onAction(SignalsAction.UpdateConfig(config.copy(distanceToTurnM = it.toInt())))
            },
            valueRange = 0f..5_000f,
        )

        Text("Distance remaining: ${config.distanceLeftM} m", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = config.distanceLeftM.toFloat(),
            onValueChange = {
                onAction(SignalsAction.UpdateConfig(config.copy(distanceLeftM = it.toInt())))
            },
            valueRange = 0f..50_000f,
        )

        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = config.text,
            onValueChange = { onAction(SignalsAction.UpdateConfig(config.copy(text = it))) },
            label = { Text("Street text (max 31 chars)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        Text("Roundabout exit: ${config.roundaboutExit}", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..7).forEach { exit ->
                val selected = config.roundaboutExit == exit
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            RoundedCornerShape(9.dp),
                        )
                        .clickable {
                            onAction(SignalsAction.UpdateConfig(config.copy(roundaboutExit = exit)))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        exit.toString(),
                        style = MonoText.label,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GPS active", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = config.gpsActive,
                onCheckedChange = {
                    onAction(SignalsAction.UpdateConfig(config.copy(gpsActive = it)))
                },
            )
        }
    }
}
