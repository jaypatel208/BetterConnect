package dev.jay.betterconnect.core.link

import kotlinx.coroutines.flow.Flow

/**
 * Remembers the last-connected cluster address, behind an interface for the same reason as
 * [DeviceScanner]: the real implementation touches Android (DataStore); tests and demo mode
 * use an in-memory fake instead.
 */
interface DeviceRepository {
    val lastAddress: Flow<String?>
    suspend fun setLastAddress(address: String)
}
