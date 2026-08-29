package dev.jay.betterconnect.core.data

import dev.jay.betterconnect.core.domain.DiagLog
import dev.jay.betterconnect.core.domain.LogLevel
import dev.jay.betterconnect.core.domain.SequenceRunner
import dev.jay.betterconnect.core.domain.SequenceScripts
import dev.jay.betterconnect.core.link.ControlPump
import dev.jay.betterconnect.core.link.GeneralScheduler
import dev.jay.betterconnect.core.link.SendMode
import dev.jay.betterconnect.core.link.WriteScheduler
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.NavState
import dev.jay.betterconnect.core.model.Symbol
import dev.jay.betterconnect.core.protocol.ClusterProtocol
import dev.jay.betterconnect.core.protocol.TbtEncoder
import dev.jay.betterconnect.core.protocol.TbtFrame
import dev.jay.betterconnect.core.testing.FakeClusterTransport
import dev.jay.betterconnect.core.testing.FakeDeviceScanner
import dev.jay.betterconnect.core.testing.TestClocks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End to end through the real controller, scheduler, encoder and sequence runner, with only
 * the radio replaced.
 *
 * The fake decodes every frame, so these assertions are about what the cluster would
 * actually have been told - not about the bytes, and not about the UI state that produced
 * them. This is the closest thing to a bike that runs on a laptop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClusterControllerTest {

    private class Fixture(scope: CoroutineScope) {
        val transport = FakeClusterTransport(ConnectionState.Idle)
        val scanner = FakeDeviceScanner()
        val encoder = TbtEncoder(clock = TestClocks.TEN_AM)
        val scheduler = WriteScheduler(transport)
        val controlPump = ControlPump(transport)
        val generalScheduler = GeneralScheduler(transport, controlPump.acks)
        val runner = SequenceRunner(scheduler, encoder)
        val log = DiagLog()
        val controller = ClusterController(
            transport = transport,
            scheduler = scheduler,
            controlPump = controlPump,
            generalScheduler = generalScheduler,
            runner = runner,
            encoder = encoder,
            scanner = scanner,
            log = log,
            scope = scope,
        )
    }

    /**
     * The controller keeps a collector alive for the lifetime of its scope, so it must be
     * given backgroundScope. Handing it the TestScope makes runTest wait forever for a
     * coroutine that is never meant to finish.
     */
    private fun TestScope.fixture() = Fixture(backgroundScope)

    @Test
    fun `sending a nav state puts exactly that instruction on the cluster`() = runTest {
        val f = fixture()
        f.transport.setState(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
        runCurrent()

        f.controller.send(
            NavState(
                symbol = Symbol.SHARP_LEFT,
                distanceToTurnM = 250,
                distanceLeftM = 4_800,
                etaSeconds = 600,
                text = "ASHRAM ROAD",
            ),
        )

        val decoded = f.transport.lastDecoded!!
        assertEquals('E', decoded.symbolChar)
        assertEquals(false, decoded.blinking)
        assertEquals(250, decoded.turn.whole)
        assertEquals(true, decoded.turn.isMetres)
        assertEquals("ASHRAM ROAD", decoded.text)
        assertTrue("no frame should fail its own checksum", f.transport.badFrames.isEmpty())
    }

    @Test
    fun `the blink threshold is applied end to end`() = runTest {
        val f = fixture()
        f.transport.setState(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
        runCurrent()

        f.controller.send(nav(distanceToTurnM = 101))
        assertEquals(false, f.transport.lastDecoded!!.blinking)

        f.controller.send(nav(distanceToTurnM = 100))
        assertEquals(true, f.transport.lastDecoded!!.blinking)
    }

    @Test
    fun `the heartbeat starts on Ready and stops when the link drops`() = runTest {
        val f = fixture()
        f.transport.setState(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
        runCurrent()

        f.controller.send(nav())
        advanceTimeBy(ClusterProtocol.TBT_PERIOD_MS * 3 + 1)
        val whileConnected = f.transport.received.size
        assertTrue("expected repeats, got $whileConnected", whileConnected >= 4)

        f.transport.setState(ConnectionState.Disconnected(FakeClusterTransport.ADDRESS, 19))
        runCurrent()
        advanceTimeBy(ClusterProtocol.TBT_PERIOD_MS * 5)

        assertEquals("nothing should be sent after a drop", whileConnected, f.transport.received.size)
    }

    @Test
    fun `nothing is written before the link is ready`() = runTest {
        val f = fixture()
        f.controller.send(nav())
        advanceTimeBy(ClusterProtocol.TBT_PERIOD_MS * 4)

        assertTrue(f.transport.received.isEmpty())
        assertTrue(f.controller.stats.value.notReady > 0)
    }

    @Test
    fun `clearing sends the all-zero frame`() = runTest {
        val f = fixture()
        f.transport.setState(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
        runCurrent()

        f.controller.send(nav())
        f.controller.clearCluster()

        assertEquals(TbtFrame.endNavigation(), f.transport.received.last())
        assertNull(f.controller.lastNav.value)
    }

    /**
     * The full route script, asserted frame by frame. If the encoder, the runner or the
     * scripts drift apart, this is where it shows.
     */
    @Test
    fun `the route walk script produces the exact expected instruction stream`() = runTest {
        val f = fixture()
        f.transport.setState(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
        runCurrent()
        f.controller.setSendMode(SendMode.ONE_SHOT)

        val script = SequenceScripts.ROUTE_WALK
        f.controller.startSequence(script, dwellMs = 100L, loop = false)
        advanceTimeBy(100L * script.steps.size + 20)

        assertEquals(script.steps.size, f.transport.decoded.size)
        script.steps.forEachIndexed { index, step ->
            val decoded = f.transport.decoded[index]
            assertEquals("step ${step.label} symbol", step.nav.symbolCode, decoded.symbolCode)
            assertEquals("step ${step.label} exit", step.nav.roundaboutExit, decoded.roundaboutExit)
        }
    }

    /** The reason the route script exists: comparing N and U side by side. */
    @Test
    fun `the route walk sends both roundabout families consecutively`() = runTest {
        val f = fixture()
        f.transport.setState(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
        runCurrent()
        f.controller.setSendMode(SendMode.ONE_SHOT)

        val script = SequenceScripts.ROUTE_WALK
        f.controller.startSequence(script, dwellMs = 50L, loop = false)
        advanceTimeBy(50L * script.steps.size + 20)

        val letters = f.transport.decoded.map { it.symbolChar }
        val u = letters.indexOf('U')
        assertTrue("U family missing", u >= 0)
        assertEquals("N should immediately follow U", 'N', letters[u + 1])
    }

    @Test
    fun `the symbol sweep sends all 26 letters in order`() = runTest {
        val f = fixture()
        f.transport.setState(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
        runCurrent()
        f.controller.setSendMode(SendMode.ONE_SHOT)

        val script = SequenceScripts.SYMBOL_SWEEP
        f.controller.startSequence(script, dwellMs = 20L, loop = false)
        advanceTimeBy(20L * script.steps.size + 20)

        assertEquals(('A'..'Z').toList(), f.transport.decoded.map { it.symbolChar })
    }

    @Test
    fun `a manual send interrupts a running sequence`() = runTest {
        val f = fixture()
        f.transport.setState(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
        runCurrent()
        f.controller.setSendMode(SendMode.ONE_SHOT)

        f.controller.startSequence(SequenceScripts.SYMBOL_SWEEP, dwellMs = 50L, loop = true)
        advanceTimeBy(120)
        f.controller.send(nav(symbol = Symbol.U_TURN))
        val afterManual = f.transport.received.size
        advanceTimeBy(500)

        assertEquals("sequence should have stopped", afterManual, f.transport.received.size)
        assertEquals('P', f.transport.lastDecoded!!.symbolChar)
        assertNull(f.controller.sequenceProgress.value)
    }

    @Test
    fun `state changes and frames are both recorded in the log`() = runTest {
        val f = fixture()
        f.transport.setState(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
        runCurrent()
        f.controller.send(nav())
        advanceUntilIdle()

        val entries = f.log.entries.value
        assertTrue(entries.any { it.level == LogLevel.INFO && it.message.contains("ready") })
        assertTrue(entries.any { it.level == LogLevel.FRAME && it.hex != null })
    }

    @Test
    fun `an unsupported cluster is logged as an error`() = runTest {
        val f = fixture()
        runCurrent()
        f.transport.setState(
            ConnectionState.Unsupported(
                FakeClusterTransport.ADDRESS,
                dev.jay.betterconnect.core.model.UnsupportedReason.CHARACTERISTIC_MISSING,
            ),
        )
        runCurrent()

        assertTrue(f.log.entries.value.any { it.level == LogLevel.ERROR })
    }

    private fun nav(symbol: Symbol = Symbol.LEFT, distanceToTurnM: Int = 500) = NavState(
        symbol = symbol,
        distanceToTurnM = distanceToTurnM,
        distanceLeftM = 6_000,
        etaSeconds = 900,
        text = "TEST",
    )
}
