package dev.jay.betterconnect.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jay.betterconnect.core.link.DeviceScanner
import dev.jay.betterconnect.core.model.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unfiltered BLE scan.
 *
 * Deliberately no ScanFilter on the service UUID: there is no evidence the cluster
 * advertises it, and a filtered scan that finds nothing is indistinguishable from a
 * cluster that is switched off. The official app matches on advertised name alone, so
 * we surface everything and mark the likely candidates.
 */
@Singleton
@SuppressLint("MissingPermission")
class BleScanner @Inject constructor(@param:ApplicationContext private val context: Context) :
    DeviceScanner {

    private val manager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter? get() = manager?.adapter

    private val _results = MutableStateFlow<List<DeviceInfo>>(emptyList())
    override val results: StateFlow<List<DeviceInfo>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    override val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    override val bluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = record(result)

        override fun onBatchScanResults(results: List<ScanResult>) = results.forEach(::record)

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed, errorCode=$errorCode")
            _scanning.value = false
        }
    }

    override fun start() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (_scanning.value) return

        _results.value = bondedDevices()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(emptyList(), settings, callback)
        _scanning.value = true
    }

    override fun stop() {
        if (!_scanning.value) return
        adapter?.bluetoothLeScanner?.stopScan(callback)
        _scanning.value = false
    }

    override fun clear() {
        _results.value = bondedDevices()
    }

    /**
     * The cluster is usually already bonded because the official app has paired with it,
     * so bonded devices are worth listing even before a scan produces anything.
     */
    private fun bondedDevices(): List<DeviceInfo> = runCatching {
        adapter?.bondedDevices.orEmpty().map { device ->
            DeviceInfo(
                address = device.address,
                name = device.name,
                rssi = DeviceInfo.RSSI_UNKNOWN,
                bonded = true,
                connectable = true,
            )
        }
    }.getOrDefault(emptyList())

    private fun record(result: ScanResult) {
        val record = result.scanRecord
        val info = DeviceInfo(
            address = result.device.address,
            name = record?.deviceName ?: runCatching { result.device.name }.getOrNull(),
            rssi = result.rssi,
            bonded = runCatching { result.device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED }
                .getOrDefault(false),
            connectable = result.isConnectable,
            serviceUuids = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty(),
            manufacturerData = record?.manufacturerSpecificData?.let { data ->
                if (data.size() == 0) {
                    null
                } else {
                    buildString {
                        for (i in 0 until data.size()) {
                            append("0x%04X:".format(data.keyAt(i)))
                            append(data.valueAt(i).joinToString("") { "%02X".format(it) })
                            if (i < data.size() - 1) append(' ')
                        }
                    }
                }
            },
        )

        _results.update { current ->
            // Replace rather than append: adverts repeat, and RSSI should track the latest.
            (current.filterNot { it.address == info.address } + info)
                .sortedWith(compareByDescending<DeviceInfo> { it.isCandidate }.thenByDescending { it.rssi })
        }
    }

    companion object {
        private const val TAG = "NavBridge.Scan"
    }
}
