package dev.jay.betterconnect.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCodecTest {

    @Test
    fun `keeps alphanumerics and dots`() {
        assertEquals("MG ROAD 42.5", TextCodec.sanitise("MG ROAD 42.5"))
    }

    @Test
    fun `replaces everything else with a space, which loses hyphens in road names`() {
        assertEquals("Sarkhej Gandhinagar Hwy", TextCodec.sanitise("Sarkhej-Gandhinagar Hwy"))
        assertEquals("A B", TextCodec.sanitise("A/B"))
    }

    @Test
    fun `trims after substitution so leading punctuation does not become padding`() {
        assertEquals("MG ROAD", TextCodec.sanitise("--MG ROAD--"))
    }

    @Test
    fun `clips to 31 characters`() {
        val long = "A".repeat(40)
        assertEquals(31, TextCodec.sanitise(long).length)
    }

    @Test
    fun `round trips every length from empty to the cap`() {
        val buffer = ByteArray(ClusterProtocol.TBT_SIZE)
        for (len in 0..ClusterProtocol.MAX_TEXT_LEN) {
            val text = (0 until len).map { ('A' + (it % 26)) }.joinToString("")
            buffer.fill(0)
            TextCodec.writeTo(text, buffer)
            assertEquals("for length $len", text, TextCodec.readFrom(buffer))
        }
    }

    @Test
    fun `never writes past the text region`() {
        val buffer = ByteArray(ClusterProtocol.TBT_SIZE)
        TextCodec.writeTo("Z".repeat(40), buffer)
        assertEquals(ClusterProtocol.MAX_TEXT_LEN, buffer[14].toInt() and 0xFF)
        assertEquals("byte 46 is reserved", 0, buffer[46].toInt())
        assertEquals("checksum slot untouched by text", 0, buffer[47].toInt())
    }
}
