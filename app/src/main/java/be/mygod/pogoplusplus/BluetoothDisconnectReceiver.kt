package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import be.mygod.pogoplusplus.App.Companion.app
import timber.log.Timber

class BluetoothDisconnectReceiver : BroadcastReceiver() {
    companion object : BluetoothGattCallback()  {
        /**
         * Landroid/bluetooth/BluetoothDevice;->removeBond()Z,sdk,system-api,test-api
         * This private API is also called by the game so we are fine.
         */
        private val removeBond by lazy { BluetoothDevice::class.java.getDeclaredMethod("removeBond") }

        /**
         * Landroid/bluetooth/BluetoothGatt;->mClientIf:I,unsupported
         */
        private val mClientIf by lazy {
            BluetoothGatt::class.java.getDeclaredField("mClientIf").apply { isAccessible = true }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent) {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java) ?: return
        if (app.bluetooth.getConnectionState(device, BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTED ||
            app.bluetooth.adapter.bondedDevices?.contains(device) != false &&
            removeBond(device) as? Boolean == true) return
        val gatt = device.connectGatt(app, false, BluetoothDisconnectReceiver)
        try {
            for (i in 1..32) {  // https://cs.android.com/android/platform/superproject/+/master:packages/modules/Bluetooth/system/internal_include/bt_target.h;l=525;drc=a786e24777988f3207b90fdb5eb00bc68b540691
                mClientIf.setInt(gatt, i)
                gatt.disconnect()
            }
        } catch (e: Exception) {
            Timber.w(e)
        } finally {
            gatt.close()
        }
    }
}
