package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.IntentCompat
import timber.log.Timber

class BluetoothReceiver : BroadcastReceiver() {
    companion object {
        private const val DEVICE_NAME_PBP = "Pokemon PBP"
        const val DEVICE_NAME_PGP = "Pokemon GO Plus"

        @SuppressLint("MissingPermission")
        fun getDeviceName(device: BluetoothDevice, action: String? = null): String? {
            val name = device.name
            val type = device.type
            val bluetoothClass = device.bluetoothClass
            val shouldSkip = type != BluetoothDevice.DEVICE_TYPE_LE ||
                    bluetoothClass?.hashCode() != BluetoothClass.Device.Major.UNCATEGORIZED ||
                    name != DEVICE_NAME_PBP && name != DEVICE_NAME_PGP ||
                    !device.address.startsWith("7C:BB:8A:", true) &&
                    !device.address.startsWith("98:B6:E9:", true)
            if (action != null) Timber.d("$action: ${device.address}, $name, $type, $bluetoothClass, $shouldSkip")
            return if (shouldSkip) null else name
        }
        fun getDevice(intent: Intent): Pair<BluetoothDevice, String>? {
            val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java) ?: return null
            val name = getDeviceName(device, intent.action) ?: return null
            return device to name
        }
    }

    override fun onReceive(context: Context?, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && intent.getIntExtra(
                BluetoothDevice.EXTRA_TRANSPORT,
                BluetoothDevice.ERROR) != BluetoothDevice.TRANSPORT_LE) return
        val device = getDevice(intent) ?: return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> GameNotificationService.onAuxiliaryConnected(
                device.first, device.second)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> GameNotificationService.onAuxiliaryDisconnected(device.second)
        }
    }
}
