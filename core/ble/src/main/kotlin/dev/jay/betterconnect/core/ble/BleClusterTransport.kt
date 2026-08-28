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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    /** Observable side channel so the log can record link events verbatim. */
    private val _events = MutableStateFlow<LinkEvent?>(null)
    val events: StateFlow<LinkEvent?> = _events.asStateFlow()

    private val manager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var negotiatedMtu: Int = ClusterLink.UNKNOWN_MTU

    /** Only one GATT operation may be outstanding. Guards the drop-don't-queue rule. */
    @Volatile
    private var inFlight = false

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
        val target = characteristic ?: return WriteOutcome.NOT_READY
        if (_state.value !is ConnectionState.Ready) return WriteOutcome.NOT_READY
        if (inFlight) return WriteOutcome.BUSY

        inFlight = true
        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activeGatt.writeCharacteristic(
                target,
                frame.bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                target.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                target.value = frame.bytes
                activeGatt.writeCharacteristic(target)
            }
        }

        if (!accepted) {
            inFlight = false
            return WriteOutcome.FAILED
        }
        return WriteOutcome.ACCEPTED
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

            is LinkCommand.RequestMtu -> {
                val requested = gatt?.requestMtu(command.mtu) == true
                if (!requested) dispatch(LinkEvent.MtuRequestFailed)
            }

            LinkCommand.DiscoverServices -> {
                if (gatt?.discoverServices() != true) {
                    Log.w(TAG, "discoverServices() refused")
                }
            }

            LinkCommand.SendEndNavigation -> {
                inFlight = false
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
        characteristic = null
        inFlight = false
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
            characteristic = g.getService(ClusterProtocol.SERVICE_UUID)
                ?.getCharacteristic(ClusterProtocol.TBT_INFO_UUID)
            dispatch(LinkEvent.ServicesResolved(dump))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            inFlight = false
            if (status != BluetoothGatt.GATT_SUCCESS) Log.w(TAG, "write failed status=$status")
        }
    }

    companion object {
        private const val TAG = "NavBridge.Ble"
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
