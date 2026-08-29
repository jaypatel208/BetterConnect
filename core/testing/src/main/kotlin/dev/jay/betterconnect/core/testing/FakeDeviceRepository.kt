package dev.jay.betterconnect.core.testing

import dev.jay.betterconnect.core.link.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * An in-memory stand-in for the DataStore-backed repository, so a persisted-address
 * assertion is a plain state check rather than a race against real disk IO under a
 * virtual test dispatcher.
 */
class FakeDeviceRepository(initial: String? = null) : DeviceRepository {

    private val _lastAddress = MutableStateFlow(initial)
    override val lastAddress: StateFlow<String?> = _lastAddress

    override suspend fun setLastAddress(address: String) {
        _lastAddress.value = address
    }
}
