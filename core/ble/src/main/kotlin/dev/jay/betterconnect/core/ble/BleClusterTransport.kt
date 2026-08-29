package dev.jay.betterconnect.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jay.betterconnect.core.link.ClusterLink
import dev.jay.betterconnect.core.link.ClusterTransport
import dev.jay.betterconnect.core.link.LinkCommand
import dev.jay.betterconnect.core.link.LinkEvent
import dev.jay.betterconnect.core.link.WriteOutcome
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.GattCharacteristic
import dev.jay.betterconnect.core.model.GattDump
import dev.jay.betterconnect.core.model.GattService
import dev.jay.betterconnect.core.protocol.ClusterProtocol
import dev.jay.betterconnect.core.protocol.TbtFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only class that touches android.bluetooth.
 *
 * It is deliberately dumb: callbacks become [LinkEvent]s, [ClusterLink] decides what the
 * state is and what should happen next, and this class only carries out the resulting
 * [LinkCommand]s. Everything worth testing therefore lives in a pure module.
 */
@Singleton
@SuppressLint("MissingPermission")
class BleClusterTransport @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scope: CoroutineScope,
) : ClusterTransport {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _gattDump = MutableStateFlow<GattDump?>(null)
    override val gattDump: StateFlow<GattDump?> = _gattDump.asStateFlow()

    private val _controlReads = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    override val controlReads: SharedFlow<ByteArray> = _controlReads

    /** Observable side channel so the log can record link events verbatim. */
    private val _events = MutableStateFlow<LinkEvent?>(null)
    val events: StateFlow<LinkEvent?> = _events.asStateFlow()

    private val manager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private var gatt: BluetoothGatt? = null
    private var tbtCharacteristic: BluetoothGattCharacteristic? = null
    private var generalCharacteristic: BluetoothGattCharacteristic? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null
    private var negotiatedMtu: Int = ClusterLink.UNKNOWN_MTU

    /**
     * One GATT operation outstanding at a time, across all three characteristics - a BLE
     * connection has exactly one in-flight operation regardless of which characteristic it
     * targets. `tryLock()` gives the drop-don't-queue behaviour non-suspending callers need;
     * the watchdog coroutine launched alongside every successful lock releases it even if
     * the expected callback never fires, which a bare boolean flag cannot do on its own.
     */
    private val gattMutex = Mutex()
    private var pendingCompletion: CompletableDeferred<Unit>? = null

    /**
     * Identity of the [pendingCompletion] the current lock belongs to. A watchdog that
     * times out only unlocks if this is still *its own* completion - otherwise the lock
     * has already been released (and possibly re-acquired by a newer operation) and
     * touching it again would release someone else's lock instead of its own.
     */
    private var lockOwner: CompletableDeferred<Unit>? = null

    private var userWantsConnection = false

    override fun connect(address: String) {
        userWantsConnection = true
        dispatch(LinkEvent.ConnectRequested(address))
    }

    override fun disconnect() {
        userWantsConnection = false
        dispatch(LinkEvent.DisconnectRequested)
    }

    override fun write(frame: TbtFrame): WriteOutcome {
        val activeGatt = gatt ?: return WriteOutcome.NOT_READY
        val target = tbtCharacteristic ?: return WriteOutcome.NOT_READY
        if (_state.value !is ConnectionState.Ready) return WriteOutcome.NOT_READY
        return performWrite(activeGatt, target, frame.bytes)
    }

    override fun writeGeneral(bytes: ByteArray): WriteOutcome {
        val activeGatt = gatt ?: return WriteOutcome.NOT_READY
        val target = generalCharacteristic ?: return WriteOutcome.NOT_READY
        if (_state.value !is ConnectionState.Ready) return WriteOutcome.NOT_READY
        return performWrite(activeGatt, target, bytes)
    }

    override fun requestControlRead(): WriteOutcome {
        val activeGatt = gatt ?: return WriteOutcome.NOT_READY
        val target = controlCharacteristic ?: return WriteOutcome.NOT_READY
        if (_state.value !is ConnectionState.Ready) return WriteOutcome.NOT_READY
        if (!gattMutex.tryLock()) return WriteOutcome.BUSY

        val completion = CompletableDeferred<Unit>()
        pendingCompletion = completion
        lockOwner = completion
        armWatchdog(completion)

        val accepted = activeGatt.readCharacteristic(target)
        if (!accepted) {
            completeOperation()
            return WriteOutcome.FAILED
        }
        return WriteOutcome.ACCEPTED
    }

    private fun performWrite(
        activeGatt: BluetoothGatt,
        target: BluetoothGattCharacteristic,
        bytes: ByteArray,
    ): WriteOutcome {
        if (!gattMutex.tryLock()) return WriteOutcome.BUSY

        val completion = CompletableDeferred<Unit>()
        pendingCompletion = completion
        lockOwner = completion
        armWatchdog(completion)

        // Never hardcode the write type (A2/C1): derive it from the characteristic's own
        // advertised properties. Every writable characteristic on this cluster advertises
        // WRITE and not WRITE_NO_RESPONSE, so Write Request is correct - a Write Command
        // sent to a WRITE-only characteristic has undefined handling on the peripheral
        // side. CONNECTION.md §4.
        val writeType = target.writeTypeFor()
        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activeGatt.writeCharacteristic(target, bytes, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                target.writeType = writeType
                target.value = bytes
                activeGatt.writeCharacteristic(target)
            }
        }

        if (!accepted) {
            completeOperation()
            return WriteOutcome.FAILED
        }
        return WriteOutcome.ACCEPTED
    }

    /**
     * Falls back to releasing [gattMutex] if `onCharacteristicWrite`/`onCharacteristicRead`
     * never fires - the exact failure the bare `@Volatile inFlight` flag this replaces could
     * not recover from, which would strand every future write BUSY forever. Guarded by
     * [lockOwner] so a watchdog that fires after [completeOperation] already released (and
     * something else already re-acquired) the lock never touches it a second time.
     */
    private fun armWatchdog(completion: CompletableDeferred<Unit>) {
        scope.launch {
            val finished = withTimeoutOrNull(GATT_OP_TIMEOUT_MS) { completion.await() }
            if (finished == null) {
                Log.w(TAG, "GATT operation timed out after ${GATT_OP_TIMEOUT_MS}ms")
                unlockIfOwner(completion)
            }
        }
    }

    /**
     * Signals the armed watchdog that the operation finished and releases the lock right
     * away, rather than waiting for the watchdog coroutine to be scheduled - callers such
     * as `SendEndNavigation` issue a follow-up write in the same call stack and need the
     * lock free synchronously, not on the next dispatch.
     */
    private fun completeOperation() {
        val completion = pendingCompletion
        pendingCompletion = null
        completion?.complete(Unit)
        unlockIfOwner(completion)
    }

    private fun unlockIfOwner(completion: CompletableDeferred<Unit>?) {
        if (completion != null && lockOwner === completion) {
            lockOwner = null
            if (gattMutex.isLocked) gattMutex.unlock()
        }
    }

    // ---- reducer plumbing ------------------------------------------------------------

    private fun dispatch(event: LinkEvent) {
        _events.value = event
        val transition = ClusterLink.reduce(_state.value, event)
        _state.value = transition.state
        transition.commands.forEach(::execute)
    }

    private fun execute(command: LinkCommand) {
        when (command) {
            is LinkCommand.Connect -> openGatt(command.address)

            LinkCommand.RequestConnectionPriority -> {
                // Never HIGH: the vendor's own code documents that HIGH renegotiation on
                // API 35+ causes a status=8 disconnect loop. CONNECTION.md §7.
                gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
            }

            is LinkCommand.DiscoverServices -> scope.launch {
                if (command.delayMs > 0) delay(command.delayMs)
                if (gatt?.discoverServices() != true) {
                    Log.w(TAG, "discoverServices() refused")
                }
            }

            is LinkCommand.RequestMtu -> {
                val requested = gatt?.requestMtu(command.mtu) == true
                if (!requested) dispatch(LinkEvent.MtuRequestFailed)
            }

            LinkCommand.SendEndNavigation -> {
                completeOperation()
                write(TbtFrame.endNavigation())
            }

            LinkCommand.Close -> closeGatt()

            is LinkCommand.ScheduleReconnect -> scope.launch {
                delay(command.delayMs)
                if (userWantsConnection) openGatt(command.address)
            }
        }
    }

    private fun openGatt(address: String) {
        closeGatt()
        val device = runCatching { manager?.adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            Log.w(TAG, "unknown device $address")
            return
        }
        // autoConnect = false: we want a direct connection attempt with a fast failure,
        // not an opportunistic background one. TRANSPORT_LE is explicit because a dual-mode
        // cluster would otherwise be reachable over BR/EDR, where this service does not exist.
        @Suppress("DEPRECATION")
        gatt = device.connectGatt(
            context,
            false,
            callback,
            android.bluetooth.BluetoothDevice.TRANSPORT_LE,
        )
    }

    private fun closeGatt() {
        tbtCharacteristic = null
        generalCharacteristic = null
        controlCharacteristic = null
        completeOperation()
        gatt?.runCatching { disconnect() }
        gatt?.runCatching { close() }
        gatt = null
    }

    // ---- callbacks: translate to events, decide nothing --------------------------------

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> dispatch(LinkEvent.Connected(g.device.address))
                BluetoothProfile.STATE_DISCONNECTED -> dispatch(LinkEvent.Disconnected(status))
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            negotiatedMtu = mtu
            dispatch(LinkEvent.MtuNegotiated(mtu))
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val dump = g.toDump(negotiatedMtu)
            _gattDump.value = dump
            val service = g.getService(ClusterProtocol.SERVICE_UUID)
            tbtCharacteristic = service?.getCharacteristic(ClusterProtocol.TBT_INFO_UUID)
            generalCharacteristic = service?.getCharacteristic(ClusterProtocol.GENERAL_UUID)
            controlCharacteristic = service?.getCharacteristic(ClusterProtocol.ACTION_UUID)
            dispatch(LinkEvent.ServicesResolved(dump))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            completeOperation()
            if (status != BluetoothGatt.GATT_SUCCESS) Log.w(TAG, "write failed status=$status")
        }

        // Only actually invoked below API 33; the ByteArray overload below takes over on
        // API 33+ per the platform's own dispatch rule for this callback pair.
        @Suppress("DEPRECATION")
        @Deprecated("Superseded by the (gatt, characteristic, value, status) overload on API 33+")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val bytes = c.value
            onControlReadCompleted(status, bytes)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            onControlReadCompleted(status, value)
        }

        private fun onControlReadCompleted(status: Int, bytes: ByteArray?) {
            completeOperation()
            if (status == BluetoothGatt.GATT_SUCCESS && bytes != null) {
                _controlReads.tryEmit(bytes)
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "CONTROL read failed status=$status")
            }
        }
    }

    companion object {
        private const val TAG = "NavBridge.Ble"

        /**
         * Bounds how long a write/read is allowed to stay "in flight" with no callback.
         * Without this a single stalled callback would strand the shared GATT lock and
         * every future operation would return BUSY forever.
         */
        private const val GATT_OP_TIMEOUT_MS = 5_000L
    }
}

