package dev.jay.betterconnect.core.data

import dev.jay.betterconnect.core.domain.RoutesRepository
import dev.jay.betterconnect.core.model.LatLng
import dev.jay.betterconnect.core.model.RouteLeg
import dev.jay.betterconnect.core.model.RoutePlan
import dev.jay.betterconnect.core.model.RouteStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Calls Routes API `computeRoutes` for a two-wheeler route.
 *
 * Two-wheeler routing is the Enterprise SKU ($15/1,000 requests, 1,000/month free per the
 * pricing page checked 2026-08-29) - this class makes exactly one request per call, and it
 * is [dev.jay.betterconnect.core.domain.GuidanceEngine]'s job upstream to decide when a
 * re-fetch is actually warranted, never this repository's.
 *
 * The field mask is deliberately narrow: only what the guidance loop and the map screen
 * need, per Google's own guidance to avoid `routes.*` in production.
 */
@Singleton
class RoutesApiRepository @Inject constructor(
    private val client: OkHttpClient,
    @Named("mapsApiKey") private val apiKey: String,
) : RoutesRepository {

    override suspend fun computeRoute(origin: LatLng, destination: LatLng): Result<RoutePlan> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    NoApiKeyException("No Maps API key configured. See docs/SETUP.md."),
                )
            }
            runCatching {
                val body = Json.encodeToString(
                    ComputeRoutesRequest.serializer(),
                    ComputeRoutesRequest(
                        origin = Waypoint(Location(LatLngDto(origin.lat, origin.lng))),
                        destination = Waypoint(Location(LatLngDto(destination.lat, destination.lng))),
                    ),
                )
                val request = Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("X-Goog-Api-Key", apiKey)
                    .addHeader("X-Goog-FieldMask", FIELD_MASK)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("computeRoutes failed: HTTP ${response.code} ${response.message}")
                    }
                    parseComputeRoutesResponse(response.body.string())
                }
            }
        }

    companion object {
        private const val ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"

        private const val FIELD_MASK = "routes.duration,routes.distanceMeters," +
            "routes.polyline.encodedPolyline,routes.routeToken," +
            "routes.legs.distanceMeters,routes.legs.duration," +
            "routes.legs.steps.navigationInstruction.maneuver," +
            "routes.legs.steps.navigationInstruction.instructions," +
            "routes.legs.steps.polyline.encodedPolyline," +
            "routes.legs.steps.distanceMeters,routes.legs.steps.staticDuration," +
            "routes.legs.steps.startLocation,routes.legs.steps.endLocation"
    }
}

class NoApiKeyException(message: String) : Exception(message)

/** Extracted so a unit test can assert the JSON-to-[RoutePlan] mapping without a network call. */
internal fun parseComputeRoutesResponse(rawJson: String): RoutePlan {
    val json = Json { ignoreUnknownKeys = true }
    val parsed = json.decodeFromString(ComputeRoutesResponse.serializer(), rawJson)
    val route = parsed.routes.firstOrNull() ?: error("no route in response")
    return route.toRoutePlan()
}

// ---- Wire DTOs, matching Routes API v2's computeRoutes JSON shape --------------------------

@Serializable
private data class ComputeRoutesRequest(
    val origin: Waypoint,
    val destination: Waypoint,
    val travelMode: String = "TWO_WHEELER",
    val polylineQuality: String = "HIGH_QUALITY",
)

@Serializable
private data class Waypoint(val location: Location)

@Serializable
private data class Location(val latLng: LatLngDto)

@Serializable
private data class LatLngDto(val latitude: Double, val longitude: Double)

@Serializable
private data class ComputeRoutesResponse(val routes: List<RouteDto> = emptyList())

@Serializable
private data class RouteDto(
    val distanceMeters: Int = 0,
    val duration: String = "0s",
    val polyline: PolylineDto? = null,
    val routeToken: String? = null,
    val legs: List<LegDto> = emptyList(),
) {
    fun toRoutePlan(): RoutePlan = RoutePlan(
        legs = legs.map { it.toRouteLeg() },
        overviewPolyline = polyline?.encodedPolyline.orEmpty(),
        distanceM = distanceMeters,
        durationS = duration.parseSeconds(),
        routeToken = routeToken,
    )
}

@Serializable
private data class LegDto(
    val distanceMeters: Int = 0,
    val duration: String = "0s",
    val steps: List<StepDto> = emptyList(),
) {
    fun toRouteLeg(): RouteLeg = RouteLeg(
        steps = steps.map { it.toRouteStep() },
        distanceM = distanceMeters,
        durationS = duration.parseSeconds(),
    )
}

@Serializable
private data class StepDto(
    val distanceMeters: Int = 0,
    val staticDuration: String = "0s",
    val polyline: PolylineDto? = null,
    val startLocation: LocationDto? = null,
    val endLocation: LocationDto? = null,
    val navigationInstruction: NavigationInstructionDto? = null,
) {
    fun toRouteStep(): RouteStep = RouteStep(
        maneuver = navigationInstruction?.maneuver ?: "MANEUVER_UNSPECIFIED",
        polyline = polyline?.encodedPolyline.orEmpty(),
        distanceM = distanceMeters,
        durationS = staticDuration.parseSeconds(),
        instruction = navigationInstruction?.instructions.orEmpty(),
        startLocation = startLocation?.toLatLng() ?: LatLng(0.0, 0.0),
        endLocation = endLocation?.toLatLng() ?: LatLng(0.0, 0.0),
    )
}

@Serializable
private data class LocationDto(val latLng: LatLngDto) {
    fun toLatLng(): LatLng = LatLng(latLng.latitude, latLng.longitude)
}

@Serializable
private data class NavigationInstructionDto(val maneuver: String? = null, val instructions: String? = null)

@Serializable
private data class PolylineDto(@SerialName("encodedPolyline") val encodedPolyline: String = "")

/** Routes API durations are like `"123s"`. */
private fun String.parseSeconds(): Int = removeSuffix("s").toDoubleOrNull()?.toInt() ?: 0
