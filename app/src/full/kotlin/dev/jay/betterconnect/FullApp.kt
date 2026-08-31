package dev.jay.betterconnect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.jay.betterconnect.core.ble.FullPermissions
import dev.jay.betterconnect.feature.debug.DebugRoute
import dev.jay.betterconnect.feature.devices.DevicesRoute
import dev.jay.betterconnect.feature.navigation.NavigationRoute
import dev.jay.betterconnect.feature.onboarding.OnboardingRoute

private enum class AppTab(val label: String, val icon: ImageVector) {
    Connect("Connect", Icons.Filled.Bluetooth),
    Navigate("Navigate", Icons.Filled.Map),
}

/**
 * Connect and Navigate are persistent, independently-reachable tabs - not a sequential
 * "connect first, then unlock navigation" flow. The map, destination search and route preview
 * all work with no cluster connection at all; only starting guidance needs one, and that is
 * gated on the Navigate tab itself (`NavigationUiState.canStart`), not by hiding the tab.
 */
@Composable
fun FullApp() {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(FullPermissions.allRequiredGranted(context)) }

    // Re-verified on every resume, not just once at first-run onboarding. A required
    // permission revoked later - via system Settings, or Android auto-revoking an unused one -
    // must route back to Onboarding rather than leaving unguarded BLE/location calls reachable
    // behind a stale "already onboarded" assumption.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsGranted = FullPermissions.allRequiredGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!permissionsGranted) {
        OnboardingRoute(onGranted = { permissionsGranted = true })
        return
    }

    LaunchedEffect(Unit) { ClusterService.start(context) }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Connect) }
    var showDebug by rememberSaveable { mutableStateOf(false) }

    if (showDebug) {
        DebugRoute(onClose = { showDebug = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                AppTab.Connect -> ConnectTab(onNavigateToDebug = { showDebug = true })
                AppTab.Navigate -> NavigationRoute(onClose = { selectedTab = AppTab.Connect })
            }
        }
    }
}

/** Hidden unlock, not a rider-facing affordance - matches the version-tap pattern from prior apps. */
private const val TAPS_TO_UNLOCK_DEBUG = 7

@Composable
private fun ConnectTab(onNavigateToDebug: () -> Unit) {
    var debugTapCount by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { DevicesRoute() }
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    debugTapCount++
                    if (debugTapCount >= TAPS_TO_UNLOCK_DEBUG) {
                        debugTapCount = 0
                        onNavigateToDebug()
                    }
                }
                .padding(12.dp),
        )
    }
}
