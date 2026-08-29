package dev.jay.betterconnect.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import dev.jay.betterconnect.core.designsystem.component.ManeuverGlyph
import dev.jay.betterconnect.core.domain.PolylineCodec
import dev.jay.betterconnect.core.model.LatLng
import kotlinx.serialization.Serializable
import com.google.android.gms.maps.model.LatLng as GmsLatLng

@Serializable
data object Navigation : NavKey

fun EntryProviderScope<NavKey>.navigationEntry(onClose: () -> Unit) {
    entry<Navigation> { NavigationRoute(onClose = onClose) }
}

@Composable
fun NavigationRoute(onClose: () -> Unit, viewModel: NavigationViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NavigationScreen(state = state, onAction = viewModel::onAction, onClose = onClose)
}

@Composable
fun NavigationScreen(
    state: NavigationUiState,
    onAction: (NavigationAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState()
    var followingCamera by remember { mutableStateOf(true) }

    state.currentLocation?.let { location ->
        if (followingCamera && !cameraPositionState.isMoving) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(location.toGms(), FOLLOW_ZOOM)
        }
    }

    Box(modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = true),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
            onMapClick = { latLng -> onAction(NavigationAction.SetDestination(latLng.toModel())) },
        ) {
            state.destination?.let { destination ->
                Marker(state = rememberUpdatedMarkerState(position = destination.toGms()))
            }
            state.routePlan?.let { plan ->
                val points = PolylineCodec.decode(plan.overviewPolyline).map { it.toGms() }
                if (points.isNotEmpty()) {
                    Polyline(points = points, color = MaterialTheme.colorScheme.primary, width = 12f)
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(8.dp),
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) {
                Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.padding(8.dp))
            }
        }

        if (!followingCamera) {
            FloatingActionButton(
                onClick = {
                    followingCamera = true
                    state.currentLocation?.let {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(it.toGms(), FOLLOW_ZOOM)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = 96.dp),
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Recenter")
            }
        }

        RoutePreviewCard(
            state = state,
            onAction = onAction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        )
    }

    // The gesture that starts a camera pan is reported by cameraPositionState; stop
    // following once the rider (or the map fling) moves it away from the puck.
    if (cameraPositionState.isMoving && followingCamera) {
        followingCamera = false
    }
}

@Composable
private fun RoutePreviewCard(
    state: NavigationUiState,
    onAction: (NavigationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = state.routePlan
    val error = state.routeError

    if (plan != null) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${plan.distanceM / 1000} km · ${plan.durationS / 60} min",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ManeuverGlyph(symbolCode = 'I'.code, size = 32.dp)
                }
                if (state.showTwoWheelerWarning) {
                    Text(
                        "Two-wheeler routing is in beta. Roads and restrictions may not fully " +
                            "reflect motorcycle rules - ride your own judgement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Button(
                    onClick = { onAction(NavigationAction.StartNavigation) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    enabled = state.canStart,
                ) { Text(if (state.navigating) "Navigating" else "Start") }
            }
        }
    } else if (error != null) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Text(
                error,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

private const val FOLLOW_ZOOM = 16f

private fun LatLng.toGms(): GmsLatLng = GmsLatLng(lat, lng)
private fun GmsLatLng.toModel(): LatLng = LatLng(latitude, longitude)
