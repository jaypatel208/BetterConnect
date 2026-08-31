package dev.jay.betterconnect.feature.navigation

import app.cash.turbine.test
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.data.GuidanceController
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
import dev.jay.betterconnect.core.testing.FakeClusterTransport
import dev.jay.betterconnect.core.testing.FakeDeviceScanner
import dev.jay.betterconnect.core.testing.FakeLocationFixSource
import dev.jay.betterconnect.core.testing.FakeRoutesRepository
import dev.jay.betterconnect.core.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The map, destination search and route preview must all work with no cluster connection at
 * all - that is the entire point of Connect and Navigate being separate tabs rather than
 * Navigate being gated behind a completed connection. Only `canStart` (which drives guidance
 * actually reaching the cluster) should care about `ConnectionState`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val origin = LatLng(0.0, 0.0)
    private val destination = LatLng(1.0, 1.0)

    private fun samplePlan() = RoutePlan(
        legs = listOf(
            RouteLeg(
                steps = listOf(
                    RouteStep(
                        maneuver = "TURN_LEFT",
                        polyline = PolylineCodec.encode(listOf(origin, destination)),
                        distanceM = 1000,
                        durationS = 60,
                        instruction = "TURN_LEFT",
                        startLocation = origin,
                        endLocation = destination,
                    ),
                ),
                distanceM = 1000,
                durationS = 60,
            ),
        ),
        overviewPolyline = PolylineCodec.encode(listOf(origin, destination)),
        distanceM = 1000,
        durationS = 60,
    )

    private fun sampleFix() = LocationFix(
        position = origin,
        bearingDeg = 0f,
        speedMps = 10f,
        accuracyM = 5f,
        timestampMs = 0L,
    )

    private class Harness(scope: CoroutineScope, connection: ConnectionState) {
        val transport = FakeClusterTransport(connection)
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
    fun `a route can be fetched with no cluster connection at all`() = runTest {
        val h = Harness(backgroundScope, ConnectionState.Idle)
        h.routes.setResult(samplePlan())
        val vm = NavigationViewModel(h.locationSource, h.routes, h.guidance, h.controller)
        runCurrent() // let the init block's fixes() collector subscribe before emitting

        h.locationSource.emit(sampleFix())
        runCurrent()
        vm.onAction(NavigationAction.SetDestination(destination))
        runCurrent()

        vm.uiState.test {
            awaitItem()
            runCurrent()
            val state = expectMostRecentItem()
            assertTrue("a route plan should be reachable without any connection", state.routePlan != null)
            assertFalse(state.connected)
        }
    }

    @Test
    fun `canStart is false with a route plan but no cluster connection`() = runTest {
        val h = Harness(backgroundScope, ConnectionState.Idle)
        h.routes.setResult(samplePlan())
        val vm = NavigationViewModel(h.locationSource, h.routes, h.guidance, h.controller)
        runCurrent() // let the init block's fixes() collector subscribe before emitting

        h.locationSource.emit(sampleFix())
        runCurrent()
        vm.onAction(NavigationAction.SetDestination(destination))
        runCurrent()

        vm.uiState.test {
            awaitItem()
            runCurrent()
            assertFalse(expectMostRecentItem().canStart)
        }
    }

    @Test
    fun `canStart is true once connected and a route plan exists`() = runTest {
        val h = Harness(backgroundScope, ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
        h.routes.setResult(samplePlan())
        val vm = NavigationViewModel(h.locationSource, h.routes, h.guidance, h.controller)
        runCurrent() // let the init block's fixes() collector subscribe before emitting

        h.locationSource.emit(sampleFix())
        runCurrent()
        vm.onAction(NavigationAction.SetDestination(destination))
        runCurrent()

        vm.uiState.test {
            awaitItem()
            runCurrent()
            assertTrue(expectMostRecentItem().canStart)
        }
    }
}
