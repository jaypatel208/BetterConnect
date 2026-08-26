package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.link.WriteScheduler
import dev.jay.betterconnect.core.protocol.TbtEncoder
import dev.jay.betterconnect.core.protocol.TbtFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SequenceProgress(
    val script: SequenceScript,
    val index: Int,
    val looping: Boolean,
) {
    val step: SequenceStep get() = script.steps[index]
    val total: Int get() = script.steps.size
    val fraction: Float get() = if (total == 0) 0f else (index + 1).toFloat() / total
}

/**
 * Plays a [SequenceScript] into the [WriteScheduler].
 *
 * The runner only sets the current frame; the scheduler keeps re-asserting it for the
 * dwell period. That split matters - the cluster needs the heartbeat regardless of
 * whether a human or a script is choosing what to show.
 */
class SequenceRunner(
    private val scheduler: WriteScheduler,
    private val encoder: TbtEncoder = TbtEncoder(),
) {

    private val _progress = MutableStateFlow<SequenceProgress?>(null)
    val progress: StateFlow<SequenceProgress?> = _progress.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start(
        scope: CoroutineScope,
        script: SequenceScript,
        dwellMs: Long = script.defaultDwellMs,
        loop: Boolean = false,
    ): Job {
        stop()
        return scope.launch {
            do {
                for ((index, step) in script.steps.withIndex()) {
                    if (!isActive) break
                    _progress.value = SequenceProgress(script, index, loop)
                    scheduler.setFrame(TbtFrame(encoder.encode(step.nav)))
                    delay(dwellMs)
                }
            } while (loop && isActive)
            _progress.value = null
        }.also { job = it }
    }

    /** Stops and clears the cluster, so a half-finished script does not leave an arrow up. */
    fun stop() {
        job?.cancel()
        job = null
        _progress.value = null
    }
}
