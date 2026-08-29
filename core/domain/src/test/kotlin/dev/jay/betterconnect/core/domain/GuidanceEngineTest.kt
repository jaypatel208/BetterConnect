package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.model.GpsStatus
import dev.jay.betterconnect.core.model.GuidanceState
import dev.jay.betterconnect.core.model.LatLng
import dev.jay.betterconnect.core.model.LocationFix
import dev.jay.betterconnect.core.model.RouteLeg
import dev.jay.betterconnect.core.model.RoutePlan
import dev.jay.betterconnect.core.model.RouteStep
import dev.jay.betterconnect.core.model.Symbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A synthetic two-step route running north along the same meridian, so distances are easy
 * to reason about by hand: 0,0 -> 0,0.01 (~1112 m) -> 0,0.02 (~1112 m more).
 */
class GuidanceEngineTest {

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

    @Test
    fun `distance to turn shrinks as the rider approaches the step end`() {
        val plan = plan()
        val far = GuidanceEngine.advance(plan, fix(start), GuidanceState())
        val near = GuidanceEngine.advance(plan, fix(LatLng(0.0, 0.009)), GuidanceState())

        assertTrue(far.distanceToTurnM > near.distanceToTurnM)
    }

    @Test
    fun `crossing the step-advance radius moves to the next step`() {
        val plan = plan()
        val atMid = GuidanceEngine.advance(plan, fix(mid), GuidanceState(activeStepIndex = 0))
        assertEquals(1, atMid.activeStepIndex)
    }

    @Test
    fun `arriving within the arrival radius of the final step is reported as arrived`() {
        val plan = plan()
        val state = GuidanceEngine.advance(plan, fix(end), GuidanceState(activeStepIndex = 1))
        assertTrue(state.arrived)
    }

    @Test
    fun `distance remaining sums the active step plus every step after it`() {
        val plan = plan()
        // Right at the start of step 0: ~1113 m left in step 0 (haversine), plus 1112 m
        // nominal for step 1 (whole later steps use their declared distanceM, not geometry).
        val state = GuidanceEngine.advance(plan, fix(start), GuidanceState(activeStepIndex = 0))
        assertTrue(
            "expected ~2225 m, was ${state.distanceRemainingM}",
            kotlin.math.abs(2225 - state.distanceRemainingM) <= 5,
        )
    }

    @Test
    fun `a fix far from the polyline increments the off-route streak`() {
        val plan = plan()
        val farOff = fix(LatLng(0.01, 0.0)) // ~1111 m perpendicular from the route
        val state = GuidanceEngine.advance(plan, farOff, GuidanceState(offRouteStreak = 2))
        assertEquals(3, state.offRouteStreak)
    }

    @Test
    fun `a fix back on the polyline resets the off-route streak`() {
        val plan = plan()
        val state = GuidanceEngine.advance(plan, fix(start), GuidanceState(offRouteStreak = 4))
        assertEquals(0, state.offRouteStreak)
    }

    @Test
    fun `reroute is requested only after the off-route threshold, not on the first bad fix`() {
        val plan = plan()
        var state = GuidanceState()
        val farOff = fix(LatLng(0.01, 0.0))
        repeat(GuidanceState.OFF_ROUTE_THRESHOLD - 1) {
            state = GuidanceEngine.advance(plan, farOff, state)
        }
        assertFalse("a single wobble must not trigger a paid re-fetch", state.needsReroute)

        state = GuidanceEngine.advance(plan, farOff, state)
        assertTrue(state.needsReroute)
    }

    @Test
    fun `an empty plan is reported as arrived rather than crashing`() {
        val empty = RoutePlan(legs = emptyList(), overviewPolyline = "", distanceM = 0, durationS = 0)
        val state = GuidanceEngine.advance(empty, fix(start), GuidanceState())
        assertTrue(state.arrived)
    }

    @Test
    fun `buildNavState maps the active step's maneuver`() {
        val plan = plan()
        val state = GuidanceState(activeStepIndex = 0, distanceToTurnM = 500, distanceRemainingM = 1600)
        val nav = GuidanceEngine.buildNavState(plan, state, Symbol.STRAIGHT, GpsStatus.ACTIVE, speedMps = 10f)

        assertEquals(Symbol.LEFT, nav.symbol) // TURN_LEFT for step 0
        assertEquals("TURN LEFT", nav.text)
        assertEquals(500, nav.distanceToTurnM)
        assertEquals(1600, nav.distanceLeftM)
    }

    @Test
    fun `buildNavState holds the previous symbol for an unmapped maneuver`() {
        val plan = plan().let { p ->
            p.copy(
                legs = listOf(
                    p.legs[0].copy(
                        steps = listOf(p.legs[0].steps[0].copy(maneuver = "MANEUVER_UNSPECIFIED")),
                    ),
                ),
            )
        }
        val nav = GuidanceEngine.buildNavState(
            plan,
            GuidanceState(activeStepIndex = 0),
            previousSymbol = Symbol.RIGHT,
            gpsStatus = GpsStatus.ACTIVE,
            speedMps = 10f,
        )
        assertEquals(Symbol.RIGHT, nav.symbol)
    }

    @Test
    fun `buildNavState never clears GPS while navigating`() {
        val plan = plan()
        val nav = GuidanceEngine.buildNavState(
            plan,
            GuidanceState(activeStepIndex = 0),
            Symbol.STRAIGHT,
            GpsStatus.SEARCHING,
            speedMps = null,
        )
        assertEquals(GpsStatus.SEARCHING, nav.gpsStatus)
    }

    @Test
    fun `buildNavState reports arrival with the documented icon and caption`() {
        val plan = plan()
        val nav = GuidanceEngine.buildNavState(
            plan,
            GuidanceState(activeStepIndex = 1, arrived = true),
            Symbol.LEFT,
            GpsStatus.ACTIVE,
            speedMps = 0f,
        )
        assertEquals(Symbol.ARRIVE, nav.symbol)
        assertEquals("ARRIVED", nav.text)
        assertEquals(0, nav.distanceLeftM)
    }

    @Test
    fun `buildNavState never sends a roundabout exit number Routes API cannot provide`() {
        val plan = plan()
        val nav = GuidanceEngine.buildNavState(
            plan,
            GuidanceState(activeStepIndex = 0),
            Symbol.STRAIGHT,
            GpsStatus.ACTIVE,
            speedMps = 10f,
        )
        assertEquals(0, nav.roundaboutExit)
    }
}
