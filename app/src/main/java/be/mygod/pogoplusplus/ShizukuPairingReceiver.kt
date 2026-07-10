package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import be.mygod.pogoplusplus.util.ShizukuManager
import timber.log.Timber

class ShizukuPairingReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != BluetoothDevice.ACTION_PAIRING_REQUEST) return
        // check for PAIRING_VARIANT_CONSENT
        if (intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR) != 3) return
        val device = SfidaManager.getDevice(intent) ?: return

        var directFailure: SecurityException? = null
        val directlyConfirmed = try {
            device.first.setPairingConfirmation(true)
        } catch (e: SecurityException) {
            directFailure = e
            false
        }
        val confirmed = if (directlyConfirmed) true else if (!ShizukuManager.isAuthorized()) {
            directFailure?.let(Timber::d)
            false
        } else try {
            ShizukuManager.setPairingConfirmation(device.first)
        } catch (e: Exception) {
            directFailure?.let(e::addSuppressed)
            Timber.w(e)
            false
        }

        if (confirmed) {
            // Commit only after confirmation so every failure retains Android's normal pairing UI.
            abortBroadcast()
            GameNotificationService.onPrivilegedPairingSucceeded()
        } else GameNotificationService.onPrivilegedPairingFailed()
    }
}
