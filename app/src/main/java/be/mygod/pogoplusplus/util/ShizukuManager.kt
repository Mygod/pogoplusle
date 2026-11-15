package be.mygod.pogoplusplus.util

import android.Manifest
import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.AttributionSource
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.os.Process
import androidx.annotation.RequiresApi
import be.mygod.pogoplusplus.App.Companion.app
import eu.darken.porter.sdk.Porter
import eu.darken.porter.sdk.PorterConnection
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber
import java.time.Duration

object ShizukuManager {
    private const val BLUETOOTH_JARJAR_PREFIX = "com.android.bluetooth.x."
    private const val SYNCHRONOUS_RESULT_RECEIVER =
        "${BLUETOOTH_JARJAR_PREFIX}com.android.modules.utils.SynchronousResultReceiver"
    private val synchronousResultTimeout: Duration = Duration.ofSeconds(5)

    private val hiddenApiAccess by lazy {
        HiddenApiBypass.addHiddenApiExemptions(
            "Landroid/bluetooth/",
            "Lcom/android/bluetooth/",
            "Lcom/android/modules/utils/",
        )
    }

    // API 28-30 only expose the callback overload. API 31 added the no-argument overload.
    // https://android.googlesource.com/platform/frameworks/base/+/android-11.0.0_r1/core/java/android/bluetooth/BluetoothAdapter.java#2487
    // https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/bluetooth/BluetoothAdapter.java#2206
    private val getBluetoothService: (BluetoothAdapter) -> IInterface by lazy {
        try {
            val method = BluetoothAdapter::class.java.getDeclaredMethod("getBluetoothService")
            method.isAccessible = true;
            { adapter -> method(adapter) as IInterface }
        } catch (e: NoSuchMethodException) {
            if (Build.VERSION.SDK_INT >= 31) Timber.w(e)
            val method = BluetoothAdapter::class.java.getDeclaredMethod("getBluetoothService",
                Class.forName("android.bluetooth.IBluetoothManagerCallback"))
            method.isAccessible = true;
            { adapter -> method(adapter, null) as IInterface }
        }
    }

    @get:RequiresApi(31)
    private val shellAttributionSource by lazy @TargetApi(31) {
        AttributionSource.Builder(Process.SHELL_UID).apply {
            setPackageName("com.android.shell")
        }.build()
    }

    /**
     * Verified against tagged AOSP sources at each signature boundary:
     * android-11.0.0_r1: https://android.googlesource.com/platform/frameworks/base/+/android-11.0.0_r1/core/java/android/bluetooth/BluetoothDevice.java#1556
     * android-12.0.0_r1: https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/bluetooth/BluetoothDevice.java#1887
     * android-13.0.0_r1: https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android-13.0.0_r1/system/binder/android/bluetooth/IBluetooth.aidl#158
     * android-15.0.0_r1: https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android-15.0.0_r1/android/app/aidl/android/bluetooth/IBluetooth.aidl#140
     *
     * Android 13-14 jarjar-shade SynchronousResultReceiver in the runtime descriptor:
     * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android-13.0.0_r1/framework/jarjar-rules.txt#2
     * Motorola Android 13 build T1SSS33.1-119-8-16 exposes the unshaded source name, so prefixed
     * runtime class names fall back to their unshaded form.
     */
    private val setPairingConfirmation: (Any?, BluetoothDevice) -> Boolean by lazy {
        val serviceClass = Class.forName("android.bluetooth.IBluetooth")
        if (Build.VERSION.SDK_INT < 31) {
            val method = serviceClass.getMethod("setPairingConfirmation", BluetoothDevice::class.java,
                java.lang.Boolean.TYPE);
            { service, device -> method(service, device, true) as Boolean }
        } else {
            try {
                val method = serviceClass.getMethod("setPairingConfirmation", BluetoothDevice::class.java,
                    java.lang.Boolean.TYPE, AttributionSource::class.java);
                { service, device -> method(service, device, true, shellAttributionSource) as Boolean }
            } catch (e: NoSuchMethodException) {
                if (Build.VERSION.SDK_INT !in 33..34) Timber.w(e)
                val receiverClass = try {
                    Class.forName(SYNCHRONOUS_RESULT_RECEIVER)
                } catch (_: ClassNotFoundException) {
                    Class.forName(SYNCHRONOUS_RESULT_RECEIVER.removePrefix(BLUETOOTH_JARJAR_PREFIX))
                }
                val method = serviceClass.getMethod("setPairingConfirmation", BluetoothDevice::class.java,
                    java.lang.Boolean.TYPE, AttributionSource::class.java, receiverClass)
                val getReceiver = receiverClass.getMethod("get")
                val awaitResult = receiverClass.getMethod("awaitResultNoInterrupt", Duration::class.java)
                val getValue = awaitResult.returnType.getMethod("getValue", Any::class.java);
                { service, device ->
                    val receiver = getReceiver(null)
                    method(service, device, true, shellAttributionSource, receiver)
                    // Match BluetoothUtils.getSyncTimeout() on Android 13-14.
                    // https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android-13.0.0_r1/framework/java/android/bluetooth/BluetoothUtils.java#41
                    val receiverResult = awaitResult(receiver, synchronousResultTimeout)
                        ?: error("SynchronousResultReceiver returned no result")
                    getValue(receiverResult, false) as Boolean
                }
            }
        }
    }
    private val asInterface by lazy {
        Class.forName("android.bluetooth.IBluetooth\$Stub").getMethod("asInterface", IBinder::class.java)
    }

    /**
     * A process started by the pairing broadcast receives the server's binder asynchronously. Porter publishes a
     * connection only after the server's attach reply, so a connection is always usable once it arrives.
     */
    suspend fun awaitConnection(timeoutMillis: Long) = withTimeoutOrNull(timeoutMillis) {
        Porter.connection.filterNotNull().first()
    }

    /**
     * Bluetooth enforces BLUETOOTH_PRIVILEGED on the server's UID, which shell only holds since API 33, so an
     * ADB-started Shizuku or Porter cannot confirm pairing before that:
     * https://android.googlesource.com/platform/packages/apps/Bluetooth/+/android-12.0.0_r1/src/com/android/bluetooth/btservice/AdapterService.java#1983
     * https://android.googlesource.com/platform/frameworks/base/+/android-12.1.0_r1/packages/Shell/AndroidManifest.xml#120
     * https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r1/packages/Shell/AndroidManifest.xml#126
     */
    suspend fun canConfirmPairing(connection: PorterConnection) =
        connection.checkRemotePermission(Manifest.permission.BLUETOOTH_PRIVILEGED)

    /** Blocks on Binder calls; call off the main thread. */
    fun setPairingConfirmation(connection: PorterConnection, device: BluetoothDevice) = hiddenApiAccess.run {
        setPairingConfirmation(asInterface(null, connection.wrap(getBluetoothService(
            app.getSystemService(BluetoothManager::class.java).adapter).asBinder())), device)
    }
}
