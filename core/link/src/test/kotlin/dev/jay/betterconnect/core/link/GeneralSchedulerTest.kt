package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.model.ControlAcks
import dev.jay.betterconnect.core.model.GeneralState
import dev.jay.betterconnect.core.model.GeneralVersion
import dev.jay.betterconnect.core.protocol.ClusterProtocol
import dev.jay.betterconnect.core.protocol.GeneralEncoder
import dev.jay.betterconnect.core.testing.FakeClusterTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralSchedulerTest {

    @Test
    fun `sends immediately on start, then every 1000 ms`() = runTest {
        val transport = FakeClusterTransport()
        val scheduler = GeneralScheduler(transport, MutableStateFlow(ControlAcks()))
        val job = scheduler.start(backgroundScope)

        assertEquals(1, transport.receivedGeneral.size)
        advanceTimeBy(ClusterProtocol.GENERAL_PERIOD_MS * 2 + 1)
        assertEquals(3, transport.receivedGeneral.size)
        job.cancel()
    }

    @Test
    fun `the heartbeat byte advances on every send`() = runTest {
        val transport = FakeClusterTransport()
        val scheduler = GeneralScheduler(transport, MutableStateFlow(ControlAcks()))
        val job = scheduler.start(backgroundScope)

        advanceTimeBy(ClusterProtocol.GENERAL_PERIOD_MS * 3 + 1)

        val heartbeats = transport.decodedGeneral.map { it.heartbeat }
        assertEquals(listOf(1, 2, 3, 4), heartbeats)
        job.cancel()
    }

    /** D2 - unconfirmed on hardware, so this is a live flag, not a constructor constant. */
    @Test
    fun `defaults to v1, the size that carries no phone name and no checksum`() = runTest {
        val transport = FakeClusterTransport()
        val scheduler = GeneralScheduler(transport, MutableStateFlow(ControlAcks()))
        val job = scheduler.start(backgroundScope)

        assertEquals(GeneralEncoder.SIZE_V1, transport.receivedGeneral.first().size)
        job.cancel()
    }

    @Test
    fun `switching to v2 changes the wire size without restarting the scheduler`() = runTest {
        val transport = FakeClusterTransport()
        val scheduler = GeneralScheduler(transport, MutableStateFlow(ControlAcks()))
        val job = scheduler.start(backgroundScope)

        scheduler.setVersion(GeneralVersion.V2)
        advanceTimeBy(ClusterProtocol.GENERAL_PERIOD_MS + 1)

        val decoded = transport.decodedGeneral.last()
        assertEquals(GeneralVersion.V2, decoded.version)
        assertEquals(GeneralEncoder.SIZE_V2, transport.receivedGeneral.last().size)
        job.cancel()
    }

    @Test
    fun `acks are read fresh at send time, not captured at construction`() = runTest {
        val transport = FakeClusterTransport()
        val acks = MutableStateFlow(ControlAcks())
        val scheduler = GeneralScheduler(transport, acks)
        val job = scheduler.start(backgroundScope)

        acks.value = ControlAcks(callAccept = 42)
        advanceTimeBy(ClusterProtocol.GENERAL_PERIOD_MS + 1)

        assertEquals(42, transport.decodedGeneral.last().acks.callAccept)
        job.cancel()
    }

    @Test
    fun `app state changes are reflected on the next send`() = runTest {
        val transport = FakeClusterTransport()
        val scheduler = GeneralScheduler(transport, MutableStateFlow(ControlAcks()))
        val job = scheduler.start(backgroundScope)

        scheduler.setState(GeneralState(volume = 5, batteryBars = 2))
        advanceTimeBy(ClusterProtocol.GENERAL_PERIOD_MS + 1)

        val decoded = transport.decodedGeneral.last()
        assertEquals(5, decoded.volume)
        assertEquals(2, decoded.batteryBars)
        job.cancel()
    }
}
