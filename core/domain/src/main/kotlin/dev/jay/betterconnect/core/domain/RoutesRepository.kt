package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.model.LatLng
import dev.jay.betterconnect.core.model.RoutePlan

/**
 * Fetches a two-wheeler route. Behind an interface so the whole guidance loop is testable
 * offline with [dev.jay.betterconnect.core.testing.FakeRoutesRepository] - two-wheeler
 * routing is a billed Enterprise-SKU request, so tests must never depend on a real call.
 */
interface RoutesRepository {
    suspend fun computeRoute(origin: LatLng, destination: LatLng): Result<RoutePlan>
}
