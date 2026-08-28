package dev.jay.betterconnect.core.ble

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Context-registered only - never manifest-declared. The rider turning Bluetooth off
 * mid-ride should surface in the UI instead of looking like a silent scan failure, but
 * that is the only reason this exists; it costs no permission and no manifest surface.
 */
object BluetoothStateReceiver {

    fun state(context: Context): Flow<Int> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR,
                )
                trySend(state)
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
