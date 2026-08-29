package dev.jay.betterconnect.core.testing

import dev.jay.betterconnect.core.link.DemoCapableTransport
import dev.jay.betterconnect.core.link.WriteOutcome
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.GattDump
import dev.jay.betterconnect.core.protocol.DecodeResult
import dev.jay.betterconnect.core.protocol.DecodedFrame
import dev.jay.betterconnect.core.protocol.DecodedGeneral
import dev.jay.betterconnect.core.protocol.GeneralDecoder
import dev.jay.betterconnect.core.protocol.TbtFrame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A cluster that lives in a test.
 *
 * It decodes every frame it is given, so assertions can be written against what the
 * cluster would actually have been told rather than against the bytes. Each fault below
 * is a real failure mode documented in CONNECTION.md, made reachable without hardware.
 */
class FakeClusterTransport(initialState: ConnectionState = ConnectionState.Ready(ADDRESS, DEFAULT_MTU)) :
    DemoCapableTransport {

    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _demoMode = MutableStateFlow(false)
    override val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    override fun setDemoMode(enabled: Boolean) {
        _demoMode.value = enabled
        if (enabled) _gattDump.value = TestData.healthyDump()
    }

    private val _gattDump = MutableStateFlow<GattDump?>(null)
    override val gattDump: StateFlow<GattDump?> = _gattDump.asStateFlow()

    private val _received = mutableListOf<TbtFrame>()

    /** Every frame the fake accepted, in order. */
    val received: List<TbtFrame> get() = _received.toList()

    /** The same frames decoded. Invalid frames are surfaced rather than silently skipped. */
    val decoded: List<DecodedFrame>
        get() = _received.mapNotNull { it.decode().frameOrNull }

    val lastDecoded: DecodedFrame? get() = decoded.lastOrNull()

    val badFrames: List<DecodeResult>
        get() = _received.map { it.decode() }.filter { it !is DecodeResult.Valid }

    private val _receivedGeneral = mutableListOf<ByteArray>()

    /** Every `GENERAL` frame the fake accepted, in order. */
    val receivedGeneral: List<ByteArray> get() = _receivedGeneral.toList()

    val decodedGeneral: List<DecodedGeneral> get() = _receivedGeneral.map(GeneralDecoder::decode)

    val lastGeneral: DecodedGeneral? get() = decodedGeneral.lastOrNull()

    private val _controlReads = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    override val controlReads: SharedFlow<ByteArray> = _controlReads

    private val controlReadQueue = ArrayDeque<ByteArray>()

    var connectCalls: Int = 0
        private set
    var disconnectCalls: Int = 0
        private set
    var controlReadRequests: Int = 0
        private set

    // ---- fault injection -------------------------------------------------------------

    /** When set, every write returns this outcome instead of being accepted. */
    var forcedOutcome: WriteOutcome? = null

    /**
     * When false, an accepted write stays in flight until [completeWrite] is called, so
     * subsequent writes come back BUSY. Models the one-GATT-operation-at-a-time rule -
     * shared across [write], [writeGeneral] and [requestControlRead], exactly as a real
     * GATT connection only ever has one operation outstanding regardless of characteristic.
     */
    var autoCompleteWrites: Boolean = true

    private var inFlight = false

    fun completeWrite() {
        inFlight = false
    }

    fun setState(state: ConnectionState) {
        _state.value = state
    }

    fun setGattDump(dump: GattDump?) {
        _gattDump.value = dump
    }

    fun clearReceived() = _received.clear()

    /** Queues bytes to be delivered on the next [requestControlRead]. FIFO. */
    fun enqueueControlRead(bytes: ByteArray) {
        controlReadQueue.addLast(bytes)
    }

    // ---- transport -------------------------------------------------------------------

    override fun connect(address: String) {
        connectCalls++
        _state.value = ConnectionState.Ready(address, DEFAULT_MTU)
    }

    override fun disconnect() {
        disconnectCalls++
        _state.value = ConnectionState.Disconnected(ADDRESS, status = 0)
    }

    override fun write(frame: TbtFrame): WriteOutcome {
        forcedOutcome?.let { return it }
        if (_state.value !is ConnectionState.Ready) return WriteOutcome.NOT_READY
        if (inFlight) return WriteOutcome.BUSY

        _received += frame
        if (!autoCompleteWrites) inFlight = true
        return WriteOutcome.ACCEPTED
    }

    override fun writeGeneral(bytes: ByteArray): WriteOutcome {
        forcedOutcome?.let { return it }
        if (_state.value !is ConnectionState.Ready) return WriteOutcome.NOT_READY
        if (inFlight) return WriteOutcome.BUSY

        _receivedGeneral += bytes
        if (!autoCompleteWrites) inFlight = true
        return WriteOutcome.ACCEPTED
    }

    override fun requestControlRead(): WriteOutcome {
        controlReadRequests++
        forcedOutcome?.let { return it }
        if (_state.value !is ConnectionState.Ready) return WriteOutcome.NOT_READY
        if (inFlight) return WriteOutcome.BUSY

        if (!autoCompleteWrites) inFlight = true
        controlReadQueue.removeFirstOrNull()?.let { _controlReads.tryEmit(it) }
        return WriteOutcome.ACCEPTED
    }

    companion object {
        const val ADDRESS = "AA:BB:CC:DD:EE:FF"
        const val DEFAULT_MTU = 64
    }
}
