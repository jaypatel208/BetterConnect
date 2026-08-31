package dev.jay.betterconnect.feature.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.data.GuidanceController
import dev.jay.betterconnect.core.domain.LocationFixSource
import dev.jay.betterconnect.core.domain.RoutesRepository
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.LatLng
import dev.jay.betterconnect.core.model.LocationFix
import dev.jay.betterconnect.core.model.RoutePlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NavigationUiState(
    val currentLocation: LatLng? = null,
    val destination: LatLng? = null,
    val routePlan: RoutePlan? = null,
    val fetchingRoute: Boolean = false,
    val routeError: String? = null,
    val navigating: Boolean = false,
    val connection: ConnectionState = ConnectionState.Idle,
) {
    /**
     * The map, destination picking and route preview all work with no cluster connection at
     * all - this screen must be reachable and useful on its own (the whole point of splitting
     * Connect and Navigate into separate tabs). Only actually starting guidance needs a live
     * link, since that is what sends frames to the cluster.
     */
    val connected: Boolean get() = connection is ConnectionState.Ready
    val canStart: Boolean get() = routePlan != null && !navigating && connected

    /** Google requires this warning to be shown wherever a two-wheeler route is displayed. */
    val showTwoWheelerWarning: Boolean get() = routePlan != null
}

private data class RouteFields(
    val currentLocation: LatLng?,
    val destination: LatLng?,
    val routePlan: RoutePlan?,
    val fetchingRoute: Boolean,
    val routeError: String?,
)

sealed interface NavigationAction {
    data class SetDestination(val location: LatLng) : NavigationAction
    data object ClearDestination : NavigationAction
    data object StartNavigation : NavigationAction
    data object StopNavigation : NavigationAction
}

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val locationFixSource: LocationFixSource,
    private val routesRepository: RoutesRepository,
    private val guidanceController: GuidanceController,
    private val clusterController: ClusterController,
) : ViewModel() {

    private val currentLocation = MutableStateFlow<LatLng?>(null)
    private val destination = MutableStateFlow<LatLng?>(null)
    private val routePlan = MutableStateFlow<RoutePlan?>(null)
    private val fetchingRoute = MutableStateFlow(false)
    private val routeError = MutableStateFlow<String?>(null)

    init {
        locationFixSource.fixes()
            .onEach { fix: LocationFix -> currentLocation.value = fix.position }
            .launchIn(viewModelScope)
    }

    private val routeFields: Flow<RouteFields> = combine(
        currentLocation,
        destination,
        routePlan,
        fetchingRoute,
        routeError,
    ) { loc, dest, plan, fetching, error -> RouteFields(loc, dest, plan, fetching, error) }

    val uiState: StateFlow<NavigationUiState> = combine(
        routeFields,
        guidanceController.activePlan,
        clusterController.state,
    ) { fields, activePlan, connection ->
        NavigationUiState(
            currentLocation = fields.currentLocation,
            destination = fields.destination,
            routePlan = fields.routePlan,
            fetchingRoute = fields.fetchingRoute,
            routeError = fields.routeError,
            // The guidance loop runs at service lifetime (GuidanceController), not tied to
            // this ViewModel - closing the nav screen must not stop it, and its own state
            // (including auto-stop on arrival) is the single source of truth for "navigating".
            navigating = activePlan != null,
            connection = connection,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavigationUiState())

    fun onAction(action: NavigationAction) {
        when (action) {
            is NavigationAction.SetDestination -> {
                destination.value = action.location
                routePlan.value = null
                routeError.value = null
                fetchRoute(action.location)
            }

            NavigationAction.ClearDestination -> {
                destination.value = null
                routePlan.value = null
                routeError.value = null
            }

            NavigationAction.StartNavigation -> {
                val plan = routePlan.value
                val dest = destination.value
                if (plan != null && dest != null) guidanceController.start(plan, dest)
            }

            NavigationAction.StopNavigation -> guidanceController.stop()
        }
    }

    private fun fetchRoute(destinationLocation: LatLng) {
        val origin = currentLocation.value ?: run {
            routeError.value = "Waiting for a location fix - try again in a moment."
            return
        }
        fetchingRoute.value = true
        viewModelScope.launch {
            routesRepository.computeRoute(origin, destinationLocation)
                .onSuccess { plan ->
                    routePlan.value = plan
                    routeError.value = null
                }
                .onFailure { error ->
                    routePlan.value = null
                    routeError.value = error.message ?: "Could not fetch a route."
                }
            fetchingRoute.value = false
        }
    }
}
