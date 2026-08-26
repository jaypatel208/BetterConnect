package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.link.WriteScheduler
import dev.jay.betterconnect.core.protocol.TbtEncoder
import dev.jay.betterconnect.core.protocol.TbtFrame
import dev.jay.betterconnect.core.testing.FakeClusterTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SequenceRunnerTest {

    private val encoder = TbtEncoder()

    private fun fixture(): Triple<FakeClusterTransport, WriteScheduler, SequenceRunner> {
        val transport = FakeClusterTransport()
        val scheduler = WriteScheduler(transport)
        return Triple(transport, scheduler, SequenceRunner(scheduler, encoder))
    }

    @Test
    fun `steps advance on the dwell interval`() = runTest {
        val (_, _, runner) = fixture()
        val script = SequenceScripts.ROUTE_WALK

        runner.start(backgroundScope, script, dwellMs = 1_000L)
        runCurrent()
        assertEquals(0, runner.progress.value?.index)

        advanceTimeBy(1_001)
        assertEquals(1, runner.progress.value?.index)

        advanceTimeBy(1_000)
        assertEquals(2, runner.progress.value?.index)
        runner.stop()
    }

    @Test
    fun `a full pass sends one frame per step in order`() = runTest {
        val (transport, _, runner) = fixture()
        val script = SequenceScripts.ROUTE_WALK

        runner.start(backgroundScope, script, dwellMs = 100L)
        advanceTimeBy(100L * script.steps.size + 10)

        assertEquals(script.steps.size, transport.received.size)
        script.steps.forEachIndexed { index, step ->
            assertEquals(
                "step ${step.label}",
                TbtFrame(encoder.encode(step.nav)),
                transport.received[index],
            )
        }
    }

    @Test
    fun `progress clears when the script finishes`() = runTest {
        val (_, _, runner) = fixture()
        val script = SequenceScripts.TEXT_SWEEP

        runner.start(backgroundScope, script, dwellMs = 10L)
        advanceTimeBy(10L * script.steps.size + 50)

        assertNull(runner.progress.value)
    }

    @Test
    fun `looping restarts from the first step`() = runTest {
        val (transport, _, runner) = fixture()
        val script = SequenceScripts.ROUTE_WALK

        runner.start(backgroundScope, script, dwellMs = 50L, loop = true)
        advanceTimeBy(50L * script.steps.size + 60)

        assertTrue("should have wrapped around", transport.received.size > script.steps.size)
        assertEquals(
            TbtFrame(encoder.encode(script.steps.first().nav)),
            transport.received[script.steps.size],
        )
        runner.stop()
    }

    @Test
    fun `stopping halts advancement`() = runTest {
        val (transport, _, runner) = fixture()

        runner.start(backgroundScope, SequenceScripts.SYMBOL_SWEEP, dwellMs = 50L)
        advanceTimeBy(120)
        val sentSoFar = transport.received.size

        runner.stop()
        advanceTimeBy(1_000)

        assertEquals(sentSoFar, transport.received.size)
        assertNull(runner.progress.value)
    }

    @Test
    fun `starting a second script replaces the first`() = runTest {
        val (transport, _, runner) = fixture()

        runner.start(backgroundScope, SequenceScripts.SYMBOL_SWEEP, dwellMs = 50L)
        advanceTimeBy(60)
        transport.clearReceived()

        runner.start(backgroundScope, SequenceScripts.ROUTE_WALK, dwellMs = 50L)
        runCurrent()

        assertEquals(SequenceScripts.ROUTE_WALK.id, runner.progress.value?.script?.id)
        assertEquals(
            TbtFrame(encoder.encode(SequenceScripts.ROUTE_WALK.steps.first().nav)),
            transport.received.first(),
        )
        runner.stop()
    }

    /**
     * The runner only chooses what to show; the scheduler keeps re-asserting it. Without
     * that split a slow script would let the cluster go stale between steps.
     */
    @Test
    fun `the heartbeat keeps re-sending the current step between advances`() = runTest {
        val (transport, scheduler, runner) = fixture()
        val job = scheduler.start(backgroundScope)

        runner.start(backgroundScope, SequenceScripts.ROUTE_WALK, dwellMs = 2_000L)
        advanceTimeBy(1_800)

        assertTrue(
            "expected repeated frames within one dwell, got ${transport.received.size}",
            transport.received.size > 1,
        )
        assertTrue("all should be the same step", transport.received.distinct().size == 1)

        runner.stop()
        job.cancel()
    }
}
