package dev.jay.betterconnect.feature.signals

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jay.betterconnect.core.designsystem.component.Advisory
import dev.jay.betterconnect.core.designsystem.component.SectionCard
import dev.jay.betterconnect.core.designsystem.component.VirtualCluster
import dev.jay.betterconnect.core.designsystem.theme.MonoText
import dev.jay.betterconnect.core.designsystem.theme.StatusColors
import dev.jay.betterconnect.core.domain.SequenceScript

@Composable
fun SequenceRoute(viewModel: SequenceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SequenceScreen(state, viewModel::onAction)
}

@Composable
fun SequenceScreen(
    state: SequenceUiState,
    onAction: (SequenceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { VirtualCluster(frame = state.currentFrame) }

        if (!state.canSend) {
            item { Advisory("Not connected. Use Demo mode on the Link tab to run scripts dry.") }
        }

        item {
            AnimatedVisibility(state.running) {
                val progress = state.progress
                SectionCard(
                    title = "Running: ${progress?.script?.name.orEmpty()}",
                    subtitle = progress?.let { "Step ${it.index + 1} of ${it.total}" },
                ) {
                    LinearProgressIndicator(
                        progress = { progress?.fraction ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        progress?.step?.label.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    progress?.step?.note?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = StatusColors.Warn)
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { onAction(SequenceAction.Start) },
                    enabled = !state.running,
                    modifier = Modifier.weight(1f),
                ) { Text("Start") }
                OutlinedButton(
                    onClick = { onAction(SequenceAction.Stop) },
                    enabled = state.running,
                    modifier = Modifier.weight(1f),
                ) { Text("Stop") }
            }
        }

        item {
            SectionCard(title = "Playback") {
                Text("Dwell per step: ${state.dwellMs} ms", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = state.dwellMs.toFloat(),
                    onValueChange = { onAction(SequenceAction.SetDwell(it.toLong())) },
                    valueRange = 300f..5_000f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Loop", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = state.loop,
                        onCheckedChange = { onAction(SequenceAction.SetLoop(it)) },
                    )
                }
            }
        }

        items(state.scripts.size) { index ->
            val script = state.scripts[index]
            ScriptCard(
                script = script,
                selected = script.id == state.selectedId,
                onClick = { onAction(SequenceAction.Select(script.id)) },
            )
        }
    }
}

@Composable
private fun ScriptCard(
    script: SequenceScript,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                script.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text("${script.steps.size} steps", style = MonoText.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            script.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
