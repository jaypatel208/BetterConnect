package dev.jay.betterconnect.core.data

import dev.jay.betterconnect.core.domain.GuidanceEngine
import dev.jay.betterconnect.core.domain.LocationFixSource
import dev.jay.betterconnect.core.domain.LogLevel
import dev.jay.betterconnect.core.domain.ManeuverMapper
import dev.jay.betterconnect.core.domain.RoutesRepository
import dev.jay.betterconnect.core.model.GpsStatus
import dev.jay.betterconnect.core.model.GuidanceState
import dev.jay.betterconnect.core.model.LatLng
import dev.jay.betterconnect.core.model.LocationFix
import dev.jay.betterconnect.core.model.NavState
import dev.jay.betterconnect.core.model.RoutePlan
import dev.jay.betterconnect.core.model.Symbol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the guidance loop: consumes [DeviceLocationSource], advances [GuidanceEngine], and
 * sends the resulting frame through [ClusterController]. Lives at singleton (service)
 * lifetime, not a ViewModel's - starting it survives the nav screen being closed, and
 * `ClusterService` is what actually keeps the process alive while it runs.
 *
 * Off-route re-fetches are both debounced and capped: two-wheeler routing is a billed
 * Enterprise-SKU request, so a chatty implementation is a real bill, not just bad UX.
 */
@Singleton
class GuidanceController @Inject constructor(
    private val locationFixSource: LocationFixSource,
    private val controller: ClusterController,
    private val routesRepository: RoutesRepository,
    private val scope: CoroutineScope,
) {
    private val _activePlan = MutableStateFlow<RoutePlan?>(null)
    val activePlan: StateFlow<RoutePlan?> = _activePlan.asStateFlow()

    private val _guidanceState = MutableStateFlow<GuidanceState?>(null)
    val guidanceState: StateFlow<GuidanceState?> = _guidanceState.asStateFlow()

    private val _lastFix = MutableStateFlow<LocationFix?>(null)
    val lastFix: StateFlow<LocationFix?> = _lastFix.asStateFlow()

    /** The exact frame just sent to the cluster - the navigation notification mirrors this. */
    private val _lastNavState = MutableStateFlow<NavState?>(null)
    val lastNavState: StateFlow<NavState?> = _lastNavState.asStateFlow()

    private var destination: LatLng? = null
    private var previousSymbol = Symbol.STRAIGHT
    private var locationJob: Job? = null
    private var rerouteJob: Job? = null
    private var rerouteCount = 0
    private var lastRerouteAtMs = 0L
    private var lastWarnedManeuver: String? = null

    fun start(plan: RoutePlan, destination: LatLng) {
        stop()
        this.destination = destination
        rerouteCount = 0
        previousSymbol = Symbol.STRAIGHT
        _activePlan.value = plan
        _guidanceState.value = GuidanceState()
        controller.log.log(LogLevel.INFO, TAG, "navigation started, ${plan.distanceM} m", now())
        locationJob = locationFixSource.fixes().onEach(::tick).launchIn(scope)
    }

    fun stop() {
        val wasActive = _activePlan.value != null
        locationJob?.cancel()
        locationJob = null
        rerouteJob?.cancel()
        rerouteJob = null
        destination = null
        _activePlan.value = null
        _guidanceState.value = null
        _lastNavState.value = null
        controller.clearCluster()
        if (wasActive) controller.log.log(LogLevel.INFO, TAG, "navigation stopped", now())
    }

    private fun tick(fix: LocationFix) {
        val plan = _activePlan.value ?: return
        _lastFix.value = fix

        val previous = _guidanceState.value ?: GuidanceState()
        val next = GuidanceEngine.advance(plan, fix, previous)
        _guidanceState.value = next

        // GPS must never read OFF while navigating - it gates the whole cluster display.
        val gpsStatus = if ((fix.accuracyM ?: Float.MAX_VALUE) <= MAX_USEFUL_ACCURACY_M) {
            GpsStatus.ACTIVE
        } else {
            GpsStatus.SEARCHING
        }
        val activeManeuver = plan.steps.getOrNull(next.activeStepIndex)?.maneuver
        if (activeManeuver != null && lastWarnedManeuver != activeManeuver) {
            val mapped = ManeuverMapper.map(activeManeuver)
            if (mapped.symbol == null) {
                // An unmapped manoeuvre is a bug to fix, not an arrow to guess - IMPLEMENTATION.md §4.
                lastWarnedManeuver = activeManeuver
                controller.log.log(LogLevel.WARN, TAG, "unmapped maneuver: $activeManeuver", now())
            }
        }

        val nav = GuidanceEngine.buildNavState(plan, next, previousSymbol, gpsStatus, fix.speedMps)
        previousSymbol = nav.symbol
        _lastNavState.value = nav
        controller.send(nav)

        when {
            next.arrived -> {
                controller.log.log(LogLevel.INFO, TAG, "arrived", now())
                stop()
            }
            next.needsReroute -> maybeReroute(fix.position)
        }
    }

    private fun maybeReroute(origin: LatLng) {
        val dest = destination ?: return
        if (rerouteJob?.isActive == true) return
        if (rerouteCount >= MAX_REROUTES_PER_TRIP) return
        val now = System.currentTimeMillis()
        if (now - lastRerouteAtMs < MIN_REROUTE_INTERVAL_MS) return

        lastRerouteAtMs = now
        rerouteCount++
        controller.log.log(LogLevel.WARN, TAG, "off-route, requesting re-fetch #$rerouteCount", now())
        rerouteJob = scope.launch {
            routesRepository.computeRoute(origin, dest)
                .onSuccess { plan ->
                    _activePlan.value = plan
                    _guidanceState.value = GuidanceState()
                    controller.log.log(LogLevel.INFO, TAG, "re-fetch succeeded", now())
                }
                .onFailure { error ->
                    controller.log.log(LogLevel.ERROR, TAG, "re-fetch failed: ${error.message}", now())
                }
        }
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        private const val TAG = "GUIDANCE"
        private const val MAX_USEFUL_ACCURACY_M = 50f

        /** Floor between re-fetches, so a pathological off-route loop cannot burn quota. */
        private const val MIN_REROUTE_INTERVAL_MS = 30_000L

        /** Cap per navigation session, independent of the interval floor above. */
        private const val MAX_REROUTES_PER_TRIP = 10
    }
}
