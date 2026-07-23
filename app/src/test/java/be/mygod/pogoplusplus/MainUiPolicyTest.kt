package be.mygod.pogoplusplus

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiPolicyTest {
    @Test
    fun servicePairingAvailabilityFollowsSecurityPatchBoundary() {
        assertFalse(needsServicePairing(30, "2020-10-05"))
        assertTrue(needsServicePairing(30, "2020-11-01"))
        assertTrue(needsServicePairing(30, "2020"))
        assertTrue(needsServicePairing(30, ""))
        assertTrue(needsServicePairing(31, "2019-01-01"))
    }

    @Test
    fun bluetoothPermissionRequestAddsNotificationsOnAndroid13() {
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), bluetoothRuntimePermissions(32))
        assertEquals(listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS,
        ), bluetoothRuntimePermissions(33))
    }
}
