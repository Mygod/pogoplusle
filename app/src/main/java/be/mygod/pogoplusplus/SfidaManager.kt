package be.mygod.pogoplusplus

import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService
import be.mygod.pogoplusplus.App.Companion.app
import timber.log.Timber

object SfidaManager : BluetoothGattCallback() {
    private const val DEVICE_NAME_PBP = "Pokemon PBP"
    const val DEVICE_NAME_PGP = "Pokemon GO Plus"

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

    private val alarm by lazy { app.getSystemService<AlarmManager>()!! }
    private val bluetooth by lazy { app.getSystemService<BluetoothManager>()!! }

    val isConnected get() = try {
        bluetooth.getConnectedDevices(BluetoothProfile.GATT).any { getDeviceName(it) != null }
    } catch (_: SecurityException) {
        null
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun getDeviceName(device: BluetoothDevice, action: String? = null): String? {
        val name = device.name
        val type = device.type
        val bluetoothClass = device.bluetoothClass
        val shouldSkip = type != BluetoothDevice.DEVICE_TYPE_LE ||
                bluetoothClass?.hashCode() != BluetoothClass.Device.Major.UNCATEGORIZED ||
                name != DEVICE_NAME_PBP && name != DEVICE_NAME_PGP ||
                !device.address.startsWith("7C:BB:8A:", true) &&
                !device.address.startsWith("98:B6:E9:", true) &&
                !device.address.startsWith("B8:78:26:", true)
        if (action != null) Timber.d("$action: ${device.address}, $name, $type, $bluetoothClass, $shouldSkip")
        return if (shouldSkip) null else name
    }
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun getDevice(intent: Intent): Pair<BluetoothDevice, String>? {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java) ?: return null
        val name = getDeviceName(device, intent.action) ?: return null
        return device to name
    }

    private val timeoutIntent by lazy {
        PendingIntent.getBroadcast(app, 0, Intent(app, SfidaTimeoutReceiver::class.java), PendingIntent.FLAG_MUTABLE)
    }
    // 1h 12.457s - 1h 11m 6.778s
    fun reportConnection() = alarm.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP,
        SystemClock.elapsedRealtime() + 3612457, 654321, timeoutIntent)
    fun reportDisconnection() = alarm.cancel(timeoutIntent)

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun disconnectGatt(device: BluetoothDevice) {
        val gatt = device.connectGatt(app, false, SfidaManager)
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
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect(device: BluetoothDevice) {
        if (bluetooth.getConnectionState(device, BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTED ||
            bluetooth.adapter.bondedDevices?.contains(device) != false && removeBond(device) as Boolean) return
        disconnectGatt(device)
    }
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectAll() {
        val bonded by lazy { bluetooth.adapter.bondedDevices?.toSet() }
        for (device in bluetooth.getConnectedDevices(BluetoothProfile.GATT)) {
            if (getDeviceName(device) == null ||
                bonded?.contains(device) != false && removeBond(device) as Boolean) continue
            disconnectGatt(device)
        }
    }
}
