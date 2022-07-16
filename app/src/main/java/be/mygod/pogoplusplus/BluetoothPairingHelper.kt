package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresApi
import be.mygod.librootkotlinx.ParcelableBoolean
import be.mygod.librootkotlinx.RootCommand
import be.mygod.pogoplusplus.util.RootManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@RequiresApi(26)
class BluetoothPairingHelper : BroadcastReceiver() {
    @Parcelize
    class PairingConfirmationCommand(private val device: BluetoothDevice) : RootCommand<ParcelableBoolean> {
        @SuppressLint("MissingPermission")
        override suspend fun execute() = ParcelableBoolean(device.setPairingConfirmation(true))
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
        // check for PAIRING_VARIANT_CONSENT
        if (intent?.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR) != 3) return
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)!!
        val name = device.name
        val type = device.type
        val bluetoothClass = device.bluetoothClass
        val uuids = device.uuids
        val shouldSkip = type != BluetoothDevice.DEVICE_TYPE_LE || uuids != null ||
                bluetoothClass.hashCode() != BluetoothClass.Device.Major.UNCATEGORIZED ||
                !device.address.startsWith("98:B6:E9:", true) || name != "Pokemon GO Plus"
        Log.d("BluetoothPairingHelper", "${device.address}, $name, $type, $bluetoothClass, $uuids, $shouldSkip")
        if (shouldSkip) return
        abortBroadcast()    // stop system notification/popup
        try {
            device.setPairingConfirmation(true)
        } catch (se: SecurityException) {
            GlobalScope.launch {
                try {
                    RootManager.use { it.execute(PairingConfirmationCommand(device)) }
                } catch (e: Exception) {
                    e.addSuppressed(se)
                    Log.w("BluetoothPairingHelper", e)
                }
            }
        }
    }
}
