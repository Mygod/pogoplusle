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
                if (SfidaManager.isAuxiliary(device)) {
                    GameNotificationService.onAuxiliaryConnected(device)
                    SfidaConnectionPriority.onConnected(device.first)
                } else SfidaSessionManager.onDisconnect(device)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val writes = SfidaSessionManager.writes
                SfidaConnectionPriority.onDisconnected(device.first)
                GameNotificationService.onAuxiliaryDisconnected(device)
                // the broadcast may have started the process, which can be killed before the session is saved;
                // connections are not held, see SfidaConnectionPriority
                if (SfidaSessionManager.writes != writes) {
                    val result = goAsync()
                    SfidaSessionManager.flush().invokeOnCompletion { result.finish() }
                }
            }
        }
    }
}
