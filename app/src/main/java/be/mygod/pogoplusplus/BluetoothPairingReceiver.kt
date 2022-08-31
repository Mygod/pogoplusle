package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresApi
import be.mygod.librootkotlinx.ParcelableBoolean
import be.mygod.librootkotlinx.RootCommand
import be.mygod.pogoplusplus.util.RootManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

@RequiresApi(26)
class BluetoothPairingReceiver : BroadcastReceiver() {
    @Parcelize
    class PairingConfirmationCommand(private val device: BluetoothDevice) : RootCommand<ParcelableBoolean> {
        @SuppressLint("MissingPermission")
        override suspend fun execute() = ParcelableBoolean(device.setPairingConfirmation(true))
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
        // check for PAIRING_VARIANT_CONSENT
        if (intent?.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR) != 3) return
        val device = BluetoothReceiver.getDevice(intent) ?: return
        abortBroadcast()    // stop system notification/popup
        try {
            device.first.setPairingConfirmation(true)
        } catch (se: SecurityException) {
            GlobalScope.launch {
                try {
                    RootManager.use { it.execute(PairingConfirmationCommand(device.first)) }
                } catch (e: Exception) {
                    e.addSuppressed(se)
                    Timber.w(e)
                }
            }
        }
    }
}
