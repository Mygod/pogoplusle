package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.edit
import be.mygod.pogoplusplus.App.Companion.app
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executor

/**
 * Speeds up the link to an accessory that slows its own connection: Pokémon GO Plus asks for a 500 ms interval about
 * 3 seconds after connecting, so every GATT exchange of a spin or catch waits up to half a second.
 *
 * LE connection parameters belong to the link rather than to a GATT client, since the stack drops the client and
 * updates the link by address, so joining the link the game holds as another client changes it for the game too:
 * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/c77db469de80f86660bc053bec4dee0c5d4b947c/android/app/jni/com_android_bluetooth_gatt.cpp#1268
 * The accessory can override it at any time, as the stack accepts its requests without involving any app:
 * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/c77db469de80f86660bc053bec4dee0c5d4b947c/system/stack/l2cap/l2c_ble.cc#385
 * so each session stays for the whole connection and requests again whenever the interval turns slower than the
 * priority allows. The stack reports every parameter update to every client:
 * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/c77db469de80f86660bc053bec4dee0c5d4b947c/system/stack/gatt/gatt_main.cc#434
 * and an opportunistic client joins an existing link without holding it, so the link still drops with the game:
 * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/c77db469de80f86660bc053bec4dee0c5d4b947c/system/stack/gatt/stack_le_connection.cc#77
 * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/c77db469de80f86660bc053bec4dee0c5d4b947c/system/bta/gatt/bta_gattc_conn.cc#148
 *
 * Sessions rely on the process staying alive, which the accessibility or notification listener service ensures;
 * holding the connection broadcast instead would delay the pairing request that follows it to this process.
 */
