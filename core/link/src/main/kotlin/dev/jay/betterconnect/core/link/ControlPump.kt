package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.model.ControlAcks
import dev.jay.betterconnect.core.protocol.ClusterProtocol
import dev.jay.betterconnect.core.protocol.ControlDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The `CONTROL` (`0A10`) read pump - the return channel, and A3/D1's leading suspect for
 * the 65 s disconnect. This cluster exposes no NOTIFY, so polling is the only route
 * (`docs/PROTOCOL.md` §5).
 *
 * Two gates are mandatory, both there to prevent phantom rider-button presses:
 * - **Whole-frame dedup** - if all 20 bytes match the previous read, the entire handler
 *   is skipped.
 * - **Bootstrap adoption** - the first frame after connect is adopted without acting, so a
 *   sticky value retained across a reconnect does not fire immediately.
 *
 * Produces [acks] - the `GENERAL` acknowledgement block - by comparing each new frame
 * against the previous one: mirror fields copy the new value, counter fields increment
 * (mod 256) only on an actual level change. This deliberately does **not** copy the vendor's
 * `launchMediaPlayerAck` defect (C6), which increments forever because it compares against
 * a bootstrap-only mirror instead of the previous frame.
 */
class ControlPump(
    private val transport: ClusterTransport,
    private val periodMs: Long = ClusterProtocol.CONTROL_PERIOD_MS,
) {
    private val _acks = MutableStateFlow(ControlAcks())
    val acks: StateFlow<ControlAcks> = _acks.asStateFlow()

    private var lastRaw: ByteArray? = null
    private var bootstrapped = false
    private var job: Job? = null

    fun start(scope: CoroutineScope): Job {
        stop()
        val supervisor = SupervisorJob(scope.coroutineContext[Job])
        val pumpScope = CoroutineScope(scope.coroutineContext + supervisor)

        transport.controlReads.onEach(::onRawRead).launchIn(pumpScope)
        pumpScope.launch {
            while (isActive) {
                delay(periodMs)
                transport.requestControlRead()
            }
        }

        job = supervisor
        return supervisor
    }

    /** A reconnect must repeat the CONTROL bootstrap in full - MTU and it do not survive a drop. */
    fun stop() {
        job?.cancel()
        job = null
        lastRaw = null
        bootstrapped = false
    }

    private fun onRawRead(raw: ByteArray) {
        val previousRaw = lastRaw
        if (previousRaw != null && raw.contentEquals(previousRaw)) return // whole-frame dedup
        lastRaw = raw

        val frame = ControlDecoder.decode(raw)
        if (!bootstrapped) {
            bootstrapped = true
            return
        }
        val previousFrame = ControlDecoder.decode(previousRaw ?: return)

        _acks.update { acks ->
            acks.copy(
                callAccept = mirror(frame.callAccept, previousFrame.callAccept, acks.callAccept),
                callReject = mirror(frame.callReject, previousFrame.callReject, acks.callReject),
                callRejectWithSms = mirror(
                    frame.callRejectWithSms,
                    previousFrame.callRejectWithSms,
                    acks.callRejectWithSms,
                ),
                skipToNext = mirror(frame.skipToNext, previousFrame.skipToNext, acks.skipToNext),
                skipToPrev = mirror(frame.skipToPrev, previousFrame.skipToPrev, acks.skipToPrev),
                missedCallGet = mirror(frame.missedCallGet, previousFrame.missedCallGet, acks.missedCallGet),
                alertGet = mirror(frame.alertGet, previousFrame.alertGet, acks.alertGet),
                resumeSong = counter(frame.resumeSong, previousFrame.resumeSong, acks.resumeSong),
                pauseSong = counter(frame.pauseSong, previousFrame.pauseSong, acks.pauseSong),
                stopSong = counter(frame.stopSong, previousFrame.stopSong, acks.stopSong),
                launchMediaPlayer = counter(
                    frame.launchMediaPlayer,
                    previousFrame.launchMediaPlayer,
                    acks.launchMediaPlayer,
                ),
            )
        }
    }

    private fun mirror(new: Int, previous: Int, current: Int): Int = if (new != previous) new else current

    private fun counter(new: Int, previous: Int, current: Int): Int =
        if (new != previous) (current + 1) % 256 else current
}
