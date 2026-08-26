package dev.jay.betterconnect.core.protocol

import dev.jay.betterconnect.core.model.NavState
import dev.jay.betterconnect.core.model.Symbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecksumTest {

    private val encoder = TbtEncoder(clock = TestClocks.TEN_AM)

    private val nav = NavState(
        symbol = Symbol.RIGHT,
        distanceToTurnM = 750,
        distanceLeftM = 9_400,
        etaSeconds = 1_500,
        text = "TEST ROAD",
    )

    @Test
    fun `checksum is the additive sum of bytes 0 to 46`() {
        val frame = encoder.encode(nav)
        val manual = (0..46).sumOf { frame[it].toInt() and 0xFF } and 0xFF
        assertEquals(manual, frame[ClusterProtocol.CHECKSUM_INDEX].toInt() and 0xFF)
    }

    /**
     * An additive checksum catches every single-byte corruption. It cannot catch two
     * compensating changes - that is inherent to the scheme, not a defect in this code.
     */
    @Test
    fun `corrupting any covered byte invalidates the frame`() {
        for (index in 0..46) {
            val corrupted = encoder.encode(nav)
            corrupted[index] = (corrupted[index] + 1).toByte()
            assertFalse("byte $index went undetected", Checksum.isValid(corrupted))

            val result = TbtDecoder.decode(corrupted)
            assertTrue("byte $index should decode as BadChecksum", result is DecodeResult.BadChecksum)
        }
    }

    @Test
    fun `decoder rejects wrong sized buffers`() {
        assertEquals(DecodeResult.BadSize(47), TbtDecoder.decode(ByteArray(47)))
        assertEquals(DecodeResult.BadSize(49), TbtDecoder.decode(ByteArray(49)))
        assertEquals(DecodeResult.BadSize(0), TbtDecoder.decode(ByteArray(0)))
    }

    @Test
    fun `bad checksum still exposes the decoded fields for diagnostics`() {
        val corrupted = encoder.encode(nav)
        corrupted[ClusterProtocol.CHECKSUM_INDEX] = 0x00
        val result = TbtDecoder.decode(corrupted)
        assertTrue(result is DecodeResult.BadChecksum)
        assertEquals('J', result.frameOrNull?.symbolChar)
    }
}
