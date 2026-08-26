package dev.jay.betterconnect.core.link

import dev.jay.betterconnect.core.model.DeviceInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * Device discovery, behind an interface so the controller can be driven in tests without
 * a Bluetooth adapter.
 */
interface DeviceScanner {
    val results: StateFlow<List<DeviceInfo>>
    val scanning: StateFlow<Boolean>
    val bluetoothEnabled: Boolean

    fun start()
    fun stop()
    fun clear()
}
