package dev.jay.betterconnect.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "better_connect")

/**
 * Remembers the chosen cluster so a reconnect does not need another scan. The official app
 * does the same, keyed by chassis number; keyed by address is enough for one bike.
 */
@Singleton
class DeviceRepository @Inject constructor(@param:ApplicationContext private val context: Context) {
    val lastAddress: Flow<String?> =
        context.dataStore.data.map { it[KEY_LAST_ADDRESS] }

    suspend fun setLastAddress(address: String) {
        context.dataStore.edit { it[KEY_LAST_ADDRESS] = address }
    }

    private companion object {
        val KEY_LAST_ADDRESS = stringPreferencesKey("last_address")
    }
}
