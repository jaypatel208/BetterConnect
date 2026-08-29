package dev.jay.betterconnect.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses a representative `computeRoutes` response - shaped like Google's own documented
 * example - without a network call, so the DTO mapping is proven on a laptop rather than
 * against a billed request.
 */
class RoutesApiRepositoryTest {

    private val sampleResponse = """
        {
          "routes": [
            {
              "distanceMeters": 2224,
              "duration": "312s",
              "polyline": { "encodedPolyline": "_p~iF~ps|U_ulLnnqC" },
              "routeToken": "abc123",
              "legs": [
                {
                  "distanceMeters": 2224,
                  "duration": "312s",
                  "steps": [
                    {
                      "distanceMeters": 1112,
                      "staticDuration": "150s",
                      "polyline": { "encodedPolyline": "_p~iF~ps|U" },
                      "startLocation": { "latLng": { "latitude": 38.5, "longitude": -120.2 } },
                      "endLocation": { "latLng": { "latitude": 40.7, "longitude": -120.95 } },
                      "navigationInstruction": {
                        "maneuver": "TURN_LEFT",
                        "instructions": "Turn left onto Main St"
                      }
                    },
                    {
                      "distanceMeters": 1112,
                      "staticDuration": "162s",
                      "polyline": { "encodedPolyline": "_ulLnnqC" },
                      "startLocation": { "latLng": { "latitude": 40.7, "longitude": -120.95 } },
                      "endLocation": { "latLng": { "latitude": 43.252, "longitude": -126.453 } },
                      "navigationInstruction": { "maneuver": "STRAIGHT" }
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `maps the route's top-level distance, duration and polyline`() {
        val plan = parseComputeRoutesResponse(sampleResponse)
        assertEquals(2224, plan.distanceM)
        assertEquals(312, plan.durationS)
        assertEquals("_p~iF~ps|U_ulLnnqC", plan.overviewPolyline)
        assertEquals("abc123", plan.routeToken)
    }

    @Test
    fun `flattens legs into steps in order`() {
        val plan = parseComputeRoutesResponse(sampleResponse)
        assertEquals(2, plan.steps.size)
        assertEquals("TURN_LEFT", plan.steps[0].maneuver)
        assertEquals("STRAIGHT", plan.steps[1].maneuver)
    }

    @Test
    fun `carries the raw maneuver string through unmapped, for ManeuverMapper to handle`() {
        val plan = parseComputeRoutesResponse(sampleResponse)
        assertEquals("Turn left onto Main St", plan.steps[0].instruction)
    }

    @Test
    fun `maps step start and end locations`() {
        val plan = parseComputeRoutesResponse(sampleResponse)
        assertEquals(38.5, plan.steps[0].startLocation.lat, 1e-9)
        assertEquals(-120.2, plan.steps[0].startLocation.lng, 1e-9)
        assertEquals(40.7, plan.steps[0].endLocation.lat, 1e-9)
    }

    @Test
    fun `a step with no navigationInstruction falls back to unspecified rather than crashing`() {
        val response = sampleResponse.replace(
            """"navigationInstruction": { "maneuver": "STRAIGHT" }""",
            "\"navigationInstruction\": null",
        )
        val plan = parseComputeRoutesResponse(response)
        assertEquals("MANEUVER_UNSPECIFIED", plan.steps[1].maneuver)
    }

    @Test
    fun `an empty routes array throws rather than silently returning a blank plan`() {
        val response = """{"routes": []}"""
        val error = runCatching { parseComputeRoutesResponse(response) }.exceptionOrNull()
        assertTrue(error != null)
    }

    @Test
    fun `unknown fields in the response are ignored rather than failing parsing`() {
        val response = sampleResponse.replace(
            "\"distanceMeters\": 2224,",
            "\"distanceMeters\": 2224, \"someFutureField\": {\"nested\": true},",
        )
        val plan = parseComputeRoutesResponse(response)
        assertEquals(2224, plan.distanceM)
    }
}
