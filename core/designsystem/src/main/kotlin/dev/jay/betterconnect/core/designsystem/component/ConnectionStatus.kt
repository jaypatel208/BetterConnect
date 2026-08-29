package dev.jay.betterconnect.core.designsystem.component

import androidx.compose.ui.graphics.Color
import dev.jay.betterconnect.core.designsystem.theme.StatusColors
import dev.jay.betterconnect.core.model.ConnectionState

/** Label and colour for a [ConnectionState], shared by every screen that shows link status. */
fun ConnectionState.describe(): Pair<String, Color> = when (this) {
    ConnectionState.Idle -> "IDLE" to StatusColors.Idle
    is ConnectionState.Connecting -> "CONNECTING" to StatusColors.Warn
    is ConnectionState.Discovering -> "DISCOVERING" to StatusColors.Warn
    is ConnectionState.Ready -> "READY" to StatusColors.Ok
    is ConnectionState.Unsupported -> "UNSUPPORTED" to StatusColors.Error
    is ConnectionState.Disconnected -> "DISCONNECTED" to StatusColors.Idle
}
