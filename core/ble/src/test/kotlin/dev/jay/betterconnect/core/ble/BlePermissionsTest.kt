package dev.jay.betterconnect.core.ble

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The permission model changed shape in Android 12. Both branches are exercised here
 * because getting this wrong fails at runtime on exactly the devices you are not holding.
 */
@RunWith(RobolectricTestRunner::class)
class BlePermissionsTest {

    @Test
    @Config(sdk = [31])
    fun `Android 12 and above uses the dedicated Bluetooth permissions`() {
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
            BlePermissions.required,
        )
        assertFalse(
            "location must not be requested on modern devices",
            Manifest.permission.ACCESS_FINE_LOCATION in BlePermissions.required,
        )
    }

    @Test
    @Config(sdk = [30])
    fun `Android 11 and below falls back to fine location`() {
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), BlePermissions.required)
    }

    @Test
    @Config(sdk = [26])
    fun `the minimum supported SDK still resolves a usable permission set`() {
        assertTrue(BlePermissions.required.isNotEmpty())
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), BlePermissions.required)
    }
}
