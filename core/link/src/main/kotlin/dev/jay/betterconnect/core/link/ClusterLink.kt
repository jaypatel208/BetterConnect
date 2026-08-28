package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.UnsupportedReason
import dev.jay.betterconnect.core.protocol.ClusterProtocol

/**
 * The connection state machine, as a pure reducer.
 *
 * Sequence per CONNECTION.md §4: connect, request MTU, discover services, locate
 * TBT_INFO. The MTU step is the one the official app skips - a 48-byte write needs
 * MTU >= 51, so we request it explicitly and refuse to pretend a small MTU will work.
 */
object ClusterLink {

    /** Backoff before retrying a dropped connection. */
    const val RECONNECT_DELAY_MS: Long = 3_000L

    fun reduce(state: ConnectionState, event: LinkEvent): LinkTransition = when (event) {
        is LinkEvent.ConnectRequested -> LinkTransition(
            state = ConnectionState.Connecting(event.address),
            commands = listOf(LinkCommand.Connect(event.address)),
        )

        LinkEvent.DisconnectRequested -> LinkTransition(
            state = ConnectionState.Disconnected(state.addressOrNull, status = 0),
            commands = buildList {
                // Only worth clearing the display if we ever got far enough to write to it.
                if (state is ConnectionState.Ready) add(LinkCommand.SendEndNavigation)
                add(LinkCommand.Close)
            },
        )

        is LinkEvent.Connected -> LinkTransition(
            state = ConnectionState.Discovering(event.address, mtu = UNKNOWN_MTU),
            commands = listOf(LinkCommand.RequestMtu(ClusterProtocol.REQUESTED_MTU)),
        )

        is LinkEvent.MtuNegotiated -> {
            val address = state.addressOrNull.orEmpty()
            if (event.mtu < ClusterProtocol.MIN_MTU) {
                // Fail loudly. Silently writing 48 bytes over a 23-byte MTU is the exact
                // class of bug that is impossible to diagnose against a cluster that
                // never answers back.
                LinkTransition(
                    state = ConnectionState.Unsupported(address, UnsupportedReason.MTU_TOO_SMALL),
                    commands = listOf(LinkCommand.Close),
                )
            } else {
                LinkTransition(
                    state = ConnectionState.Discovering(address, event.mtu),
                    commands = listOf(LinkCommand.DiscoverServices),
                )
            }
        }

        LinkEvent.MtuRequestFailed -> LinkTransition(
            state = state,
            commands = listOf(LinkCommand.DiscoverServices),
        )

        is LinkEvent.ServicesResolved -> {
            val dump = event.dump
            val service = dump.services.firstOrNull {
                it.uuid.equalsIgnoreCase(ClusterProtocol.SERVICE_UUID.toString())
            }
            val characteristic = service?.characteristics?.firstOrNull { it.isTbtInfo }

            val reason = when {
                service == null -> UnsupportedReason.SERVICE_MISSING
                characteristic == null -> UnsupportedReason.CHARACTERISTIC_MISSING
                !characteristic.writable -> UnsupportedReason.NOT_WRITABLE
                else -> null
            }

            if (reason == null) {
                LinkTransition(ConnectionState.Ready(dump.address, dump.mtu))
            } else {
                LinkTransition(ConnectionState.Unsupported(dump.address, reason))
            }
        }

        is LinkEvent.Disconnected -> {
            val address = state.addressOrNull
            LinkTransition(
                state = ConnectionState.Disconnected(address, event.status),
                commands = buildList {
                    add(LinkCommand.Close)
                    // An Unsupported device will not become supported by reconnecting.
                    if (address != null && state !is ConnectionState.Unsupported) {
                        add(LinkCommand.ScheduleReconnect(address, RECONNECT_DELAY_MS))
                    }
                },
            )
        }
    }

    const val UNKNOWN_MTU: Int = 0
}

internal val ConnectionState.addressOrNull: String?
    get() = when (this) {
        is ConnectionState.Connecting -> address
        is ConnectionState.Discovering -> address
        is ConnectionState.Ready -> address
        is ConnectionState.Unsupported -> address
        is ConnectionState.Disconnected -> address
        ConnectionState.Idle -> null
    }

private fun String.equalsIgnoreCase(other: String) = equals(other, ignoreCase = true)
