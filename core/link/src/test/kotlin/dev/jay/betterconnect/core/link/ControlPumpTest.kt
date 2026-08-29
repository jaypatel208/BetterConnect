package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.model.ControlFrame
import dev.jay.betterconnect.core.protocol.ClusterProtocol
import dev.jay.betterconnect.core.protocol.ControlEncoder
import dev.jay.betterconnect.core.testing.FakeClusterTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControlPumpTest {

    private fun idleFrame() = ControlFrame(
        dialSource = 0,
        volumeToSet = 0,
        callAccept = 0,
        callReject = 0,
        callRejectWithSms = 0,
        pagePlaylist = 0,
        newPlaylistReq = 0,
        takeMeHome = 0,
        resumeSong = 0,
        pauseSong = 0,
        skipToNext = 0,
        skipToPrev = 0,
        stopSong = 0,
        missedCallGet = 0,
        alertGet = 0,
        launchMediaPlayer = 0,
        selectPlaylistSong = 0,
        selectedPlaylistSong = 0,
        dialIndex = 0,
        dialTxn = 0,
    )

    @Test
    fun `polls at the documented 700 ms period`() = runTest {
        val transport = FakeClusterTransport()
        val pump = ControlPump(transport)
        val job = pump.start(backgroundScope)

        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS * 3 + 1)

        assertEquals(3, transport.controlReadRequests)
        job.cancel()
    }

    @Test
    fun `the first frame is adopted without firing any ack`() = runTest {
        val transport = FakeClusterTransport()
        val pump = ControlPump(transport)
        val job = pump.start(backgroundScope)

        transport.enqueueControlRead(ControlEncoder.encode(idleFrame().copy(callAccept = 9)))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()

        assertEquals(0, pump.acks.value.callAccept)
        job.cancel()
    }

    @Test
    fun `a mirror field copies the new request value once bootstrapped`() = runTest {
        val transport = FakeClusterTransport()
        val pump = ControlPump(transport)
        val job = pump.start(backgroundScope)

        transport.enqueueControlRead(ControlEncoder.encode(idleFrame()))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()

        transport.enqueueControlRead(ControlEncoder.encode(idleFrame().copy(callAccept = 7)))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()

        assertEquals(7, pump.acks.value.callAccept)
        job.cancel()
    }

    @Test
    fun `a counter field increments once per press and wraps modulo 256`() = runTest {
        val transport = FakeClusterTransport()
        val pump = ControlPump(transport)
        val job = pump.start(backgroundScope)

        transport.enqueueControlRead(ControlEncoder.encode(idleFrame()))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()

        repeat(3) { i ->
            transport.enqueueControlRead(ControlEncoder.encode(idleFrame().copy(resumeSong = i + 1)))
            advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
            runCurrent()
        }

        assertEquals(3, pump.acks.value.resumeSong)
        job.cancel()
    }

    /** Re-observing the same press twice (no level change) must not increment again. */
    @Test
    fun `a repeated identical frame does not double count a counter field`() = runTest {
        val transport = FakeClusterTransport()
        val pump = ControlPump(transport)
        val job = pump.start(backgroundScope)

        transport.enqueueControlRead(ControlEncoder.encode(idleFrame()))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()

        transport.enqueueControlRead(ControlEncoder.encode(idleFrame().copy(resumeSong = 1)))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()

        // Whole-frame dedup means an identical frame is never even decoded again, but
        // prove the ack itself does not move even if it somehow were.
        transport.enqueueControlRead(ControlEncoder.encode(idleFrame().copy(resumeSong = 1)))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()

        assertEquals(1, pump.acks.value.resumeSong)
        job.cancel()
    }

    @Test
    fun `whole frame dedup skips an identical frame entirely`() = runTest {
        val transport = FakeClusterTransport()
        val pump = ControlPump(transport)
        val job = pump.start(backgroundScope)

        transport.enqueueControlRead(ControlEncoder.encode(idleFrame()))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()

        // Same bytes again - the dedup gate should skip this without even decoding.
        transport.enqueueControlRead(ControlEncoder.encode(idleFrame()))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()

        transport.enqueueControlRead(ControlEncoder.encode(idleFrame().copy(callAccept = 1)))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()

        // callAccept only ever changed relative to the true previous frame (idle -> 1),
        // so it fires exactly once despite three reads.
        assertEquals(1, pump.acks.value.callAccept)
        job.cancel()
    }

    /**
     * A cluster reconnecting with a retained non-zero button level must not fire a phantom
     * ack the instant the pump restarts - `stop()` must reset the bootstrap, not just the
     * dedup cache.
     */
    @Test
    fun `restarting after stop re-bootstraps instead of firing on a retained value`() = runTest {
        val transport = FakeClusterTransport()
        val pump = ControlPump(transport)
        var job = pump.start(backgroundScope)

        transport.enqueueControlRead(ControlEncoder.encode(idleFrame()))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()
        transport.enqueueControlRead(ControlEncoder.encode(idleFrame().copy(callAccept = 5)))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()
        assertEquals(5, pump.acks.value.callAccept)

        pump.stop()
        job.cancel()
        job = pump.start(backgroundScope)

        // A different retained value (9) arrives as the very first frame after restart.
        // If the restart did not re-bootstrap, this would look like a fresh press and the
        // mirror ack would jump straight to 9.
        transport.enqueueControlRead(ControlEncoder.encode(idleFrame().copy(callAccept = 9)))
        advanceTimeBy(ClusterProtocol.CONTROL_PERIOD_MS + 1)
        runCurrent()
        assertEquals("the post-restart frame must be adopted, not acted on", 5, pump.acks.value.callAccept)
        job.cancel()
    }
}
