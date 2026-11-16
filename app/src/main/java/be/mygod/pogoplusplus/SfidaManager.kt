package be.mygod.pogoplusplus

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService
import be.mygod.pogoplusplus.App.Companion.app
import timber.log.Timber
import java.lang.reflect.InvocationTargetException
import java.util.Optional
import java.util.concurrent.Executor
import kotlin.jvm.optionals.getOrNull

object SfidaManager : BluetoothGattCallback() {
    const val DEVICE_NAME_PGP = "Pokemon GO Plus"
    const val DEVICE_NAME_PGPP = "Pokemon GO Plus +"

    /**
     * Landroid/bluetooth/BluetoothDevice;->removeBond()Z,sdk,system-api,test-api
     * Used as a fallback when there is no app-owned companion association. Android 17 requires
     * BLUETOOTH_PRIVILEGED for this direct call, so ordinary installations use CompanionDeviceManager instead.
     */
    private val removeBond by lazy { BluetoothDevice::class.java.getDeclaredMethod("removeBond") }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun removeBond(device: BluetoothDevice): Boolean {
        if (Build.VERSION.SDK_INT >= 36 && companionDeviceSetupSupported) {
            findCompanionAssociation(device)?.let { if (companionDeviceManager.removeBond(it.id)) return true }
        }
        return try {
            removeBond.invoke(device) as Boolean
        } catch (e: InvocationTargetException) {
            val cause = e.cause ?: throw e
            if (Build.VERSION.SDK_INT >= 37 && cause is SecurityException) {
                // Expected without a companion association: the Unpair action guides users to create one instead.
                Timber.d(cause, "Direct Bluetooth bond removal is unavailable")
                false
            } else throw cause
        }
    }

    @RequiresApi(36)
    private fun findCompanionAssociation(device: BluetoothDevice) = companionDeviceManager.myAssociations.firstOrNull {
        !it.isSelfManaged && it.deviceMacAddress?.toString().equals(device.address, true)
    }

    /** Whether [device] can be unpaired through an app-owned companion association. */
    fun hasCompanionAssociation(device: BluetoothDevice) = Build.VERSION.SDK_INT >= 36 &&
            companionDeviceSetupSupported && findCompanionAssociation(device) != null

    /**
     * (API 28-36) Landroid/bluetooth/BluetoothGatt;->mClientIf:I,unsupported
     * Android 16 Bluetooth module updates removed it, so it can be missing on API 36:
     * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android-16.0.0_r2/framework/java/android/bluetooth/BluetoothGatt.java#70
     * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android-16.0.0_r4/framework/java/android/bluetooth/BluetoothGatt.java
     */
    private val mClientIf by lazy {
        BluetoothGatt::class.java.getDeclaredField("mClientIf").apply { isAccessible = true }
    }

    private val bluetooth by lazy { app.getSystemService<BluetoothManager>()!! }
    private val companionDeviceManager by lazy { app.getSystemService<CompanionDeviceManager>()!! }

