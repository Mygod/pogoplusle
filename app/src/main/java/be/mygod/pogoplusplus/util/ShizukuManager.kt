package be.mygod.pogoplusplus.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.os.Process
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.InvocationTargetException

object ShizukuManager {
    @SuppressLint("MissingPermission", "PrivateApi", "SoonBlockedPrivateApi")
    fun setPairingConfirmation(device: BluetoothDevice): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/bluetooth/",
                "Landroid/content/AttributionSource;",
            )
        }

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val service = BluetoothAdapter::class.java.getDeclaredMethod("getBluetoothService")
            .apply { isAccessible = true }
            .invoke(adapter) as? IInterface ?: return false
        val wrappedService = Class.forName("android.bluetooth.IBluetooth\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(service.asBinder()))
        val serviceInterface = Class.forName("android.bluetooth.IBluetooth")
        val method = serviceInterface.methods.singleOrNull { method ->
            method.name == "setPairingConfirmation" &&
                    method.parameterTypes.getOrNull(0) == BluetoothDevice::class.java &&
                    method.parameterTypes.getOrNull(1) == Boolean::class.javaPrimitiveType
        } ?: error("IBluetooth.setPairingConfirmation not found")
        val result = try {
            when (method.parameterTypes.size) {
                2 -> method.invoke(wrappedService, device, true)
                3 -> {
                    val builder = Class.forName("android.content.AttributionSource\$Builder")
                        .getConstructor(Int::class.javaPrimitiveType)
                        .newInstance(Process.SHELL_UID)
                    builder.javaClass.getMethod("setPackageName", String::class.java)
                        .invoke(builder, "com.android.shell")
                    method.invoke(wrappedService, device, true,
                        builder.javaClass.getMethod("build").invoke(builder))
                }
                else -> error("Unsupported IBluetooth.setPairingConfirmation signature")
            }
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
        return result as Boolean
    }
}
