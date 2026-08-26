package dev.jay.betterconnect.core.protocol

import dev.jay.betterconnect.core.model.NavState
import dev.jay.betterconnect.core.model.Symbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The test that makes the packet layout trustworthy.
 *
 * The cluster never acknowledges anything, so a wrong frame and a dropped frame look
 * identical on the bike. Sweeping the whole input space through encode -> decode turns
 * "is the packet right?" into something a laptop can answer.
 *
 * Distances are compared as encoded [DistanceField]s rather than metres: kilometre mode
 * keeps two decimals, so 12 345 m legitimately returns as 12.35 km.
 */
class RoundTripTest {

    private val encoder = TbtEncoder(clock = TestClocks.TEN_AM)

    private fun assertRoundTrip(nav: NavState) {
        val bytes = encoder.encode(nav)
        assertEquals("frame size", ClusterProtocol.TBT_SIZE, bytes.size)

        val result = TbtDecoder.decode(bytes)
        assertTrue("checksum invalid for $nav", result is DecodeResult.Valid)
        val frame = (result as DecodeResult.Valid).frame

        assertEquals("symbol for $nav", nav.symbolCode, frame.symbolCode)
        assertEquals("blink for $nav", nav.blinking, frame.blinking)
        assertEquals("turn for $nav", DistanceCodec.encode(nav.distanceToTurnM), frame.turn)
        assertEquals("total for $nav", DistanceCodec.encode(nav.distanceLeftM), frame.total)
        assertEquals("exit for $nav", nav.roundaboutExit, frame.roundaboutExit)
        assertEquals("gps for $nav", nav.gpsActive, frame.gpsActive)
        assertEquals("text for $nav", TextCodec.sanitise(nav.text), frame.text)
        assertTrue("constant bit for $nav", frame.constantBitSet)
        assertEquals("byte 13 reserved", 0, frame.reservedByte13)
        assertEquals("byte 46 reserved", 0, frame.reservedByte46)
    }

    @Test
    fun `every symbol round trips in both steady and blinking form`() {
        Symbol.entries.forEach { symbol ->
            // Distance drives the blink rule, so 500 m gives steady and 50 m gives blinking.
            listOf(500, 50).forEach { distance ->
                assertRoundTrip(
                    NavState(
                        symbol = symbol,
                        distanceToTurnM = distance,
                        distanceLeftM = 5_000,
                        etaSeconds = 600,
                        text = symbol.label,
                    ),
                )
            }
        }
    }

    @Test
    fun `distances sweep across the whole supported range`() {
        for (turn in 0..50_000 step 311) {
            assertRoundTrip(
                NavState(
                    symbol = Symbol.LEFT,
                    distanceToTurnM = turn,
                    distanceLeftM = turn * 2,
                    etaSeconds = 900,
                    text = "SWEEP",
                ),
            )
        }
    }

    @Test
    fun `text sweeps every length including past the cap`() {
        for (len in 0..40) {
            assertRoundTrip(
                NavState(
                    symbol = Symbol.RIGHT,
                    distanceToTurnM = 300,
                    distanceLeftM = 3_000,
                    etaSeconds = 300,
                    text = (0 until len).map { 'A' + (it % 26) }.joinToString(""),
                ),
            )
        }
    }

    @Test
    fun `roundabout exits 0 through 7 survive the shared byte with the ETA hour`() {
        for (exit in 0..7) {
            assertRoundTrip(
                NavState(
                    symbol = Symbol.ROUNDABOUT,
                    distanceToTurnM = 400,
                    distanceLeftM = 4_000,
                    etaSeconds = 1_200,
                    text = "ROUNDABOUT",
                    roundaboutExit = exit,
                ),
            )
        }
    }

    /**
     * The ETA is a wall-clock time in 12-hour form with the meridiem in a flag bit, so
     * midnight and noon are the interesting cases - both map to hour 12.
     */
    @Test
    fun `eta round trips across every hour of the day`() {
        for (hour in 0..23) {
            val clock = TestClocks.at("2026-01-01T%02d:37:00Z".format(hour))
            val nav = NavState(
                symbol = Symbol.STRAIGHT,
                distanceToTurnM = 800,
                distanceLeftM = 8_000,
                etaSeconds = 0,
                text = "ETA",
            )
            val frame = (TbtDecoder.decode(TbtEncoder(clock).encode(nav)) as DecodeResult.Valid).frame

            assertEquals("hour24 for $hour", hour, frame.etaHour24)
            assertEquals("minute for $hour", 37, frame.etaMinute)
            assertEquals("pm flag for $hour", hour >= 12, frame.isPm)
            assertTrue("hour12 in range for $hour", frame.etaHour12 in 1..12)
        }
    }

    @Test
    fun `gps inactive round trips`() {
        assertRoundTrip(
            NavState(
                symbol = Symbol.LEFT,
                distanceToTurnM = 200,
                distanceLeftM = 2_000,
                etaSeconds = 120,
                text = "NO FIX",
                gpsActive = false,
            ),
        )
    }
}
