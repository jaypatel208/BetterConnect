package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.GattDump
import dev.jay.betterconnect.core.model.UnsupportedReason
import dev.jay.betterconnect.core.protocol.ClusterProtocol
import dev.jay.betterconnect.core.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ADDRESS = "AA:BB:CC:DD:EE:FF"

/**
 * The connect ordering here is deliberate (A5, `.claude/rules/protocol.md`): priority is
 * requested and services are discovered **before** MTU, not after. An earlier version of
 * this reducer did it the other way around, which is a candidate for the 65 s disconnect.
 */
class ClusterLinkTest {

    private fun run(vararg events: LinkEvent): LinkTransition {
        var transition = LinkTransition(ConnectionState.Idle)
        events.forEach { transition = ClusterLink.reduce(transition.state, it) }
        return transition
    }

    private fun reachServicesResolved(dump: GattDump = TestData.healthyDump(ADDRESS, mtu = 64)) = run(
        LinkEvent.ConnectRequested(ADDRESS),
        LinkEvent.Connected(ADDRESS),
        LinkEvent.ServicesResolved(dump),
    )

    @Test
    fun `happy path reaches Ready`() {
        val transition = run(
            LinkEvent.ConnectRequested(ADDRESS),
            LinkEvent.Connected(ADDRESS),
            LinkEvent.ServicesResolved(TestData.healthyDump(ADDRESS, mtu = 64)),
            LinkEvent.MtuNegotiated(64),
        )
        assertEquals(ConnectionState.Ready(ADDRESS, 64), transition.state)
    }

    @Test
    fun `connect asks the radio to connect`() {
        val transition = run(LinkEvent.ConnectRequested(ADDRESS))
        assertEquals(listOf(LinkCommand.Connect(ADDRESS)), transition.commands)
    }

    /** BALANCED priority first, then a settle delay before discovery - never MTU here. */
    @Test
    fun `connecting requests priority then discovers services after a settle delay`() {
        val transition = run(LinkEvent.ConnectRequested(ADDRESS), LinkEvent.Connected(ADDRESS))
        assertEquals(
            listOf(
                LinkCommand.RequestConnectionPriority,
                LinkCommand.DiscoverServices(delayMs = ClusterLink.PRIORITY_SETTLE_MS),
            ),
            transition.commands,
        )
    }

    /** MTU is requested only once the TBT characteristic is confirmed present and writable. */
    @Test
    fun `a healthy service discovery requests MTU`() {
        val transition = reachServicesResolved()
        assertEquals(
            listOf(LinkCommand.RequestMtu(ClusterProtocol.REQUESTED_MTU)),
            transition.commands,
        )
        assertTrue(transition.state is ConnectionState.Discovering)
    }

    @Test
    fun `an MTU below the frame size fails loudly instead of writing anyway`() {
        val afterMtu = ClusterLink.reduce(reachServicesResolved().state, LinkEvent.MtuNegotiated(23))
        assertEquals(
            ConnectionState.Unsupported(ADDRESS, UnsupportedReason.MTU_TOO_SMALL),
            afterMtu.state,
        )
        assertTrue(LinkCommand.Close in afterMtu.commands)
    }

    @Test
    fun `the smallest usable MTU is accepted`() {
        val afterMtu = ClusterLink.reduce(
            reachServicesResolved().state,
            LinkEvent.MtuNegotiated(ClusterProtocol.MIN_MTU),
        )
        assertEquals(ConnectionState.Ready(ADDRESS, ClusterProtocol.MIN_MTU), afterMtu.state)
    }

    /**
     * Per this project's own rule ("never write before onMtuChanged"), a failed or
     * never-answered MTU request leaves the link waiting rather than forcing writes at an
     * unconfirmed MTU - the vendor's technique, not its constraint.
     */
    @Test
    fun `a failed MTU request leaves the link waiting rather than forcing writes`() {
        val discovering = reachServicesResolved().state
        val transition = ClusterLink.reduce(discovering, LinkEvent.MtuRequestFailed)
        assertEquals(discovering, transition.state)
        assertTrue(transition.commands.isEmpty())
    }

