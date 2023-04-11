package be.mygod.pogoplusplus

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.IntentCompat
import timber.log.Timber

class SfidaDisconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent) {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        try {
            if (device == null) SfidaManager.disconnectAll() else SfidaManager.disconnect(device)
        } catch (e: SecurityException) {
            Timber.d(e)
            Toast.makeText(context, e.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }
}
