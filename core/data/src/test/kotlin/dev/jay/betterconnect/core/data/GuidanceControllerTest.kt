package dev.jay.betterconnect.core.data

import dev.jay.betterconnect.core.domain.DiagLog
import dev.jay.betterconnect.core.domain.PolylineCodec
import dev.jay.betterconnect.core.domain.SequenceRunner
import dev.jay.betterconnect.core.link.ControlPump
import dev.jay.betterconnect.core.link.GeneralScheduler
import dev.jay.betterconnect.core.link.WriteScheduler
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.LatLng
import dev.jay.betterconnect.core.model.LocationFix
import dev.jay.betterconnect.core.model.RouteLeg
import dev.jay.betterconnect.core.model.RoutePlan
import dev.jay.betterconnect.core.model.RouteStep
import dev.jay.betterconnect.core.protocol.TbtEncoder
import dev.jay.betterconnect.core.protocol.TbtFrame
import dev.jay.betterconnect.core.testing.FakeClusterTransport
import dev.jay.betterconnect.core.testing.FakeDeviceScanner
import dev.jay.betterconnect.core.testing.FakeLocationFixSource
import dev.jay.betterconnect.core.testing.FakeRoutesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `runCurrent()`, not `advanceUntilIdle()`, after every [FakeLocationFixSource.emit] here.
 *
 * `advanceUntilIdle()` does not reliably resume a `backgroundScope`-launched collector's
 * suspension when it is woken by an external, non-`delay` trigger like `tryEmit` - confirmed
 * empirically against a bare `MutableSharedFlow` in this exact test setup. The delay-driven
 * schedulers elsewhere in this codebase (`WriteScheduler`, `ControlPump`, `GeneralScheduler`)
 * never hit this because their collectors wake up via `delay()`, which `advanceUntilIdle()`
 * does drive correctly; a push-based `Flow` needs `runCurrent()` instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceControllerTest {

    private val start = LatLng(0.0, 0.0)
    private val mid = LatLng(0.0, 0.01)
    private val end = LatLng(0.0, 0.02)

    private fun step(from: LatLng, to: LatLng, maneuver: String) = RouteStep(
        maneuver = maneuver,
        polyline = PolylineCodec.encode(listOf(from, to)),
        distanceM = 1112,
        durationS = 80,
        instruction = maneuver,
        startLocation = from,
        endLocation = to,
    )

    private fun plan() = RoutePlan(
        legs = listOf(
            RouteLeg(
                steps = listOf(step(start, mid, "TURN_LEFT"), step(mid, end, "STRAIGHT")),
                distanceM = 2224,
                durationS = 160,
            ),
        ),
        overviewPolyline = PolylineCodec.encode(listOf(start, mid, end)),
        distanceM = 2224,
        durationS = 160,
    )

    private fun fix(position: LatLng) = LocationFix(
        position = position,
        bearingDeg = 0f,
        speedMps = 10f,
        accuracyM = 5f,
        timestampMs = 0L,
    )

    private class Harness(scope: CoroutineScope) {
        val transport = FakeClusterTransport(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
        val locationSource = FakeLocationFixSource()
        val routes = FakeRoutesRepository()
        private val encoder = TbtEncoder()
        private val scheduler = WriteScheduler(transport)
        private val controlPump = ControlPump(transport)
        private val generalScheduler = GeneralScheduler(transport, controlPump.acks)
        val controller = ClusterController(
            transport = transport,
            scheduler = scheduler,
            controlPump = controlPump,
            generalScheduler = generalScheduler,
            runner = SequenceRunner(scheduler, encoder),
            encoder = encoder,
            scanner = FakeDeviceScanner(),
            log = DiagLog(),
            scope = scope,
        )
        val guidance = GuidanceController(locationSource, controller, routes, scope)
    }

    @Test
    fun `a fix sends a decoded frame through the cluster controller`() = runTest {
        val h = Harness(backgroundScope)
        h.guidance.start(plan(), end)
        runCurrent()

        h.locationSource.emit(fix(start))
        runCurrent()

        val decoded = h.transport.lastDecoded
        assertEquals('I', decoded?.symbolChar) // TURN_LEFT for step 0
    }

    @Test
    fun `arriving stops the session and clears the cluster`() = runTest {
        val h = Harness(backgroundScope)
        h.guidance.start(plan(), end)
        runCurrent()

        // advance() moves one step at a time by distance-to-current-step-end, so reaching
        // the final step first requires passing through the midpoint.
        h.locationSource.emit(fix(mid))
        runCurrent()
        h.locationSource.emit(fix(end))
        runCurrent()

        assertNull(h.guidance.activePlan.value)
        assertNull(h.guidance.guidanceState.value)
    }

    @Test
    fun `stop clears the cluster display`() = runTest {
        val h = Harness(backgroundScope)
        h.guidance.start(plan(), end)
        runCurrent()
        h.locationSource.emit(fix(start))
        runCurrent()

        h.guidance.stop()
        runCurrent()

        assertEquals(TbtFrame.endNavigation(), h.transport.received.last())
    }

    @Test
    fun `sustained off-route fixes trigger exactly one reroute request`() = runTest {
        val h = Harness(backgroundScope)
        h.routes.setResult(plan())
        h.guidance.start(plan(), end)
        runCurrent()

        val farOff = fix(LatLng(0.01, 0.0))
        repeat(6) {
            h.locationSource.emit(farOff)
            runCurrent()
        }

        assertEquals(1, h.routes.requestCount)
    }

    @Test
    fun `a single wobble does not trigger a reroute`() = runTest {
        val h = Harness(backgroundScope)
        h.guidance.start(plan(), end)
        runCurrent()

        h.locationSource.emit(fix(LatLng(0.01, 0.0)))
        runCurrent()

        assertEquals(0, h.routes.requestCount)
    }

    @Test
    fun `starting again resets any leftover state from a previous session`() = runTest {
        val h = Harness(backgroundScope)
        h.guidance.start(plan(), end)
        runCurrent()
        h.locationSource.emit(fix(mid))
        runCurrent()
        assertTrue(h.guidance.guidanceState.value?.activeStepIndex == 1)

        h.guidance.start(plan(), end)
        runCurrent()

        assertEquals(0, h.guidance.guidanceState.value?.activeStepIndex)
    }
}
