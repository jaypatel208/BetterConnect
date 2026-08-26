package dev.jay.betterconnect.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceCodecTest {

    @Test
    fun `metre mode keeps the whole value and zero fraction`() {
        listOf(0, 1, 50, 850, 998).forEach { m ->
            assertEquals(
                "for $m m",
                DistanceField(isMetres = true, whole = m, fraction = 0),
                DistanceCodec.encode(m),
            )
        }
    }

    @Test
    fun `kilometre mode splits into whole and hundredths`() {
        assertEquals(DistanceField(false, 1, 0), DistanceCodec.encode(1000))
        assertEquals(DistanceField(false, 2, 50), DistanceCodec.encode(2500))
        assertEquals(DistanceField(false, 12, 35), DistanceCodec.encode(12_345))
        assertEquals(DistanceField(false, 50, 0), DistanceCodec.encode(50_000))
    }

    /**
     * The original compares against 999 rather than 1000, so 999 m renders as "1.00 km".
     * We reproduce that by default and correct it behind a flag - both behaviours pinned
     * so a future change to the default is a deliberate, visible decision.
     */
    @Test
    fun `999 metres falls into the kilometre branch by default`() {
        assertEquals(DistanceField(false, 1, 0), DistanceCodec.encode(999, strictBoundary = false))
    }

    @Test
    fun `strict boundary keeps 999 metres in metre mode`() {
        assertEquals(DistanceField(true, 999, 0), DistanceCodec.encode(999, strictBoundary = true))
        assertEquals(DistanceField(false, 1, 0), DistanceCodec.encode(1000, strictBoundary = true))
    }

    @Test
    fun `negative input is clamped rather than producing garbage bytes`() {
        assertEquals(DistanceField(true, 0, 0), DistanceCodec.encode(-5))
    }

    @Test
    fun `whole and fraction are independent little-endian uint16 fields`() {
        val buffer = ByteArray(ClusterProtocol.TBT_SIZE)
        DistanceCodec.writeTo(DistanceField(false, 0x0102, 0x0304), buffer, 2)

        assertEquals(0x04, buffer[2].toInt() and 0xFF)
        assertEquals(0x03, buffer[3].toInt() and 0xFF)
        assertEquals(0x02, buffer[4].toInt() and 0xFF)
        assertEquals(0x01, buffer[5].toInt() and 0xFF)
    }

    @Test
    fun `write then read is lossless for the encoded fields`() {
        val buffer = ByteArray(ClusterProtocol.TBT_SIZE)
        for (metres in 0..60_000 step 37) {
            val encoded = DistanceCodec.encode(metres)
            DistanceCodec.writeTo(encoded, buffer, 2)
            assertEquals("for $metres m", encoded, DistanceCodec.readFrom(buffer, 2, encoded.isMetres))
        }
    }
}
