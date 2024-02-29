package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BluetoothReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && intent.getIntExtra(BluetoothDevice.EXTRA_TRANSPORT,
                BluetoothDevice.ERROR) != BluetoothDevice.TRANSPORT_LE) return
        val device = SfidaManager.getDevice(intent) ?: return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                if (SfidaManager.isConnected(device.first, device.second ==
                            SfidaManager.DEVICE_NAME_PGP)) GameNotificationService.onAuxiliaryConnected(device)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> GameNotificationService.onAuxiliaryDisconnected(device)
        }
    }
}
