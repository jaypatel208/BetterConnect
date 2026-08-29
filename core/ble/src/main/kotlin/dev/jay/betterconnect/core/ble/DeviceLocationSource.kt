package dev.jay.betterconnect.core.ble

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.jay.betterconnect.core.model.LatLng
import dev.jay.betterconnect.core.model.LocationFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wraps `FusedLocationProviderClient` as a [Flow] of [LocationFix] for the guidance loop.
 *
 * There is still no coroutine-native Flow API on `FusedLocationProviderClient` (checked
 * 2026-08-29) - this is `callbackFlow` over the callback API, which is the current
 * recommended approach. [maxUpdateDelayMs] is left at 0 (non-batched) deliberately: only
 * non-batched requests are guaranteed to deliver fixes in monotonically increasing time
 * order, and a guidance loop fed an out-of-order fix would jump backwards along the route.
 */
object DeviceLocationSource {

    @SuppressLint("MissingPermission")
    fun fixes(
        context: Context,
        intervalMs: Long = 1_000L,
        maxUpdateDelayMs: Long = 0L,
    ): Flow<LocationFix> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMaxUpdateDelayMillis(maxUpdateDelayMs)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(
                        LocationFix(
                            position = LatLng(location.latitude, location.longitude),
                            bearingDeg = if (location.hasBearing()) location.bearing else null,
                            speedMps = if (location.hasSpeed()) location.speed else null,
                            accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                            timestampMs = location.time,
                        ),
                    )
                }
            }
        }

        client.requestLocationUpdates(request, callback, context.mainLooper)
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
