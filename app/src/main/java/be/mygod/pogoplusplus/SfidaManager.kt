package be.mygod.pogoplusplus

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService
import be.mygod.pogoplusplus.App.Companion.app
import timber.log.Timber
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

object SfidaManager : BluetoothGattCallback() {
    const val DEVICE_NAME_PGP = "Pokemon GO Plus"

    /**
     * Landroid/bluetooth/BluetoothDevice;->removeBond()Z,sdk,system-api,test-api
     * This private API is also called by the game so we are fine.
     */
    private val removeBond by lazy { BluetoothDevice::class.java.getDeclaredMethod("removeBond") }
    private fun removeBond(device: BluetoothDevice) = removeBond.invoke(device) as Boolean

    /**
     * Landroid/bluetooth/BluetoothGatt;->mClientIf:I,unsupported
     */
    private val mClientIf by lazy {
        BluetoothGatt::class.java.getDeclaredField("mClientIf").apply { isAccessible = true }
    }

    private val bluetooth by lazy { app.getSystemService<BluetoothManager>()!! }

    val isConnected get() = try {
        bluetooth.getConnectedDevices(BluetoothProfile.GATT).any { getDeviceName(it) != null }
    } catch (_: SecurityException) {
        null
    }
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun isConnected(device: BluetoothDevice) = bluetooth.getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun getDeviceName(device: BluetoothDevice, action: String? = null): Optional<String>? {
        val name = device.name
        val type = device.type
        val bluetoothClass = device.bluetoothClass
        val shouldSkip = type != BluetoothDevice.DEVICE_TYPE_LE ||
                bluetoothClass?.hashCode() != BluetoothClass.Device.Major.UNCATEGORIZED || when (name) {
            DEVICE_NAME_PGP, "Pokemon PBP", "Pokemon GO Plus +", "EbisuEbisu test" -> false
            null -> !device.address.startsWith("7C:BB:8A:", true) &&
                    !device.address.startsWith("98:B6:E9:", true) &&
                    !device.address.startsWith("B8:78:26:", true)
            else -> true
        }
        if (action != null) Timber.d("$action: ${device.address}, $name, $type, $bluetoothClass, $shouldSkip")
        return if (shouldSkip) null else Optional.ofNullable(name)
    }
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun getDevice(intent: Intent): Pair<BluetoothDevice, String?>? {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java) ?: return null
        val name = getDeviceName(device, intent.action) ?: return null
        return device to name.getOrNull()
    }

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
        if (bluetooth.adapter.bondedDevices?.contains(device) != false && removeBond(device) ||
            !isConnected(device)) return
        disconnectGatt(device)
    }
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectAll() {
        bluetooth.adapter.bondedDevices?.forEach { device -> getDeviceName(device) == null || removeBond(device) }
        for (device in bluetooth.getConnectedDevices(BluetoothProfile.GATT)) {
            if (getDeviceName(device) != null) disconnectGatt(device)
        }
    }
}
