package dev.jay.betterconnect.core.protocol

import dev.jay.betterconnect.core.model.NavState
import dev.jay.betterconnect.core.model.Symbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The known-good frame from PROTOCOL.md §4: left turn, 500 m to the turn,
 * 12.30 km remaining, ETA 10:45 AM, GPS active, street "MG ROAD".
 *
 * If this test fails, the encoder no longer matches the documented protocol and nothing
 * downstream can be trusted.
 */
class GoldenFrameTest {

    private val encoder = TbtEncoder(clock = TestClocks.TEN_AM)

    private val goldenNav = NavState(
        symbol = Symbol.LEFT,
        distanceToTurnM = 500,
        distanceLeftM = 12_300,
        etaSeconds = 45 * 60,
        text = "MG ROAD",
        gpsActive = true,
    )

    private val goldenBytes = intArrayOf(
        0x11, 0x49, 0x00, 0x00, 0xF4, 0x01, 0x2D, 0x0A,
        0x1E, 0x00, 0x0C, 0x00, 0x04, 0x00, 0x07, 0x4D,
        0x47, 0x20, 0x52, 0x4F, 0x41, 0x44, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x95,
    ).map { it.toByte() }.toByteArray()

    @Test
    fun `encodes the documented golden frame byte for byte`() {
        assertEquals(goldenBytes.toHex(), encoder.encode(goldenNav).toHex())
    }

    @Test
    fun `golden frame checksum is 0x95`() {
        assertEquals(0x95, Checksum.compute(goldenBytes))
        assertTrue(Checksum.isValid(goldenBytes))
    }

    @Test
    fun `golden frame decodes back to the documented field values`() {
        val result = TbtDecoder.decode(encoder.encode(goldenNav))
        assertTrue(result is DecodeResult.Valid)
        val frame = (result as DecodeResult.Valid).frame

        assertEquals('I', frame.symbolChar)
        assertEquals(false, frame.blinking)
        assertEquals(DistanceField(isMetres = true, whole = 500, fraction = 0), frame.turn)
        assertEquals(DistanceField(isMetres = false, whole = 12, fraction = 30), frame.total)
        assertEquals(10, frame.etaHour12)
        assertEquals(45, frame.etaMinute)
        assertEquals(false, frame.isPm)
        assertEquals(0, frame.roundaboutExit)
        assertEquals(true, frame.gpsActive)
        assertEquals("MG ROAD", frame.text)
        assertEquals(true, frame.constantBitSet)
    }

    @Test
    fun `end navigation frame is 48 zero bytes and self-consistent`() {
        val frame = TbtEncoder.endNavigationFrame()
        assertEquals(ClusterProtocol.TBT_SIZE, frame.size)
        assertTrue(frame.all { it == 0.toByte() })
        assertTrue("all-zero frame must be a valid frame", Checksum.isValid(frame))
    }
}
