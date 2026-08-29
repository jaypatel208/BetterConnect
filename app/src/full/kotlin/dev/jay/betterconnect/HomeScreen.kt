package dev.jay.betterconnect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.designsystem.component.SectionCard
import dev.jay.betterconnect.core.designsystem.component.StatusPill
import dev.jay.betterconnect.core.designsystem.component.describe
import dev.jay.betterconnect.core.model.ConnectionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data object Home : NavKey

fun EntryProviderScope<NavKey>.homeEntry(
    onNavigateToDevices: () -> Unit,
    onNavigateToNavigation: () -> Unit,
    onNavigateToDebug: () -> Unit,
) {
    entry<Home> {
        HomeRoute(
            onNavigateToDevices = onNavigateToDevices,
            onNavigateToNavigation = onNavigateToNavigation,
            onNavigateToDebug = onNavigateToDebug,
        )
    }
}

/** Hidden unlock, not a rider-facing affordance - matches the version-tap pattern from prior apps. */
private const val TAPS_TO_UNLOCK_DEBUG = 7

data class HomeUiState(val connection: ConnectionState = ConnectionState.Idle) {
    val isReady: Boolean get() = connection is ConnectionState.Ready
}

@HiltViewModel
class HomeViewModel @Inject constructor(controller: ClusterController) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = controller.state
        .map { HomeUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

@Composable
fun HomeRoute(
    onNavigateToDevices: () -> Unit,
    onNavigateToNavigation: () -> Unit,
    onNavigateToDebug: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ClusterService.start(context)
    }

    HomeScreen(
        state = state,
        onNavigateToDevices = onNavigateToDevices,
        onNavigateToNavigation = onNavigateToNavigation,
        onNavigateToDebug = onNavigateToDebug,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onNavigateToDevices: () -> Unit,
    onNavigateToNavigation: () -> Unit,
    onNavigateToDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var debugTapCount by remember { mutableIntStateOf(0) }

    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Better Connect", style = MaterialTheme.typography.headlineMedium)

        val (label, color) = state.connection.describe()
        SectionCard(
            title = "Cluster link",
            subtitle = statusText(state.connection),
            trailing = { StatusPill(label, color) },
            modifier = Modifier.padding(top = 24.dp),
        ) {
            if (state.isReady) {
                Button(
                    onClick = onNavigateToNavigation,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start navigation") }
            } else {
                OutlinedButton(
                    onClick = onNavigateToDevices,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Connect a cluster") }
            }
        }

        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp).clickable {
                debugTapCount++
                if (debugTapCount >= TAPS_TO_UNLOCK_DEBUG) {
                    debugTapCount = 0
                    onNavigateToDebug()
                }
            },
        )
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
