package dev.jay.betterconnect

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.jay.betterconnect.feature.debug.Debug
import dev.jay.betterconnect.feature.debug.debugEntry
import dev.jay.betterconnect.feature.devices.Devices
import dev.jay.betterconnect.feature.devices.devicesEntry
import dev.jay.betterconnect.feature.navigation.Navigation
import dev.jay.betterconnect.feature.navigation.navigationEntry
import dev.jay.betterconnect.feature.onboarding.Onboarding
import dev.jay.betterconnect.feature.onboarding.onboardingEntry

@Composable
fun FullApp() {
    val backStack = rememberNavBackStack(Onboarding)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            onboardingEntry(
                onGranted = {
                    if (backStack.lastOrNull() != Home) {
                        backStack.clear()
                        backStack.add(Home)
                    }
                },
            )
            homeEntry(
                onNavigateToDevices = { backStack.add(Devices) },
                onNavigateToNavigation = { backStack.add(Navigation) },
                onNavigateToDebug = { backStack.add(Debug) },
            )
            devicesEntry(
                onConnected = { backStack.removeLastOrNull() },
            )
            navigationEntry(
                onClose = { backStack.removeLastOrNull() },
            )
            debugEntry(
                onClose = { backStack.removeLastOrNull() },
            )
        },
    )
}
