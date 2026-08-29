package dev.jay.betterconnect.core.model

/** A point on Earth. Decimal degrees, WGS84 - the same datum GPS and Routes API both use. */
data class LatLng(val lat: Double, val lng: Double)

/**
 * One step of a route leg, roughly "the instructions from one manoeuvre to the next."
 *
 * [maneuver] is the raw Routes API `Maneuver` enum string (e.g. `TURN_LEFT`) - kept as the
 * wire value rather than mapped eagerly, so [dev.jay.betterconnect.core.domain.ManeuverMapper]
 * stays the single place that translates it and can be swept exhaustively in a test.
 */
data class RouteStep(
    val maneuver: String,
    val polyline: String,
    val distanceM: Int,
    val durationS: Int,
    /** Human-readable instruction text. Phone UI only - never sent to the cluster verbatim. */
    val instruction: String,
    val startLocation: LatLng,
    val endLocation: LatLng,
)

data class RouteLeg(val steps: List<RouteStep>, val distanceM: Int, val durationS: Int)

/**
 * A computed route, ready to navigate. [overviewPolyline] is the whole-route encoded
 * polyline used to draw the map; [steps] is what [dev.jay.betterconnect.core.domain.GuidanceEngine]
 * walks through as the rider moves.
 */
data class RoutePlan(
    val legs: List<RouteLeg>,
    val overviewPolyline: String,
    val distanceM: Int,
    val durationS: Int,
    /** Opaque token some Routes API responses return for a cheaper resumed request. */
    val routeToken: String? = null,
) {
    val steps: List<RouteStep> get() = legs.flatMap { it.steps }
}

/** One position update from the device's location sensor. */
data class LocationFix(
    val position: LatLng,
    val bearingDeg: Float?,
    val speedMps: Float?,
    val accuracyM: Float?,
    val timestampMs: Long,
)

/**
 * Where guidance is along [RoutePlan.steps], recomputed on every [LocationFix].
 *
 * [offRouteStreak] counts consecutive fixes that fell outside the snap tolerance - a
 * re-fetch only fires once this crosses a threshold, so a single GPS wobble at a flyover
 * or in a tunnel never costs a Routes API call. Two-wheeler routing is billed per request
 * (docs/DEVELOPMENT-NOTES.md region), so this is a cost control, not just a UX one.
 */
data class GuidanceState(
    val activeStepIndex: Int = 0,
    val distanceToTurnM: Int = 0,
    val distanceRemainingM: Int = 0,
    val offRouteStreak: Int = 0,
    val arrived: Boolean = false,
) {
    val needsReroute: Boolean get() = offRouteStreak >= OFF_ROUTE_THRESHOLD

    companion object {
        /** Consecutive off-route fixes required before asking for a re-fetch. */
        const val OFF_ROUTE_THRESHOLD = 5
    }
}