    val companionDeviceSetupSupported get() = Build.VERSION.SDK_INT >= 36 &&
            app.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)

    @get:RequiresApi(36)
    val companionAssociationCount get() = companionDeviceManager.myAssociations.count {
        !it.isSelfManaged && it.deviceMacAddress != null
    }

    @RequiresApi(36)
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun associateCompanion(executor: Executor, callback: CompanionDeviceManager.Callback,
                           device: BluetoothDevice? = null): Boolean {
        val associations = companionDeviceManager.myAssociations
        fun isAssociated(device: BluetoothDevice) = associations.any {
            !it.isSelfManaged && it.deviceMacAddress?.toString().equals(device.address, true)
        }
        val candidate = (if (device != null) device.takeUnless(::isAssociated) else {
            bluetooth.adapter.bondedDevices.orEmpty().firstOrNull { getDeviceName(it) != null && !isAssociated(it) }
        }) ?: return false
        // Single-device requests can retroactively find an existing bond without rediscovering the accessory.
        val request = AssociationRequest.Builder().setSingleDevice(true)
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(candidate.address).build())
            .build()
        companionDeviceManager.associate(request, executor, callback)
        return true
    }

    val isConnected get() = try {
        bluetooth.getConnectedDevices(BluetoothProfile.GATT).any { getDeviceName(it) != null }
    } catch (_: SecurityException) {
        null
    }

    /** Whether a connection of [device] counts as an accessory connection, as [BluetoothReceiver] treats it. */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun isAuxiliary(device: Pair<BluetoothDevice, String?>) =
        device.second != DEVICE_NAME_PGP || device.first.bondState != BluetoothDevice.BOND_NONE

    @get:RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    val connectedAuxiliaries get() = bluetooth.getConnectedDevices(BluetoothProfile.GATT).filter { device ->
        getDeviceName(device)?.let { isAuxiliary(device to it.getOrNull()) } == true
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun getDeviceName(device: BluetoothDevice, action: String? = null): Optional<String>? {
        val name = device.name
        val type = device.type
        val shouldSkip = type != BluetoothDevice.DEVICE_TYPE_UNKNOWN &&
                type != BluetoothDevice.DEVICE_TYPE_LE || when (name) {
            DEVICE_NAME_PGP, DEVICE_NAME_PGPP, "Pokemon PBP", "EbisuEbisu test" -> false
            null -> !device.address.startsWith("7C:BB:8A:", true) &&
                    !device.address.startsWith("98:B6:E9:", true) &&
                    !device.address.startsWith("B8:78:26:", true)
            else -> true
        }
        if (action != null) Timber.d("$action: ${device.address}, $name, $type, $shouldSkip")
        return if (shouldSkip) null else Optional.ofNullable(name)
    }
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun getDevice(intent: Intent): Pair<BluetoothDevice, String?>? {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java) ?: return null
        val name = getDeviceName(device, intent.action) ?: return null
        return device to name.getOrNull()
    }

    /** Only called on API 28-36. */
    @Suppress("DEPRECATION")
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun disconnectGatt(device: BluetoothDevice): Boolean {
        val gatt = device.connectGatt(app, false, SfidaManager) ?: return false
        try {
            for (i in 1..32) {  // https://cs.android.com/android/platform/superproject/+/master:packages/modules/Bluetooth/system/internal_include/bt_target.h;l=525;drc=a786e24777988f3207b90fdb5eb00bc68b540691
                mClientIf.setInt(gatt, i)
                gatt.disconnect()
            }
            return true
        } catch (e: NoSuchFieldException) {
            // Expected on API 36 once a Bluetooth module update removed mClientIf; unexpected on earlier versions.
            if (Build.VERSION.SDK_INT >= 36) Timber.d(e) else Timber.w(e)
            return false
        } catch (e: Exception) {
            Timber.w(e)
            return false
        } finally {
            gatt.close()
        }
    }
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect(device: BluetoothDevice): Boolean {
        if (bluetooth.adapter.bondedDevices?.contains(device) != false) {
            if (removeBond(device)) return true
            if (Build.VERSION.SDK_INT >= 37) return false
        }
        if (Build.VERSION.SDK_INT >= 31 && bluetooth.getConnectionState(device, BluetoothProfile.GATT) !=
            BluetoothProfile.STATE_CONNECTED) return true
        if (device.name == DEVICE_NAME_PGP && device.bondState == BluetoothDevice.BOND_NONE) return false
        return if (Build.VERSION.SDK_INT < 37) disconnectGatt(device) else false
    }
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectAll(): Boolean {
        var attempted = false
        var succeeded = false
        bluetooth.adapter.bondedDevices?.forEach { device ->
            if (getDeviceName(device) != null) {
                attempted = true
                succeeded = removeBond(device) || succeeded
            }
        }
        for (device in bluetooth.getConnectedDevices(BluetoothProfile.GATT)) if (getDeviceName(device) != null) {
            attempted = true
            succeeded = disconnect(device) || succeeded
        }
        return succeeded || !attempted
    }
}
