package be.mygod.pogoplusplus.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.AttributionSource
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.os.Process
import androidx.annotation.RequiresApi
import be.mygod.pogoplusplus.App.Companion.app
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import timber.log.Timber
import java.lang.reflect.InvocationTargetException
import java.time.Duration

@SuppressLint("PrivateApi", "SoonBlockedPrivateApi")
object ShizukuManager {
    private const val SYNCHRONOUS_RESULT_RECEIVER =
        "com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver"
    private val synchronousResultTimeout: Duration = Duration.ofSeconds(5)

    // API 28-30 only expose the callback overload. API 31 added the no-argument overload.
    // https://android.googlesource.com/platform/frameworks/base/+/android-11.0.0_r1/core/java/android/bluetooth/BluetoothAdapter.java#2487
    // https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/bluetooth/BluetoothAdapter.java#2206
    private val getBluetoothService: (BluetoothAdapter) -> IInterface? by lazy {
        try {
            val method = BluetoothAdapter::class.java.getDeclaredMethod("getBluetoothService")
            method.isAccessible = true;
            { adapter -> method.invoke(adapter) as? IInterface }
        } catch (e: NoSuchMethodException) {
            if (Build.VERSION.SDK_INT >= 31) Timber.w(e)
            val method = BluetoothAdapter::class.java.getDeclaredMethod("getBluetoothService",
                Class.forName("android.bluetooth.IBluetoothManagerCallback"))
            method.isAccessible = true;
            { adapter -> method.invoke(adapter, null) as? IInterface }
        }
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
     */
    private val pairingConfirmation: (Any, BluetoothDevice) -> Boolean by lazy {
        val serviceClass = Class.forName("android.bluetooth.IBluetooth")
        if (Build.VERSION.SDK_INT < 31) {
            val method = serviceClass.getMethod("setPairingConfirmation", BluetoothDevice::class.java,
                java.lang.Boolean.TYPE);
            { service, device -> method.invoke(service, device, true) as Boolean }
        } else {
            val attributionSource = shellAttributionSource()
            try {
                val method = serviceClass.getMethod("setPairingConfirmation", BluetoothDevice::class.java,
                    java.lang.Boolean.TYPE, AttributionSource::class.java);
                { service, device -> method.invoke(service, device, true, attributionSource) as Boolean }
            } catch (e: NoSuchMethodException) {
                if (Build.VERSION.SDK_INT < 33 || Build.VERSION.SDK_INT > 34) Timber.w(e)
                val receiverClass = Class.forName(SYNCHRONOUS_RESULT_RECEIVER)
                val method = serviceClass.getMethod("setPairingConfirmation", BluetoothDevice::class.java,
                    java.lang.Boolean.TYPE, AttributionSource::class.java, receiverClass)
                val getReceiver = receiverClass.getMethod("get")
                val awaitResult = receiverClass.getMethod("awaitResultNoInterrupt", Duration::class.java)
                val getValue = awaitResult.returnType.getMethod("getValue", Any::class.java);
                { service, device ->
                    val receiver = getReceiver.invoke(null)
                    method.invoke(service, device, true, attributionSource, receiver)
                    // Match BluetoothUtils.getSyncTimeout() on Android 13-14.
                    // https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android-13.0.0_r1/framework/java/android/bluetooth/BluetoothUtils.java#41
                    val receiverResult = awaitResult.invoke(receiver, synchronousResultTimeout)
                        ?: error("SynchronousResultReceiver returned no result")
                    getValue.invoke(receiverResult, false) as Boolean
                }
            }
        }
    }

    fun isAuthorized() = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: RuntimeException) {
        Timber.d(e)
        false
    }

    @RequiresApi(31)
    private fun shellAttributionSource() = AttributionSource.Builder(Process.SHELL_UID)
        .setPackageName("com.android.shell")
        .build()

    @SuppressLint("MissingPermission")
    fun setPairingConfirmation(device: BluetoothDevice): Boolean {
        HiddenApiBypass.addHiddenApiExemptions(
            "Landroid/bluetooth/",
            "Lcom/android/bluetooth/",
        )

        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        val service = getBluetoothService(adapter) ?: return false
        val wrappedService = Class.forName("android.bluetooth.IBluetooth\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(service.asBinder()))
        return try {
            pairingConfirmation(wrappedService ?: return false, device)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }
}
