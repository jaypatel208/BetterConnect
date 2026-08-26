package dev.jay.betterconnect

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.jay.betterconnect.core.ble.BlePermissions
import dev.jay.betterconnect.feature.connect.ConnectRoute
import dev.jay.betterconnect.feature.connect.InspectRoute
import dev.jay.betterconnect.feature.log.LogRoute
import dev.jay.betterconnect.feature.signals.SequenceRoute
import dev.jay.betterconnect.feature.signals.SignalsRoute

private enum class DiagTab(val route: String, val label: String, val icon: ImageVector) {
    CONNECT("connect", "Link", Icons.Default.Bluetooth),
    INSPECT("inspect", "Inspect", Icons.Default.Info),
    SIGNALS("signals", "Signals", Icons.AutoMirrored.Filled.Send),
    SEQUENCE("sequence", "Sequence", Icons.Default.PlayArrow),
    LOG("log", "Log", Icons.AutoMirrored.Filled.List),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagApp() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(BlePermissions.allGranted(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = BlePermissions.allGranted(context) }

    LaunchedEffect(Unit) {
        if (!granted) {
            val request = BlePermissions.required + buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            launcher.launch(request.toTypedArray())
        }
    }

    if (!granted) {
        PermissionGate(onRequest = { launcher.launch(BlePermissions.required.toTypedArray()) })
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        DiagTab.entries.firstOrNull { tab ->
                            current?.hierarchy?.any { it.route == tab.route } == true
                        }?.label ?: "BC Diag",
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                DiagTab.entries.forEach { tab ->
                    val selected = current?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = DiagTab.CONNECT.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(DiagTab.CONNECT.route) { ConnectRoute() }
            composable(DiagTab.INSPECT.route) { InspectRoute() }
            composable(DiagTab.SIGNALS.route) { SignalsRoute() }
            composable(DiagTab.SEQUENCE.route) { SequenceRoute() }
            composable(DiagTab.LOG.route) { LogRoute() }
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Bluetooth permission needed",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "Scanning for the cluster requires " +
                BlePermissions.required.joinToString { it.substringAfterLast('.') } + ".",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onRequest) { Text("Grant") }
    }
}
