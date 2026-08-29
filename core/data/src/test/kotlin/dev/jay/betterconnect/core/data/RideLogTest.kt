package dev.jay.betterconnect.core.data

import dev.jay.betterconnect.core.domain.DiagLog
import dev.jay.betterconnect.core.domain.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RideLogTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `attaching records every logged line to the ride's own file`() {
        val rideLog = RideLog(tempFolder.newFolder())
        val log = DiagLog()
        rideLog.attach(log)

        log.log(LogLevel.INFO, "LINK", "connected", nowMs = 1)
        log.log(LogLevel.WARN, "LINK", "disconnected status=8", nowMs = 2)

        val lines = rideLog.file.readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("connected"))
        assertTrue(lines[1].contains("disconnected status=8"))
    }

    @Test
    fun `previous rides lists earlier files but not the current one`() {
        val directory = tempFolder.newFolder()
        val earlier = java.io.File(directory, "ride-1.log").also { it.writeText("old ride") }

        val rideLog = RideLog(directory)
        rideLog.attach(DiagLog())

        val previous = rideLog.previousRides()
        assertEquals(listOf(earlier), previous)
    }
}
