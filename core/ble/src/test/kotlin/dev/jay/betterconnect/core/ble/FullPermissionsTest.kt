package dev.jay.betterconnect.core.ble

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class FullPermissionsTest {

    @Test
    @Config(sdk = [34])
    fun `required set on a modern device includes Bluetooth, location and notifications`() {
        val required = FullPermissions.required
        assertTrue(Manifest.permission.BLUETOOTH_SCAN in required)
        assertTrue(Manifest.permission.BLUETOOTH_CONNECT in required)
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in required)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in required)
    }

    @Test
    @Config(sdk = [26])
    fun `required set on the minimum SDK has no Bluetooth-12 or notification permissions`() {
        val required = FullPermissions.required
        assertFalse(Manifest.permission.BLUETOOTH_SCAN in required)
        assertFalse(Manifest.permission.BLUETOOTH_CONNECT in required)
        assertFalse(Manifest.permission.POST_NOTIFICATIONS in required)
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in required)
    }

    @Test
    @Config(sdk = [34])
    fun `enhanced set never includes a permission Play blocks for a non-default handler`() {
        val enhanced = FullPermissions.enhanced
        assertFalse(Manifest.permission.READ_SMS in enhanced)
        assertFalse(Manifest.permission.READ_CALL_LOG in enhanced)
        assertTrue(Manifest.permission.READ_PHONE_STATE in enhanced)
        assertTrue(Manifest.permission.READ_CONTACTS in enhanced)
    }
}
