package dev.jay.betterconnect.core.data

import dev.jay.betterconnect.core.domain.DiagLog
import java.io.File

/**
 * File-backed sink for [DiagLog]. The in-memory ring is bounded at 500 entries so the on-screen
 * debug view stays fast, but an hour-long ride outruns that even logging events only (not
 * frames) - so every entry is also appended to a per-ride file that survives a process kill and
 * covers the whole ride for export.
 */
class RideLog(private val directory: File) {

    val file: File = File(directory, "ride-${System.currentTimeMillis()}.log").also { directory.mkdirs() }

    fun attach(log: DiagLog) {
        log.sink = { line -> file.appendText("$line\n") }
    }

    /** Earlier rides left on disk, most recent first - useful once export ever needs a picker. */
    fun previousRides(): List<File> = directory.listFiles()
        ?.filter { it != file }
        ?.sortedByDescending { it.lastModified() }
        .orEmpty()
}
