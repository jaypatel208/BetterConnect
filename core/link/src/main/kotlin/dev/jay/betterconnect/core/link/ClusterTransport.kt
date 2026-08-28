package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.GattDump
import dev.jay.betterconnect.core.protocol.TbtFrame
import kotlinx.coroutines.flow.StateFlow

/**
 * The seam between the scheduler and the radio.
 *
 * Everything above this interface is pure and testable; the only implementation that
 * touches android.bluetooth lives in :core:ble. The fake in :core:testing decodes each
 * frame it receives, which is what lets the whole pipeline be asserted without hardware.
 */
interface ClusterTransport {
    val state: StateFlow<ConnectionState>
    val gattDump: StateFlow<GattDump?>

    fun connect(address: String)
    fun disconnect()

    /**
     * Non-suspending on purpose. Only one GATT operation may be outstanding at a time, so
     * a caller that cannot be served is told [WriteOutcome.BUSY] and drops the frame
     * rather than queueing it - a backlog of stale navigation frames is worse than a gap.
     */
    fun write(frame: TbtFrame): WriteOutcome
}

enum class WriteOutcome {
    ACCEPTED,

    /** A previous write has not completed. The frame is discarded, not queued. */
    BUSY,

    /** Not connected, or the characteristic was never found. */
    NOT_READY,

    /** The stack rejected the write outright. */
    FAILED,
}

data class WriteStats(val sent: Int = 0, val dropped: Int = 0, val failed: Int = 0, val notReady: Int = 0) {
    val attempted: Int get() = sent + dropped + failed + notReady
}

enum class SendMode {
    /**
     * Re-assert state every 350 ms. This is the transport, not an optimisation: with no
     * acknowledgement from the cluster, a dropped frame is only recoverable by repetition.
     */
    HEARTBEAT,

    /**
     * Send exactly once. Purely diagnostic - it answers whether the cluster latches a
     * frame or decays without one, which cannot be determined from the APK.
     */
    ONE_SHOT,
}

/**
 * A transport that can be swapped for an in-process fake at runtime.
 *
 * Demo mode is a first-class capability rather than a debug hack: it is what lets the whole
 * app be driven, and asserted, with no bike present.
 */
interface DemoCapableTransport : ClusterTransport {
    val demoMode: kotlinx.coroutines.flow.StateFlow<Boolean>
    fun setDemoMode(enabled: Boolean)
}
