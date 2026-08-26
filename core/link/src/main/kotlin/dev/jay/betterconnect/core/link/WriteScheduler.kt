package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.protocol.ClusterProtocol
import dev.jay.betterconnect.core.protocol.TbtFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the 350 ms heartbeat and the one-outstanding-write rule.
 *
 * Two behaviours here are load-bearing and both come from the link being one-way:
 *
 *  - State is re-sent continuously rather than as deltas, because nothing acknowledges
 *    a frame and nothing can be re-requested.
 *  - A frame that arrives while a write is in flight is dropped, never queued. Queueing
 *    would build a backlog of stale instructions and surface them late, which on a bike
 *    is worse than briefly showing nothing.
 */
class WriteScheduler(
    private val transport: ClusterTransport,
    private val periodMs: Long = ClusterProtocol.HEARTBEAT_MS,
) {

    private val _stats = MutableStateFlow(WriteStats())
    val stats: StateFlow<WriteStats> = _stats.asStateFlow()

    private val _currentFrame = MutableStateFlow<TbtFrame?>(null)
    val currentFrame: StateFlow<TbtFrame?> = _currentFrame.asStateFlow()

    private val _mode = MutableStateFlow(SendMode.HEARTBEAT)
    val mode: StateFlow<SendMode> = _mode.asStateFlow()

    private var job: Job? = null

    fun setMode(mode: SendMode) {
        _mode.value = mode
    }

    /** Sets the frame and sends it immediately so the UI feels responsive. */
    fun setFrame(frame: TbtFrame) {
        _currentFrame.value = frame
        attempt(frame)
    }

    /**
     * Clears the cluster and stops repeating. Without this the last instruction stays
     * frozen on the display indefinitely.
     */
    fun clear() {
        val frame = TbtFrame.endNavigation()
        _currentFrame.value = null
        attempt(frame)
    }

    fun start(scope: CoroutineScope): Job {
        job?.cancel()
        return scope.launch {
            while (isActive) {
                delay(periodMs)
                if (_mode.value == SendMode.HEARTBEAT) {
                    _currentFrame.value?.let(::attempt)
                }
            }
        }.also { job = it }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun resetStats() {
        _stats.value = WriteStats()
    }

    private fun attempt(frame: TbtFrame) {
        when (transport.write(frame)) {
            WriteOutcome.ACCEPTED -> _stats.update { it.copy(sent = it.sent + 1) }
            WriteOutcome.BUSY -> _stats.update { it.copy(dropped = it.dropped + 1) }
            WriteOutcome.FAILED -> _stats.update { it.copy(failed = it.failed + 1) }
            WriteOutcome.NOT_READY -> _stats.update { it.copy(notReady = it.notReady + 1) }
        }
    }
}