@SuppressLint("MissingPermission")  // sessions only start with BLUETOOTH_CONNECT granted
class SfidaConnectionPriority private constructor(private val device: BluetoothDevice, private val priority: Int,
                                                  private val maxInterval: Int) : BluetoothGattCallback() {
    companion object {
        const val KEY_PRIORITY = "connectionPriority"
        const val PRIORITY_DEFAULT = -1
        private const val CONNECT_TIMEOUT_MILLIS = 10_000L
        /** An accessory overriding more often than this is fighting back; going on would only keep both radios busy. */
        private const val MAX_REQUESTS = 6
        private const val REQUEST_WINDOW_MILLIS = 60_000L

        private val handler = Handler(Looper.getMainLooper())
        /** Accessed on the main thread only. */
        private val sessions = mutableMapOf<String, SfidaConnectionPriority>()

        /** Ordered from slowest to fastest. */
        val priorities = listOfNotNull(
            PRIORITY_DEFAULT,
            BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER,
            BluetoothGatt.CONNECTION_PRIORITY_BALANCED,
            if (Build.VERSION.SDK_INT >= 34) BluetoothGatt.CONNECTION_PRIORITY_DCK else null,
            BluetoothGatt.CONNECTION_PRIORITY_HIGH,
        )

        /** Called on the main thread; applies to accessories that are already connected. */
        var preferredPriority: Int
            // a restored backup can hold a priority this device lacks
            get() = Prefs.backup.getInt(KEY_PRIORITY, PRIORITY_DEFAULT).takeIf { it in priorities } ?: PRIORITY_DEFAULT
            set(value) {
                Prefs.backup.edit { putInt(KEY_PRIORITY, value) }
                for (session in sessions.values.toList()) session.close()
                try {
                    for (device in SfidaManager.connectedAuxiliaries) onConnected(device)
                } catch (e: SecurityException) {
                    Timber.d(e)
                }
            }

        /**
         * Slowest connection interval, in 1.25 ms units, of each priority's default parameters:
         * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/c77db469de80f86660bc053bec4dee0c5d4b947c/android/app/src/com/android/bluetooth/btservice/CompanionManager.java#93
         * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/c77db469de80f86660bc053bec4dee0c5d4b947c/android/app/res/values/config.xml#37
         */
        private fun maxIntervalOf(priority: Int) = when (priority) {
            BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER -> 100
            BluetoothGatt.CONNECTION_PRIORITY_BALANCED -> 40
            BluetoothGatt.CONNECTION_PRIORITY_HIGH -> 12
            BluetoothGatt.CONNECTION_PRIORITY_DCK -> if (Build.VERSION.SDK_INT >= 34) 24 else null
            else -> null
        }

        /**
         * (API 28-36) Landroid/bluetooth/BluetoothDevice;->connectGatt(Landroid/content/Context;ZLandroid/bluetooth/BluetoothGattCallback;IZILandroid/os/Handler;)Landroid/bluetooth/BluetoothGatt;,max-target-r
         * (API 36) Landroid/bluetooth/BluetoothDevice;->connectGatt(Landroid/content/Context;ZLandroid/bluetooth/BluetoothGattCallback;IZILjava/util/concurrent/Executor;)Landroid/bluetooth/BluetoothGatt;,max-target-r
         */
        private val hiddenConnectGattOpportunistic: ((BluetoothDevice, BluetoothGattCallback) -> BluetoothGatt?)? by lazy {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/BluetoothDevice;->connectGatt(")
            val parameters = arrayOf(Context::class.java, Boolean::class.java, BluetoothGattCallback::class.java,
                Int::class.java, Boolean::class.java, Int::class.java)
            for ((type, executor) in arrayOf(Handler::class.java to handler, Executor::class.java to app.mainExecutor)) {
                val method = try {
                    BluetoothDevice::class.java.getDeclaredMethod("connectGatt", *parameters, type)
                } catch (_: NoSuchMethodException) {
                    continue
                }
                return@lazy { device: BluetoothDevice, callback: BluetoothGattCallback ->
                    method(device, app, false, callback, BluetoothDevice.TRANSPORT_LE, true,
                        BluetoothDevice.PHY_LE_1M_MASK, executor) as BluetoothGatt?
                }
            }
            Timber.w(NoSuchMethodException("BluetoothDevice.connectGatt with opportunistic"))
            null
        }

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        private fun BluetoothDevice.connectGattOpportunistic(callback: BluetoothGattCallback) =
            if (Build.VERSION.SDK_INT >= 37) connectGatt(BluetoothGattConnectionSettings.Builder()
                .setTransport(BluetoothDevice.TRANSPORT_LE)
                .setOpportunisticEnabled(true)
                .setAutomaticMtuEnabled(false)
                .build(), app.mainExecutor, callback) else try {
                hiddenConnectGattOpportunistic?.invoke(this, callback)
            } catch (e: InvocationTargetException) {
                Timber.w(e.cause ?: e)
                null
            }

        /** Called on the main thread when [device] connects over LE. */
        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        fun onConnected(device: BluetoothDevice) {
            val priority = preferredPriority
            val maxInterval = maxIntervalOf(priority) ?: return
            sessions.remove(device.address)?.close()
            val session = SfidaConnectionPriority(device, priority, maxInterval)
            sessions[device.address] = session
            session.start()
        }

        /** Called on the main thread when [device] disconnects. */
        fun onDisconnected(device: BluetoothDevice) {
            sessions.remove(device.address)?.close()
        }
    }

    private var gatt: BluetoothGatt? = null
    private val requests = ArrayDeque<Long>()
    private val connectTimeout = Runnable {
        Timber.w("Connection priority could not join ${device.address}")
        close()
    }

    private fun start() {
        gatt = device.connectGattOpportunistic(this) ?: return close()
        handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MILLIS)
    }

    private fun close() {
        handler.removeCallbacks(connectTimeout)
        gatt?.close()
        gatt = null
        sessions.remove(device.address, this)
    }

    private fun request(gatt: BluetoothGatt) {
        val now = SystemClock.elapsedRealtime()
        while (requests.isNotEmpty() && now - requests.first() >= REQUEST_WINDOW_MILLIS) requests.removeFirst()
        if (requests.size >= MAX_REQUESTS) {
            Timber.w("Connection priority keeps being overridden for ${device.address}")
            return close()
        }
        requests.addLast(now)
        if (!gatt.requestConnectionPriority(priority)) {
            Timber.w("Connection priority request failed for ${device.address}")
            close()
        }
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (gatt != this.gatt) return
        if (newState != BluetoothProfile.STATE_CONNECTED) return close()
        handler.removeCallbacks(connectTimeout)
        request(gatt)
    }

    /**
     * Landroid/bluetooth/BluetoothGattCallback;->onConnectionUpdated(Landroid/bluetooth/BluetoothGatt;IIII)V,lo-prio,max-target-o
     * Overriding the hidden callback is not access to it, and API 37 allows it as sdk,test-api; kept by the keep rules.
     */
    @Suppress("unused")
    fun onConnectionUpdated(gatt: BluetoothGatt, interval: Int, latency: Int, timeout: Int, status: Int) {
        if (gatt != this.gatt) return
        Timber.d("Connection updated for ${device.address}: interval=$interval, latency=$latency, " +
                "timeout=$timeout, status=$status")
        if (status == BluetoothGatt.GATT_SUCCESS && interval > maxInterval) request(gatt)
    }
}
