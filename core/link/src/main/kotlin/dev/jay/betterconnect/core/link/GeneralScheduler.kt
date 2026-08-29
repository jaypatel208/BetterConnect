package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.model.ControlAcks
import dev.jay.betterconnect.core.model.GeneralState
import dev.jay.betterconnect.core.model.GeneralVersion
import dev.jay.betterconnect.core.protocol.ClusterProtocol
import dev.jay.betterconnect.core.protocol.GeneralEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The `GENERAL` (`0210`) heartbeat - A4/D1's other leading suspect for the 65 s disconnect.
 * Sent on a 1000 ms timer and immediately on start (connect / MTU change), rebuilt fresh at
 * transmission time so the heartbeat byte advances per actual send, not per enqueue.
 *
 * [version] defaults to V1 per `docs/PROTOCOL.md` §2's SKU-cohort inference, which the docs
 * mark **unconfirmed** (tracker D2) - exposed as a live-switchable flag rather than a
 * constructor constant so flipping to V2 on the bike is a toggle, not a rebuild.
 */
class GeneralScheduler(
    private val transport: ClusterTransport,
    private val acks: StateFlow<ControlAcks>,
    private val periodMs: Long = ClusterProtocol.GENERAL_PERIOD_MS,
) {
    private val _state = MutableStateFlow(GeneralState())
    val state: StateFlow<GeneralState> = _state.asStateFlow()

    private val _version = MutableStateFlow(GeneralVersion.V1)
    val version: StateFlow<GeneralVersion> = _version.asStateFlow()

    private var heartbeat = 0
    private var job: Job? = null

    fun setState(state: GeneralState) {
        _state.value = state
    }

    fun setVersion(version: GeneralVersion) {
        _version.value = version
    }

    fun start(scope: CoroutineScope): Job {
        stop()
        // UNDISPATCHED so the immediate on-start send has actually happened by the time
        // start() returns, rather than merely being scheduled.
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sendNow()
            while (isActive) {
                delay(periodMs)
                sendNow()
            }
        }.also { job = it }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun sendNow() {
        heartbeat = (heartbeat + 1) % 256
        val bytes = GeneralEncoder.encode(_state.value, acks.value, heartbeat, _version.value)
        transport.writeGeneral(bytes)
    }
}
