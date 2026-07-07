package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import be.mygod.pogoplusplus.util.ShizukuManager
import timber.log.Timber

class BluetoothPairingReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
        // check for PAIRING_VARIANT_CONSENT
        if (intent?.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR) != 3) return
        val device = SfidaManager.getDevice(intent) ?: return
        abortBroadcast()    // stop system notification/popup
        try {
            if (device.first.setPairingConfirmation(true)) {
                GameNotificationService.onPrivilegedPairingSucceeded()
            } else {
                GameNotificationService.onPrivilegedPairingFailed()
            }
        } catch (se: SecurityException) {
            try {
                if (ShizukuManager.setPairingConfirmation(device.first)) {
                    GameNotificationService.onPrivilegedPairingSucceeded()
                } else {
                    GameNotificationService.onPrivilegedPairingFailed()
                }
            } catch (e: Exception) {
                e.addSuppressed(se)
                Timber.w(e)
                GameNotificationService.onPrivilegedPairingFailed()
            }
        }
    }
}
