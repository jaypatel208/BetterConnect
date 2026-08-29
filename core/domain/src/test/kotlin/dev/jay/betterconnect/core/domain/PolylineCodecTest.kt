package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PolylineCodecTest {

    /** Google's own worked example from the algorithm documentation. */
    private val googleExampleEncoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
    private val googleExamplePoints = listOf(
        LatLng(38.5, -120.2),
        LatLng(40.7, -120.95),
        LatLng(43.252, -126.453),
    )

    @Test
    fun `decodes Google's documented example exactly`() {
        val decoded = PolylineCodec.decode(googleExampleEncoded)
        assertEquals(googleExamplePoints.size, decoded.size)
        googleExamplePoints.zip(decoded).forEach { (expected, actual) ->
            assertEquals("lat", expected.lat, actual.lat, 1e-5)
            assertEquals("lng", expected.lng, actual.lng, 1e-5)
        }
    }

    @Test
    fun `encodes Google's documented example exactly`() {
        assertEquals(googleExampleEncoded, PolylineCodec.encode(googleExamplePoints))
    }

    @Test
    fun `round trips an empty list`() {
        assertEquals(emptyList<LatLng>(), PolylineCodec.decode(PolylineCodec.encode(emptyList())))
    }

    @Test
    fun `round trips a single point`() {
        val points = listOf(LatLng(12.9716, 77.5946))
        val decoded = PolylineCodec.decode(PolylineCodec.encode(points))
        assertEquals(1, decoded.size)
        assertEquals(points[0].lat, decoded[0].lat, 1e-5)
        assertEquals(points[0].lng, decoded[0].lng, 1e-5)
    }

    @Test
    fun `round trips negative coordinates`() {
        val points = listOf(LatLng(-33.8688, 151.2093), LatLng(-37.8136, 144.9631))
        val decoded = PolylineCodec.decode(PolylineCodec.encode(points))
        points.zip(decoded).forEach { (expected, actual) ->
            assertTrue(abs(expected.lat - actual.lat) < 1e-5)
            assertTrue(abs(expected.lng - actual.lng) < 1e-5)
        }
    }

    @Test
    fun `round trips a long sweep of points without drifting`() {
        val points = (0..500).map { i -> LatLng(12.0 + i * 0.001, 77.0 - i * 0.0007) }
        val decoded = PolylineCodec.decode(PolylineCodec.encode(points))
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEachIndexed { i, (expected, actual) ->
            assertTrue("lat drift at $i", abs(expected.lat - actual.lat) < 1e-5)
            assertTrue("lng drift at $i", abs(expected.lng - actual.lng) < 1e-5)
        }
    }
}
