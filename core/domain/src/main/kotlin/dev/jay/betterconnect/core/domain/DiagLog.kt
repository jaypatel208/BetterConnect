package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.protocol.DecodeResult
import dev.jay.betterconnect.core.protocol.TbtFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogLevel { INFO, FRAME, WARN, ERROR }

data class LogEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    /** Present for FRAME entries: the exact bytes, plus how they decode. */
    val hex: String? = null,
    val decoded: String? = null,
)

/**
 * Bounded in-memory log.
 *
 * The cluster acknowledges nothing, so this log is the only record of what was actually
 * sent. That makes it a primary diagnostic, not a debugging afterthought.
 */
class DiagLog(private val capacity: Int = DEFAULT_CAPACITY) {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    /**
     * Optional sink for every entry as it is appended, formatted the same as [export]'s
     * per-entry lines. The ride log (`docs/DEVELOPMENT-NOTES.md` D1/D4 bisect) needs more
     * than [capacity]'s 500-entry ring can hold for an hour-long ride, so the debug menu
     * attaches a file-backed sink here rather than this class growing a second log type.
     */
    var sink: ((String) -> Unit)? = null

    /**
     * Off by default: at the 800 ms TBT cadence a ride is ~4,500 [LogLevel.FRAME] entries an
     * hour, which makes the exported file unreadable for what the ride log actually exists
     * to answer (connects, drops, reroutes, unmapped manoeuvres). The debug menu's frame-
     * logging toggle flips this on only when someone is actively chasing a frame-level bug.
     */
    var sinkFrames: Boolean = false

    fun log(level: LogLevel, tag: String, message: String, nowMs: Long) {
        append(LogEntry(nowMs, level, tag, message))
    }

    fun frame(tag: String, frame: TbtFrame, nowMs: Long, outcome: String) {
        val decoded = when (val result = frame.decode()) {
            is DecodeResult.Valid -> result.frame.let {
                "${it.symbolChar} ${it.symbolLabel} | turn ${it.turn.describe()} | " +
                    "left ${it.total.describe()} | ETA ${it.etaHour12}:%02d %s | exit ${it.roundaboutExit} | \"${it.text}\""
                        .format(it.etaMinute, if (it.isPm) "PM" else "AM")
            }
            is DecodeResult.BadChecksum -> "BAD CHECKSUM expected ${result.expected} got ${result.actual}"
            is DecodeResult.BadSize -> "BAD SIZE ${result.size}"
        }
        append(
            LogEntry(
                timestampMs = nowMs,
                level = LogLevel.FRAME,
                tag = tag,
                message = outcome,
                hex = frame.toHex(),
                decoded = decoded,
            ),
        )
    }

    fun clear() {
        _entries.value = emptyList()
    }

    /** Plain text for sharing out of the app. */
    fun export(): String = _entries.value.joinToString("\n") { formatLine(it) }

    private fun formatLine(entry: LogEntry): String = buildString {
        append(entry.timestampMs).append(' ')
        append(entry.level.name.padEnd(5)).append(' ')
        append('[').append(entry.tag).append("] ")
        append(entry.message)
        entry.hex?.let { append("\n    hex: ").append(it) }
        entry.decoded?.let { append("\n    dec: ").append(it) }
    }

    private fun append(entry: LogEntry) {
        _entries.update { current ->
            val next = current + entry
            if (next.size > capacity) next.subList(next.size - capacity, next.size) else next
        }
        if (entry.level != LogLevel.FRAME || sinkFrames) {
            sink?.invoke(formatLine(entry))
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 500
    }
}

private fun dev.jay.betterconnect.core.protocol.DistanceField.describe(): String =
    if (isMetres) "$whole m" else "$whole.%02d km".format(fraction)
