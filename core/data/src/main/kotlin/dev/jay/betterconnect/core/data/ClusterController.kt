package dev.jay.betterconnect.core.data

import dev.jay.betterconnect.core.domain.DiagLog
import dev.jay.betterconnect.core.domain.LogLevel
import dev.jay.betterconnect.core.domain.SequenceRunner
import dev.jay.betterconnect.core.domain.SequenceScript
import dev.jay.betterconnect.core.link.DemoCapableTransport
import dev.jay.betterconnect.core.link.DeviceScanner
import dev.jay.betterconnect.core.link.SendMode
import dev.jay.betterconnect.core.link.WriteScheduler
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.NavState
import dev.jay.betterconnect.core.protocol.TbtEncoder
import dev.jay.betterconnect.core.protocol.TbtFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the cluster link.
 *
 * Owns the heartbeat, the sequence runner and the log, and keeps them consistent with the
 * transport's connection state. The ViewModels observe it; nothing else mutates it.
 */
@Singleton
class ClusterController @Inject constructor(
    private val transport: DemoCapableTransport,
    private val scheduler: WriteScheduler,
    private val runner: SequenceRunner,
    private val encoder: TbtEncoder,
    val scanner: DeviceScanner,
    val log: DiagLog,
    private val scope: CoroutineScope,
) {

    val state: StateFlow<ConnectionState> = transport.state
    val gattDump = transport.gattDump
    val demoMode = transport.demoMode
    val stats = scheduler.stats
    val currentFrame = scheduler.currentFrame
    val sendMode = scheduler.mode
    val sequenceProgress = runner.progress

    private val _lastNav = MutableStateFlow<NavState?>(null)
    val lastNav: StateFlow<NavState?> = _lastNav.asStateFlow()

    private var heartbeat: kotlinx.coroutines.Job? = null

    init {
        // The heartbeat only runs while the link is usable; starting it earlier would
        // just accumulate NOT_READY counts and drown the log.
        transport.state
            .onEach { state ->
                logState(state)
                if (state is ConnectionState.Ready) startHeartbeat() else stopHeartbeat()
            }
            .launchIn(scope)
    }

    fun setDemoMode(enabled: Boolean) {
        transport.setDemoMode(enabled)
        log.log(LogLevel.INFO, TAG, "demo mode ${if (enabled) "on" else "off"}", now())
    }

    fun connect(address: String) {
        log.log(LogLevel.INFO, TAG, "connect $address", now())
        transport.connect(address)
    }

    fun disconnect() {
        runner.stop()
        transport.disconnect()
    }

    fun setSendMode(mode: SendMode) = scheduler.setMode(mode)

    /** Sends a navigation state, recording both the intent and the exact bytes. */
    fun send(nav: NavState) {
        runner.stop()
        _lastNav.value = nav
        val frame = TbtFrame(encoder.encode(nav))
        scheduler.setFrame(frame)
        log.frame(TAG, frame, now(), outcome = describeLastOutcome())
    }

    fun clearCluster() {
        runner.stop()
        _lastNav.value = null
        scheduler.clear()
        log.log(LogLevel.INFO, TAG, "cleared cluster (48 zero bytes)", now())
    }

    fun startSequence(script: SequenceScript, dwellMs: Long, loop: Boolean) {
        log.log(LogLevel.INFO, TAG, "sequence '${script.name}' dwell=${dwellMs}ms loop=$loop", now())
        runner.start(scope, script, dwellMs, loop)
    }

    fun stopSequence() {
        runner.stop()
        clearCluster()
    }

    fun resetStats() = scheduler.resetStats()

    private fun startHeartbeat() {
        if (heartbeat?.isActive == true) return
        heartbeat = scheduler.start(scope)
    }

    private fun stopHeartbeat() {
        scheduler.stop()
        heartbeat = null
    }

    private fun logState(state: ConnectionState) {
        val (level, message) = when (state) {
            ConnectionState.Idle -> LogLevel.INFO to "idle"
            is ConnectionState.Connecting -> LogLevel.INFO to "connecting to ${state.address}"
            is ConnectionState.Discovering -> LogLevel.INFO to "discovering (mtu ${state.mtu})"
            is ConnectionState.Ready -> LogLevel.INFO to "ready, mtu ${state.mtu}"
            is ConnectionState.Unsupported -> LogLevel.ERROR to state.reason.message
            is ConnectionState.Disconnected -> LogLevel.WARN to "disconnected (status ${state.status})"
        }
        log.log(level, TAG, message, now())
    }

    private fun describeLastOutcome(): String {
        val s = stats.value
        return "sent=${s.sent} dropped=${s.dropped} failed=${s.failed} notReady=${s.notReady}"
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        private const val TAG = "CLUSTER"
    }
}
