package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.model.GpsStatus
import dev.jay.betterconnect.core.model.GuidanceState
import dev.jay.betterconnect.core.model.LatLng
import dev.jay.betterconnect.core.model.LocationFix
import dev.jay.betterconnect.core.model.NavState
import dev.jay.betterconnect.core.model.RoutePlan
import dev.jay.betterconnect.core.model.Symbol
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tracks position along a [RoutePlan] and turns each [LocationFix] into cluster-ready
 * [NavState]. Pure and JVM-only - the whole point of `IMPLEMENTATION.md` §4 computing
 * guidance ourselves is that this maths is testable without a device or an API key.
 */
object GuidanceEngine {

    /** Beyond this distance from the active step's polyline, a fix counts as off-route. */
    const val OFF_ROUTE_DISTANCE_M = 50.0

    /** Close enough to a step's end to consider it passed and advance to the next one. */
    const val STEP_ADVANCE_RADIUS_M = 20.0

    /** Close enough to the final step's end to consider the trip arrived. */
    const val ARRIVAL_RADIUS_M = 15.0

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** ~50 km/h - a placeholder pace for ETA until real speed is available in [advance]. */
    private const val FALLBACK_SPEED_MPS = 13.9

    fun advance(plan: RoutePlan, fix: LocationFix, previous: GuidanceState): GuidanceState {
        val steps = plan.steps
        if (steps.isEmpty()) return previous.copy(arrived = true, offRouteStreak = 0)

        var stepIndex = previous.activeStepIndex.coerceIn(0, steps.lastIndex)
        var distanceToStepEnd = haversine(fix.position, steps[stepIndex].endLocation)

        while (stepIndex < steps.lastIndex && distanceToStepEnd <= STEP_ADVANCE_RADIUS_M) {
            stepIndex++
            distanceToStepEnd = haversine(fix.position, steps[stepIndex].endLocation)
        }

        val arrived = stepIndex == steps.lastIndex && distanceToStepEnd <= ARRIVAL_RADIUS_M

        val activeStepPoints = PolylineCodec.decode(steps[stepIndex].polyline)
        val distanceToPolyline = distanceToNearestPoint(fix.position, activeStepPoints)
        val offRoute = !arrived && distanceToPolyline > OFF_ROUTE_DISTANCE_M
        val offRouteStreak = if (offRoute) previous.offRouteStreak + 1 else 0

        val remainingInLaterSteps = steps.drop(stepIndex + 1).sumOf { it.distanceM }
        val distanceRemainingM = (distanceToStepEnd.toInt() + remainingInLaterSteps)

        return GuidanceState(
            activeStepIndex = stepIndex,
            distanceToTurnM = distanceToStepEnd.toInt(),
            distanceRemainingM = distanceRemainingM,
            offRouteStreak = offRouteStreak,
            arrived = arrived,
        )
    }

    /**
     * Builds the frame to send to the cluster. [previousSymbol] is held whenever the active
     * step's manoeuvre has no confident mapping - never a stale [Symbol.ROTARY] default.
     *
     * GPS must never read [GpsStatus.OFF] while navigating (it gates the whole display,
     * `MANEUVERS.md` §6) - pass [GpsStatus.SEARCHING] instead when the fix is stale or
     * unavailable, never [GpsStatus.OFF].
     */
    fun buildNavState(
        plan: RoutePlan,
        state: GuidanceState,
        previousSymbol: Symbol,
        gpsStatus: GpsStatus,
        speedMps: Float?,
    ): NavState {
        if (state.arrived) {
            return NavState(
                symbol = Symbol.ARRIVE,
                distanceToTurnM = 0,
                distanceLeftM = 0,
                etaSeconds = 0,
                text = "ARRIVED",
                gpsStatus = gpsStatus,
            )
        }

        val steps = plan.steps
        val activeStep = steps.getOrNull(state.activeStepIndex)
        val mapped = activeStep?.let { ManeuverMapper.map(it.maneuver) }
        val symbol = mapped?.symbol ?: previousSymbol
        val caption = mapped?.caption.orEmpty()

        val pace = speedMps?.takeIf { it > 1f } ?: FALLBACK_SPEED_MPS.toFloat()
        val etaSeconds = (state.distanceRemainingM / pace).toLong().coerceAtLeast(0)

        return NavState(
            symbol = symbol,
            distanceToTurnM = state.distanceToTurnM,
            distanceLeftM = state.distanceRemainingM,
            etaSeconds = etaSeconds,
            text = caption,
            // Routes API returns no roundabout exit number (unlike the Navigation SDK) -
            // deliberately 0, not a guess. See this plan's Routes API notes.
            roundaboutExit = 0,
            gpsStatus = gpsStatus,
        )
    }

    private fun distanceToNearestPoint(point: LatLng, polyline: List<LatLng>): Double {
        if (polyline.isEmpty()) return Double.MAX_VALUE
        return polyline.minOf { haversine(point, it) }
    }

    private fun haversine(a: LatLng, b: LatLng): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }
}
