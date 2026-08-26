package dev.jay.betterconnect.core.testing

import dev.jay.betterconnect.core.link.DeviceScanner
import dev.jay.betterconnect.core.model.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeDeviceScanner : DeviceScanner {

    private val _results = MutableStateFlow<List<DeviceInfo>>(emptyList())
    override val results: StateFlow<List<DeviceInfo>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    override val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    override var bluetoothEnabled: Boolean = true

    var startCalls = 0
        private set
    var stopCalls = 0
        private set

    override fun start() {
        startCalls++
        _scanning.value = true
    }

    override fun stop() {
        stopCalls++
        _scanning.value = false
    }

    override fun clear() {
        _results.value = emptyList()
    }

    fun emit(devices: List<DeviceInfo>) {
        _results.value = devices
    }

    companion object {
        val cluster = DeviceInfo(
            address = FakeClusterTransport.ADDRESS,
            name = "PULSAR N160",
            rssi = -55,
            bonded = true,
            connectable = true,
        )
        val unrelated = DeviceInfo(
            address = "11:22:33:44:55:66",
            name = "Some Earbuds",
            rssi = -80,
            bonded = false,
            connectable = true,
        )
    }
}
