package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.protocol.TbtEncoder
import dev.jay.betterconnect.core.protocol.TbtFrame
import dev.jay.betterconnect.core.testing.TestClocks
import dev.jay.betterconnect.core.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagLogTest {

    private val frame = TbtFrame(TbtEncoder(TestClocks.TEN_AM).encode(TestData.navLeft500))

    @Test
    fun `frames are recorded with both hex and a decoded summary`() {
        val log = DiagLog()
        log.frame("BLE", frame, nowMs = 1_000, outcome = "ACCEPTED")

        val entry = log.entries.value.single()
        assertEquals(LogLevel.FRAME, entry.level)
        assertTrue(entry.hex!!.startsWith("11 49"))
        assertTrue("decoded summary should name the icon", entry.decoded!!.contains("Turn left"))
        assertTrue("decoded summary should carry the street", entry.decoded.contains("MG ROAD"))
    }

    @Test
    fun `a corrupt frame is reported rather than silently dropped`() {
        val log = DiagLog()
        val corrupted = frame.bytes.copyOf().also { it[47] = 0 }
        log.frame("BLE", TbtFrame(corrupted), nowMs = 1, outcome = "ACCEPTED")

        assertTrue(log.entries.value.single().decoded!!.contains("BAD CHECKSUM"))
    }

    @Test
    fun `the buffer is bounded and keeps the most recent entries`() {
        val log = DiagLog(capacity = 10)
        repeat(25) { log.log(LogLevel.INFO, "T", "entry $it", nowMs = it.toLong()) }

        val entries = log.entries.value
        assertEquals(10, entries.size)
        assertEquals("entry 15", entries.first().message)
        assertEquals("entry 24", entries.last().message)
    }

    @Test
    fun `export includes hex and decode for frames`() {
        val log = DiagLog()
        log.log(LogLevel.INFO, "LINK", "connected", nowMs = 1)
        log.frame("BLE", frame, nowMs = 2, outcome = "ACCEPTED")

        val text = log.export()
        assertTrue(text.contains("[LINK] connected"))
        assertTrue(text.contains("hex: 11 49"))
        assertTrue(text.contains("dec: I"))
    }

    @Test
    fun `clear empties the buffer`() {
        val log = DiagLog()
        log.log(LogLevel.INFO, "T", "x", nowMs = 1)
        log.clear()
        assertTrue(log.entries.value.isEmpty())
    }

    @Test
    fun `an attached sink receives every non-frame entry`() {
        val log = DiagLog()
        val lines = mutableListOf<String>()
        log.sink = { lines += it }

        log.log(LogLevel.INFO, "LINK", "connected", nowMs = 1)
        log.log(LogLevel.WARN, "LINK", "disconnected status=8", nowMs = 2)

        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("connected"))
        assertTrue(lines[1].contains("disconnected status=8"))
    }

    @Test
    fun `the sink does not receive frame entries by default`() {
        val log = DiagLog()
        val lines = mutableListOf<String>()
        log.sink = { lines += it }

        log.frame("BLE", frame, nowMs = 1, outcome = "ACCEPTED")

        assertTrue("an hour of frames would make the ride log unreadable", lines.isEmpty())
    }

    @Test
    fun `sinkFrames opts back in for frame-level debugging`() {
        val log = DiagLog()
        val lines = mutableListOf<String>()
        log.sink = { lines += it }
        log.sinkFrames = true

        log.frame("BLE", frame, nowMs = 1, outcome = "ACCEPTED")

        assertEquals(1, lines.size)
    }
}
