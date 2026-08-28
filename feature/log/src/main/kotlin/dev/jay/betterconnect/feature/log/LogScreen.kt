package dev.jay.betterconnect.feature.log

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jay.betterconnect.core.designsystem.theme.MonoText
import dev.jay.betterconnect.core.designsystem.theme.StatusColors
import dev.jay.betterconnect.core.domain.LogEntry
import dev.jay.betterconnect.core.domain.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogRoute(viewModel: LogViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LogScreen(
        state = state,
        onToggleFilter = viewModel::toggleFilter,
        onClear = viewModel::clear,
        onShare = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Better Connect diagnostic log")
                putExtra(Intent.EXTRA_TEXT, viewModel.export())
            }
            context.startActivity(Intent.createChooser(intent, "Share log"))
        },
    )
}

@Composable
fun LogScreen(
    state: LogUiState,
    onToggleFilter: (LogLevel) -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogLevel.entries.forEach { level ->
                FilterChip(
                    label = level.name,
                    active = level in state.activeFilters,
                    color = level.color(),
                    onClick = { onToggleFilter(level) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear") }
        }

        if (state.visible.isEmpty()) {
            Text(
                "Nothing logged yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = true,
            ) {
                items(state.visible.asReversed()) { entry -> LogRow(entry) }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MonoText.small,
        color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .background(
                if (active) color.copy(alpha = 0.14f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun LogRow(entry: LogEntry) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
    ) {
        Row {
            Text(
                TIME.format(Date(entry.timestampMs)),
                style = MonoText.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(entry.level.name, style = MonoText.small, color = entry.level.color())
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("[${entry.tag}]", style = MonoText.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(entry.message, style = MaterialTheme.typography.bodySmall)
        entry.hex?.let {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Text(it, style = MonoText.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        entry.decoded?.let {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Text(it, style = MonoText.small, color = StatusColors.Ok)
            }
        }
    }
}

private fun LogLevel.color(): Color = when (this) {
    LogLevel.INFO -> StatusColors.Idle
    LogLevel.FRAME -> StatusColors.Ok
    LogLevel.WARN -> StatusColors.Warn
    LogLevel.ERROR -> StatusColors.Error
}

private val TIME = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
