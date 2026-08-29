package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.protocol.ClusterProtocol
import dev.jay.betterconnect.core.protocol.TbtEncoder
import dev.jay.betterconnect.core.protocol.TbtFrame
import dev.jay.betterconnect.core.testing.FakeClusterTransport
import dev.jay.betterconnect.core.testing.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WriteSchedulerTest {

    private val encoder = TbtEncoder()
    private val frame = TbtFrame(encoder.encode(TestData.navLeft500))

    @Test
    fun `setting a frame sends it immediately`() = runTest {
        val transport = FakeClusterTransport()
        val scheduler = WriteScheduler(transport)

        scheduler.setFrame(frame)

        assertEquals(listOf(frame), transport.received)
        assertEquals(1, scheduler.stats.value.sent)
    }

    @Test
    fun `heartbeat re-asserts the same frame every 800 ms`() = runTest {
        val transport = FakeClusterTransport()
        val scheduler = WriteScheduler(transport)
        val job = scheduler.start(backgroundScope)

        scheduler.setFrame(frame)
        advanceTimeBy(ClusterProtocol.TBT_PERIOD_MS * 3 + 1)

        // one immediate send plus three ticks
        assertEquals(4, transport.received.size)
        assertTrue(transport.received.all { it == frame })
        job.cancel()
    }

    @Test
    fun `one shot mode does not repeat`() = runTest {
        val transport = FakeClusterTransport()
        val scheduler = WriteScheduler(transport)
        scheduler.setMode(SendMode.ONE_SHOT)
        val job = scheduler.start(backgroundScope)

        scheduler.setFrame(frame)
        advanceTimeBy(ClusterProtocol.TBT_PERIOD_MS * 10)

        assertEquals(1, transport.received.size)
        job.cancel()
    }

    /**
     * Queueing would surface stale instructions late. On a bike a brief gap is safer than
     * a turn arrow that arrives after the turn.
     */
    @Test
    fun `a frame arriving while a write is in flight is dropped, not queued`() = runTest {
        val transport = FakeClusterTransport().apply { autoCompleteWrites = false }
        val scheduler = WriteScheduler(transport)
        val job = scheduler.start(backgroundScope)

        scheduler.setFrame(frame)
        advanceTimeBy(ClusterProtocol.TBT_PERIOD_MS * 3 + 1)

        assertEquals("only the first write got through", 1, transport.received.size)
        assertEquals(3, scheduler.stats.value.dropped)

        // Once the stack completes the write, the next tick sends current state again.
        transport.completeWrite()
        advanceTimeBy(ClusterProtocol.TBT_PERIOD_MS + 1)
        assertEquals(2, transport.received.size)
        job.cancel()
    }

    @Test
    fun `writes while disconnected are counted as not ready`() = runTest {
        val transport = FakeClusterTransport(ConnectionState.Idle)
        val scheduler = WriteScheduler(transport)

        scheduler.setFrame(frame)

        assertTrue(transport.received.isEmpty())
        assertEquals(1, scheduler.stats.value.notReady)
    }

    @Test
    fun `rejected writes are counted as failed`() = runTest {
        val transport = FakeClusterTransport().apply { forcedOutcome = WriteOutcome.FAILED }
        val scheduler = WriteScheduler(transport)

        scheduler.setFrame(frame)

        assertEquals(1, scheduler.stats.value.failed)
    }

    @Test
    fun `clear sends the all-zero frame and stops repeating content`() = runTest {
        val transport = FakeClusterTransport()
        val scheduler = WriteScheduler(transport)
        val job = scheduler.start(backgroundScope)

        scheduler.setFrame(frame)
        scheduler.clear()
        advanceTimeBy(ClusterProtocol.TBT_PERIOD_MS * 3 + 1)

        assertEquals(TbtFrame.endNavigation(), transport.received.last())
        assertNull(scheduler.currentFrame.value)
        assertEquals("nothing further should be sent", 2, transport.received.size)
        job.cancel()
    }

    @Test
    fun `nothing is sent before a frame is set`() = runTest {
        val transport = FakeClusterTransport()
        val scheduler = WriteScheduler(transport)
        val job = scheduler.start(backgroundScope)

        advanceTimeBy(ClusterProtocol.TBT_PERIOD_MS * 5)

        assertTrue(transport.received.isEmpty())
        job.cancel()
    }

    @Test
    fun `stopping halts the heartbeat`() = runTest {
        val transport = FakeClusterTransport()
        val scheduler = WriteScheduler(transport)
        scheduler.start(backgroundScope)

        scheduler.setFrame(frame)
        advanceTimeBy(ClusterProtocol.TBT_PERIOD_MS + 1)
        val afterOneTick = transport.received.size

        scheduler.stop()
        advanceTimeBy(ClusterProtocol.TBT_PERIOD_MS * 5)

        assertEquals(afterOneTick, transport.received.size)
    }

    @Test
    fun `the cluster receives exactly what the caller asked for`() = runTest {
        val transport = FakeClusterTransport()
        val scheduler = WriteScheduler(transport)

        scheduler.setFrame(frame)

        val decoded = transport.lastDecoded!!
        assertEquals('I', decoded.symbolChar)
        assertEquals("MG ROAD", decoded.text)
        assertEquals(500, decoded.turn.whole)
        assertTrue(transport.badFrames.isEmpty())
    }
}
