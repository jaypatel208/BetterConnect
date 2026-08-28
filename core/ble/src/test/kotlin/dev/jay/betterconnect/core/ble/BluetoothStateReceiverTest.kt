package dev.jay.betterconnect.core.ble

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Looper
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BluetoothStateReceiverTest {

    @Test
    fun `emits the adapter state carried by ACTION_STATE_CHANGED`() = runTest {
        val context = RuntimeEnvironment.getApplication()

        BluetoothStateReceiver.state(context).test {
            context.sendBroadcast(
                Intent(BluetoothAdapter.ACTION_STATE_CHANGED)
                    .putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF),
            )
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(BluetoothAdapter.STATE_OFF, awaitItem())

            context.sendBroadcast(
                Intent(BluetoothAdapter.ACTION_STATE_CHANGED)
                    .putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON),
            )
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(BluetoothAdapter.STATE_ON, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
