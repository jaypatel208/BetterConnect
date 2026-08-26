package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.UnsupportedReason
import dev.jay.betterconnect.core.protocol.ClusterProtocol
import dev.jay.betterconnect.core.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ADDRESS = "AA:BB:CC:DD:EE:FF"

class ClusterLinkTest {

    private fun run(vararg events: LinkEvent): LinkTransition {
        var transition = LinkTransition(ConnectionState.Idle)
        events.forEach { transition = ClusterLink.reduce(transition.state, it) }
        return transition
    }

    @Test
    fun `happy path reaches Ready`() {
        val transition = run(
            LinkEvent.ConnectRequested(ADDRESS),
            LinkEvent.Connected(ADDRESS),
            LinkEvent.MtuNegotiated(64),
            LinkEvent.ServicesResolved(TestData.healthyDump(ADDRESS, mtu = 64)),
        )
        assertEquals(ConnectionState.Ready(ADDRESS, 64), transition.state)
    }

    @Test
    fun `connect asks the radio to connect`() {
        val transition = run(LinkEvent.ConnectRequested(ADDRESS))
        assertEquals(listOf(LinkCommand.Connect(ADDRESS)), transition.commands)
    }

    /** The official app never requests an MTU; a 48-byte write cannot fit the default 23. */
    @Test
    fun `connecting immediately requests a larger MTU`() {
        val transition = run(LinkEvent.ConnectRequested(ADDRESS), LinkEvent.Connected(ADDRESS))
        assertEquals(
            listOf(LinkCommand.RequestMtu(ClusterProtocol.REQUESTED_MTU)),
            transition.commands,
        )
    }

    @Test
    fun `an MTU below the frame size fails loudly instead of writing anyway`() {
        val transition = run(
            LinkEvent.ConnectRequested(ADDRESS),
            LinkEvent.Connected(ADDRESS),
            LinkEvent.MtuNegotiated(23),
        )
        assertEquals(
            ConnectionState.Unsupported(ADDRESS, UnsupportedReason.MTU_TOO_SMALL),
            transition.state,
        )
        assertTrue(LinkCommand.Close in transition.commands)
    }

    @Test
    fun `the smallest usable MTU is accepted`() {
        val transition = run(
            LinkEvent.ConnectRequested(ADDRESS),
            LinkEvent.Connected(ADDRESS),
            LinkEvent.MtuNegotiated(ClusterProtocol.MIN_MTU),
        )
        assertTrue(transition.state is ConnectionState.Discovering)
        assertTrue(LinkCommand.DiscoverServices in transition.commands)
    }

    @Test
    fun `a refused MTU request still proceeds to discovery`() {
        val transition = run(
            LinkEvent.ConnectRequested(ADDRESS),
            LinkEvent.Connected(ADDRESS),
            LinkEvent.MtuRequestFailed,
        )
        assertTrue(LinkCommand.DiscoverServices in transition.commands)
    }

    @Test
    fun `missing service is reported distinctly`() {
        val transition = reachDiscovery(TestData.serviceMissingDump(ADDRESS))
        assertEquals(
            ConnectionState.Unsupported(ADDRESS, UnsupportedReason.SERVICE_MISSING),
            transition.state,
        )
    }

    @Test
    fun `missing TBT characteristic is reported distinctly`() {
        val transition = reachDiscovery(TestData.characteristicMissingDump(ADDRESS))
        assertEquals(
            ConnectionState.Unsupported(ADDRESS, UnsupportedReason.CHARACTERISTIC_MISSING),
            transition.state,
        )
    }

    @Test
    fun `a read-only TBT characteristic is reported distinctly`() {
        val transition = reachDiscovery(TestData.notWritableDump(ADDRESS))
        assertEquals(
            ConnectionState.Unsupported(ADDRESS, UnsupportedReason.NOT_WRITABLE),
            transition.state,
        )
    }

    private fun reachDiscovery(dump: dev.jay.betterconnect.core.model.GattDump) = run(
        LinkEvent.ConnectRequested(ADDRESS),
        LinkEvent.Connected(ADDRESS),
        LinkEvent.MtuNegotiated(64),
        LinkEvent.ServicesResolved(dump),
    )

    @Test
    fun `an unexpected disconnect schedules a reconnect`() {
        val transition = run(
            LinkEvent.ConnectRequested(ADDRESS),
            LinkEvent.Connected(ADDRESS),
            LinkEvent.MtuNegotiated(64),
            LinkEvent.ServicesResolved(TestData.healthyDump(ADDRESS, 64)),
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
            LinkEvent.MtuNegotiated(23),
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
            LinkEvent.MtuNegotiated(64),
            LinkEvent.ServicesResolved(TestData.healthyDump(ADDRESS, 64)),
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
        assertTrue(transition.commands.none { it is LinkCommand.ScheduleReconnect })
    }
}
