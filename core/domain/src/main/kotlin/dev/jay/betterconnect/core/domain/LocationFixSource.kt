package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.model.LocationFix
import kotlinx.coroutines.flow.Flow

/**
 * Behind an interface for the same reason as [RoutesRepository]: the real implementation
 * touches `FusedLocationProviderClient` (Android, no Robolectric shadow worth trusting for
 * this), so [dev.jay.betterconnect.core.testing.FakeLocationFixSource] drives
 * [GuidanceEngine]'s consumer in tests instead.
 */
fun interface LocationFixSource {
    fun fixes(): Flow<LocationFix>
}
