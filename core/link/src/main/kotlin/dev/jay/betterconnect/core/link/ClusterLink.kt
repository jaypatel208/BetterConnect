package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.UnsupportedReason
import dev.jay.betterconnect.core.protocol.ClusterProtocol

/**
 * The connection state machine, as a pure reducer.
 *
 * Sequence per `.claude/rules/protocol.md` "Cadences and connect ordering" (A5): connect,
 * request `BALANCED` connection priority, wait [PRIORITY_SETTLE_MS] for it to take, discover
 * services, **then** request MTU once the TBT characteristic is confirmed present and
 * writable. This is the opposite order from an earlier version of this file, which requested
 * MTU immediately on connect and discovered services only after MTU came back - that order
 * is not what the docs specify and is a candidate for the 65 s disconnect (tracker D1).
 *
 * **Deliberate deviation from `.claude/rules/protocol.md`'s literal wording**: the docs
 * describe a vendor fallback that force-enables writes 2500 ms after `requestMtu` if
 * `onMtuChanged` never fires. That is copying the vendor's *technique*, not their
 * *constraint* - this project's own rule is "never write before `onMtuChanged`" (writes are
 * MTU-gated; a 48-byte frame needs MTU >= 51), and forcing writes at an unconfirmed MTU
 * directly violates it. So an MTU request that never resolves stays [ConnectionState.Discovering]
 * rather than being forced to [ConnectionState.Ready] - a stuck link is diagnosable from the
 * ride log; a link silently dropping oversized frames is not.
 */
object ClusterLink {

    /** Backoff before retrying a dropped connection. */
    const val RECONNECT_DELAY_MS: Long = 3_000L

    /** Settle time between requesting `BALANCED` priority and calling `discoverServices()`. */
    const val PRIORITY_SETTLE_MS: Long = 300L

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
            commands = listOf(
                LinkCommand.RequestConnectionPriority,
                LinkCommand.DiscoverServices(delayMs = PRIORITY_SETTLE_MS),
            ),
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
                LinkTransition(
                    state = ConnectionState.Discovering(dump.address, UNKNOWN_MTU),
                    commands = listOf(LinkCommand.RequestMtu(ClusterProtocol.REQUESTED_MTU)),
                )
            } else {
                LinkTransition(
                    state = ConnectionState.Unsupported(dump.address, reason),
                    commands = listOf(LinkCommand.Close),
                )
            }
        }

        is LinkEvent.MtuNegotiated -> {
            // A stray callback after the service check already failed changes nothing.
            if (state !is ConnectionState.Discovering) {
                LinkTransition(state)
            } else if (event.mtu < ClusterProtocol.MIN_MTU) {
                // Fail loudly. Silently writing 48 bytes over a small MTU is the exact
                // class of bug that is impossible to diagnose against a cluster that
                // never answers back.
                LinkTransition(
                    state = ConnectionState.Unsupported(state.address, UnsupportedReason.MTU_TOO_SMALL),
                    commands = listOf(LinkCommand.Close),
                )
            } else {
                LinkTransition(ConnectionState.Ready(state.address, event.mtu))
            }
        }

        LinkEvent.MtuRequestFailed -> LinkTransition(state)

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
