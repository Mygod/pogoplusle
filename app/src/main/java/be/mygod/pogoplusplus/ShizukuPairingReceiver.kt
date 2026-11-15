package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import be.mygod.pogoplusplus.util.ShizukuManager
import eu.darken.porter.sdk.isGranted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class ShizukuPairingReceiver : BroadcastReceiver() {
    companion object {
        /**
         * Bounds the wait for a cold-started process to receive the server's binder, leaving room for the
         * confirmation itself within the ordered broadcast's timeout.
         */
        private const val CONNECTION_TIMEOUT_MILLIS = 3_000L
    }

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
        if (directlyConfirmed) {
            abortBroadcast()
            GameNotificationService.onPrivilegedPairingSucceeded()
            return
        }

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val confirmed = try {
                    val connection = ShizukuManager.awaitConnection(CONNECTION_TIMEOUT_MILLIS)
                    if (connection?.checkPermission()?.isGranted != true) {
                        directFailure?.let(Timber::d)
                        false
                    } else ShizukuManager.canConfirmPairing(connection) &&
                            ShizukuManager.setPairingConfirmation(connection, device.first)
                } catch (e: Exception) {
                    directFailure?.let(e::addSuppressed)
                    Timber.w(e)
                    false
                }
                // Commit only after confirmation so every failure retains Android's normal pairing UI.
                if (confirmed) {
                    result.abortBroadcast()
                    GameNotificationService.onPrivilegedPairingSucceeded()
                } else GameNotificationService.onPrivilegedPairingFailed()
            } finally {
                result.finish()
            }
        }
    }
}
