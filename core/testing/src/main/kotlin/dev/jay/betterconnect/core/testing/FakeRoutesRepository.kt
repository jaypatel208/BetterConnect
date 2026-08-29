package dev.jay.betterconnect.core.testing

import dev.jay.betterconnect.core.domain.RoutesRepository
import dev.jay.betterconnect.core.model.LatLng
import dev.jay.betterconnect.core.model.RoutePlan

/** Returns a canned [RoutePlan] (or a forced failure) so the guidance loop is testable offline. */
class FakeRoutesRepository(
    private var result: Result<RoutePlan> = Result.failure(IllegalStateException("no route set")),
) : RoutesRepository {

    var requestCount: Int = 0
        private set

    var lastOrigin: LatLng? = null
        private set

    var lastDestination: LatLng? = null
        private set

    fun setResult(plan: RoutePlan) {
        result = Result.success(plan)
    }

    fun setFailure(error: Throwable) {
        result = Result.failure(error)
    }

    override suspend fun computeRoute(origin: LatLng, destination: LatLng): Result<RoutePlan> {
        requestCount++
        lastOrigin = origin
        lastDestination = destination
        return result
    }
}
