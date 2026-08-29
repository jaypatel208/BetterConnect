package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.model.LatLng

/**
 * Google's encoded polyline algorithm (precision 1e5) - the format Routes API returns for
 * both the overview and per-step polylines. Fixed and unversioned, unlike the REST API
 * itself: https://developers.google.com/maps/documentation/utilities/polylinealgorithm.
 */
object PolylineCodec {

    private const val PRECISION = 1e5

    fun decode(encoded: String): List<LatLng> {
        val points = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            lat += readValue(encoded, index).also { index = it.second }.first
            lng += readValue(encoded, index).also { index = it.second }.first
            points += LatLng(lat / PRECISION, lng / PRECISION)
        }
        return points
    }

    fun encode(points: List<LatLng>): String {
        val sb = StringBuilder()
        var prevLat = 0
        var prevLng = 0
        for (point in points) {
            val lat = Math.round(point.lat * PRECISION).toInt()
            val lng = Math.round(point.lng * PRECISION).toInt()
            appendValue(lat - prevLat, sb)
            appendValue(lng - prevLng, sb)
            prevLat = lat
            prevLng = lng
        }
        return sb.toString()
    }

    /** Returns the decoded delta and the index just past it. */
    private fun readValue(encoded: String, start: Int): Pair<Int, Int> {
        var index = start
        var shift = 0
        var result = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val delta = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        return delta to index
    }

    private fun appendValue(value: Int, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else (value shl 1)
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }
}
