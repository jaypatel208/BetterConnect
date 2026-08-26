package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.model.GattDump

/**
 * Everything that can happen to the cluster link, expressed as data.
 *
 * The Android BLE callbacks translate into these and nothing else, which keeps the
 * connection logic in a pure reducer that needs no device and no Robolectric to test.
 */
sealed interface LinkEvent {
    data class ConnectRequested(val address: String) : LinkEvent
    data object DisconnectRequested : LinkEvent

    data class Connected(val address: String) : LinkEvent
    data class MtuNegotiated(val mtu: Int) : LinkEvent
    data class ServicesResolved(val dump: GattDump) : LinkEvent
    data class Disconnected(val status: Int) : LinkEvent

    /** MTU negotiation is best-effort; a refusal still has to proceed to discovery. */
    data object MtuRequestFailed : LinkEvent
}

/** Side effects the reducer asks the Android layer to perform. */
sealed interface LinkCommand {
    data class Connect(val address: String) : LinkCommand
    data class RequestMtu(val mtu: Int) : LinkCommand
    data object DiscoverServices : LinkCommand
    data object Close : LinkCommand
    data class ScheduleReconnect(val address: String, val delayMs: Long) : LinkCommand

    /** Clears the cluster display. Must happen before closing, or the last frame sticks. */
    data object SendEndNavigation : LinkCommand
}

data class LinkTransition(
    val state: dev.jay.betterconnect.core.model.ConnectionState,
    val commands: List<LinkCommand> = emptyList(),
)
