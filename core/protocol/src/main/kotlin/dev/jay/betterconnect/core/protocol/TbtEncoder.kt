package dev.jay.betterconnect.core.protocol

import dev.jay.betterconnect.core.model.NavState
import java.time.Clock
import java.time.LocalDateTime

/**
 * Builds the 48-byte TBT_INFO frame. Layout is documented in PROTOCOL.md §3.
 *
 * [clock] is injected so ETA encoding is deterministic under test - the ETA field is a
 * wall-clock time derived from `now + etaSeconds`, not a duration.
 */
class TbtEncoder(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val strictDistanceBoundary: Boolean = false,
) {

    fun encode(nav: NavState): ByteArray {
        val buffer = ByteArray(ClusterProtocol.TBT_SIZE)

        val turn = DistanceCodec.encode(nav.distanceToTurnM, strictDistanceBoundary)
        val total = DistanceCodec.encode(nav.distanceLeftM, strictDistanceBoundary)
        val eta = LocalDateTime.now(clock).plusSeconds(nav.etaSeconds)

        val hour24 = eta.hour
        val hour12 = when (val h = hour24 % 12) {
            0 -> 12
            else -> h
        }
        val isPm = hour24 >= 12

        var flags = FLAG_CONSTANT
        if (turn.isMetres) flags = flags or FLAG_TURN_METRES
        if (isPm) flags = flags or FLAG_PM
        buffer[0] = flags.toByte()

        buffer[1] = nav.symbolCode.toByte()
        DistanceCodec.writeTo(turn, buffer, 2)

        buffer[6] = eta.minute.toByte()
        buffer[7] = (hour12 or (nav.roundaboutExit.coerceIn(0, 7) shl 4)).toByte()

        DistanceCodec.writeTo(total, buffer, 8)

        var flags2 = 0
        if (total.isMetres) flags2 = flags2 or FLAG2_TOTAL_METRES
        if (nav.gpsActive) flags2 = flags2 or FLAG2_GPS_ACTIVE
        buffer[12] = flags2.toByte()
        buffer[13] = 0

        TextCodec.writeTo(TextCodec.sanitise(nav.text), buffer)

        // byte 46 stays reserved-zero
        Checksum.apply(buffer)
        return buffer
    }

    companion object {
        const val FLAG_CONSTANT = 0x01
        const val FLAG_TURN_METRES = 1 shl 4
        const val FLAG_PM = 1 shl 7
        const val FLAG2_TOTAL_METRES = 0x01
        const val FLAG2_GPS_ACTIVE = 1 shl 2

        /**
         * Clears the cluster's navigation area. The checksum of 47 zeros is itself zero,
         * so an all-zero buffer is already a valid frame. Must be sent on stop, or the
         * last instruction stays frozen on the display.
         */
        fun endNavigationFrame(): ByteArray = ByteArray(ClusterProtocol.TBT_SIZE)
    }
}