/** Write Request for a WRITE-only characteristic, Write Command otherwise. CONNECTION.md §4. */
private fun BluetoothGattCharacteristic.writeTypeFor(): Int {
    val writable = properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
    val writeNoResponseOnly = properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 &&
        !writable
    return if (writeNoResponseOnly) {
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    } else {
        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    }
}

/** Flattens the discovered table. This replaces needing a separate BLE explorer app. */
@SuppressLint("MissingPermission")
private fun BluetoothGatt.toDump(mtu: Int): GattDump = GattDump(
    address = device.address,
    mtu = mtu,
    services = services.map { service ->
        GattService(
            uuid = service.uuid.toString(),
            characteristics = service.characteristics.map { characteristic ->
                GattCharacteristic(
                    uuid = characteristic.uuid.toString(),
                    properties = characteristic.properties.describeProperties(),
                    isTbtInfo = characteristic.uuid == ClusterProtocol.TBT_INFO_UUID,
                )
            },
        )
    },
)

private fun Int.describeProperties(): List<String> = buildList {
    if (this@describeProperties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
    if (this@describeProperties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
    if (this@describeProperties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE !=
        0
    ) {
        add("WRITE_NO_RESPONSE")
    }
    if (this@describeProperties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
    if (this@describeProperties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
    if (this@describeProperties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) add("BROADCAST")
    if (this@describeProperties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE !=
        0
    ) {
        add("SIGNED_WRITE")
    }
    if (this@describeProperties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) add("EXTENDED")
}
