package be.mygod.pogoplusplus

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat

class BluetoothDisconnectReceiver : BroadcastReceiver() {
    companion object {
        /**
         * This private API is also called by the game so we are fine.
         */
        private val removeBond by lazy { BluetoothDevice::class.java.getMethod("removeBond") }
    }

    override fun onReceive(context: Context?, intent: Intent) {
        removeBond(IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java) ?: return)
    }
}
