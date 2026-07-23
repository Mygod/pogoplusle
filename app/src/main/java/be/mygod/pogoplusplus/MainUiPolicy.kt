package be.mygod.pogoplusplus

import android.Manifest
import android.annotation.SuppressLint

internal fun needsServicePairing(sdk: Int, securityPatch: String): Boolean {
    if (sdk >= 31) return true
    val parts = securityPatch.split('-', limit = 3)
    val year = parts.getOrNull(0)?.toIntOrNull()
    val month = parts.getOrNull(1)?.toIntOrNull()
    return year == null || year > 2020 || year == 2020 && (month == null || month >= 11)
}

@SuppressLint("InlinedApi")
internal fun bluetoothRuntimePermissions(sdk: Int) = if (sdk >= 33) listOf(
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.POST_NOTIFICATIONS,
) else listOf(Manifest.permission.BLUETOOTH_CONNECT)