    /** A stray MTU callback after the service check already failed changes nothing. */
    @Test
    fun `a late MTU callback after an unsupported verdict is ignored`() {
        val unsupported = run(
            LinkEvent.ConnectRequested(ADDRESS),
            LinkEvent.Connected(ADDRESS),
            LinkEvent.ServicesResolved(TestData.serviceMissingDump(ADDRESS)),
        )
        val transition = ClusterLink.reduce(unsupported.state, LinkEvent.MtuNegotiated(64))
        assertEquals(unsupported.state, transition.state)
    }

    @Test
    fun `missing service is reported distinctly`() {
        val transition = reachServicesResolved(TestData.serviceMissingDump(ADDRESS))
        assertEquals(
            ConnectionState.Unsupported(ADDRESS, UnsupportedReason.SERVICE_MISSING),
            transition.state,
        )
        assertTrue(LinkCommand.Close in transition.commands)
    }

    @Test
    fun `missing TBT characteristic is reported distinctly`() {
        val transition = reachServicesResolved(TestData.characteristicMissingDump(ADDRESS))
        assertEquals(
            ConnectionState.Unsupported(ADDRESS, UnsupportedReason.CHARACTERISTIC_MISSING),
            transition.state,
        )
    }

    @Test
    fun `a read-only TBT characteristic is reported distinctly`() {
        val transition = reachServicesResolved(TestData.notWritableDump(ADDRESS))
        assertEquals(
            ConnectionState.Unsupported(ADDRESS, UnsupportedReason.NOT_WRITABLE),
            transition.state,
        )
    }

    @Test
    fun `an unexpected disconnect schedules a reconnect`() {
        val transition = run(
            LinkEvent.ConnectRequested(ADDRESS),
            LinkEvent.Connected(ADDRESS),
            LinkEvent.ServicesResolved(TestData.healthyDump(ADDRESS, 64)),
            LinkEvent.MtuNegotiated(64),
            LinkEvent.Disconnected(status = 19),
        )
        assertEquals(ConnectionState.Disconnected(ADDRESS, 19), transition.state)
        assertTrue(
            LinkCommand.ScheduleReconnect(ADDRESS, ClusterLink.RECONNECT_DELAY_MS) in transition.commands,
        )
    }

    /** Reconnecting to a device that does not speak the protocol would just loop forever. */
    @Test
    fun `an unsupported device is not retried`() {
        val transition = run(
            LinkEvent.ConnectRequested(ADDRESS),
            LinkEvent.Connected(ADDRESS),
            LinkEvent.ServicesResolved(TestData.serviceMissingDump(ADDRESS)),
            LinkEvent.Disconnected(status = 0),
        )
        assertTrue(transition.commands.none { it is LinkCommand.ScheduleReconnect })
    }

    /** Otherwise the last instruction stays frozen on the cluster after we walk away. */
    @Test
    fun `an explicit disconnect clears the display first`() {
        val transition = run(
            LinkEvent.ConnectRequested(ADDRESS),
            LinkEvent.Connected(ADDRESS),
            LinkEvent.ServicesResolved(TestData.healthyDump(ADDRESS, 64)),
            LinkEvent.MtuNegotiated(64),
            LinkEvent.DisconnectRequested,
        )
        assertEquals(
            listOf(LinkCommand.SendEndNavigation, LinkCommand.Close),
            transition.commands,
        )
    }

    @Test
    fun `disconnecting before Ready does not try to clear a display we never reached`() {
        val transition = run(
            LinkEvent.ConnectRequested(ADDRESS),
            LinkEvent.DisconnectRequested,
        )
        assertEquals(listOf(LinkCommand.Close), transition.commands)
    }

    @Test
    fun `a user requested disconnect is not retried`() {
        val transition = run(
            LinkEvent.ConnectRequested(ADDRESS),
            LinkEvent.Connected(ADDRESS),
            LinkEvent.DisconnectRequested,
        )
        assertFalse(transition.commands.any { it is LinkCommand.ScheduleReconnect })
    }
}
