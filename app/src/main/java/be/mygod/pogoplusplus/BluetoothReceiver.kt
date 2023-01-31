package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import timber.log.Timber

class BluetoothReceiver : BroadcastReceiver() {
    companion object {
        private const val DEVICE_NAME_PBP = "Pokemon PBP"
        const val DEVICE_NAME_PGP = "Pokemon GO Plus"

        @SuppressLint("MissingPermission")
        fun getDevice(intent: Intent): Pair<BluetoothDevice, String>? {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return null
            val name = device.name
            val type = device.type
            val bluetoothClass = device.bluetoothClass
            val uuids = device.uuids
            val shouldSkip = type != BluetoothDevice.DEVICE_TYPE_LE || uuids != null ||
                    bluetoothClass?.hashCode() != BluetoothClass.Device.Major.UNCATEGORIZED ||
                    !device.address.startsWith("7C:BB:8A:", true) &&
                    !device.address.startsWith("98:B6:E9:", true) ||
                    name != DEVICE_NAME_PBP && name != DEVICE_NAME_PGP
            Timber.d("${intent.action}: ${device.address}, $name, $type, $bluetoothClass, $uuids, $shouldSkip")
            return if (shouldSkip) null else device to name
        }
    }

    override fun onReceive(context: Context?, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && intent.getIntExtra(
                BluetoothDevice.EXTRA_TRANSPORT,
                BluetoothDevice.ERROR) != BluetoothDevice.TRANSPORT_LE) return
        val device = getDevice(intent) ?: return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> GameNotificationService.onAuxiliaryConnected()
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> GameNotificationService.onAuxiliaryDisconnected(device.second)
        }
    }
}
