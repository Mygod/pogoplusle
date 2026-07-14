package be.mygod.pogoplusplus.xposed

import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import android.util.Log
import be.mygod.pogoplusplus.POKEMON_GO_PACKAGES
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BluetoothGattServerFilter : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val gattServerManager = XposedHelpers.findClassIfExists(CLASS_GATT_SERVER_MANAGER, lpparam.classLoader)
            if (gattServerManager != null) {
                try {
                    installServerManagerHooks(gattServerManager, lpparam.classLoader)
                    log("installed GattServerManager hooks in ${lpparam.packageName}/${lpparam.processName}")
                    return
                } catch (e: NoSuchMethodException) {
                    log("unsupported GattServerManager in ${lpparam.packageName}; trying GattService: ${e.message}")
                }
            }
            val gattService = XposedHelpers.findClassIfExists(CLASS_GATT_SERVICE, lpparam.classLoader) ?: return
            try {
                installGattServiceHooks(gattService, lpparam.classLoader)
                log("installed GattService hooks in ${lpparam.packageName}/${lpparam.processName}")
            } catch (e: NoSuchMethodException) {
                log("unsupported GattService in ${lpparam.packageName}: ${e.message}")
                log(e)
            }
        } catch (throwable: Throwable) {
            // A module failure must never take down the host Bluetooth process.
            log(throwable)
        }
    }

    /**
     * Android 17 moved GATT server callbacks and state into GattServerManager:
     * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android-17.0.0_r1/android/app/src/com/android/bluetooth/gatt/GattServerManager.kt#66
     * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android-17.0.0_r1/android/app/src/com/android/bluetooth/gatt/GattServerManager.kt#154
     * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android-17.0.0_r1/android/app/src/com/android/bluetooth/gatt/GattServerManager.kt#527
     */
    private fun installServerManagerHooks(gattServerManager: Class<*>, classLoader: ClassLoader) {
        // Resolve the complete shape before installing any hook so an incompatible vendor class can fall back safely.
        val onServerRegistered = gattServerManager.getDeclaredMethod("onServerRegisteredFromNative",
            Integer.TYPE, Integer.TYPE, UUID::class.java)
        val onClientConnected = gattServerManager.getDeclaredMethod("onClientConnectedFromNative",
            BluetoothDevice::class.java, Integer.TYPE, java.lang.Boolean.TYPE, Integer.TYPE, Integer.TYPE)
        val callbackClass = XposedHelpers.findClassIfExists(CLASS_GATT_SERVER_CALLBACK, classLoader)
            ?: throw NoSuchMethodException(CLASS_GATT_SERVER_CALLBACK)
        val unregisterServer = gattServerManager.getDeclaredMethod("unregisterServer", callbackClass)

        XposedBridge.hookMethod(onServerRegistered, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    if (param.hasThrowable() || param.args[0] as Int != 0) return
                    val serverIf = param.args[1] as Int
                    val serverMap = serverMapFromManager(param.thisObject)
                    trackServer(serverMap, serverIf) { connectedAddressesFromManager(param.thisObject) }
                } catch (throwable: Throwable) {
                    log(throwable)
                }
            }
        })
        XposedBridge.hookMethod(onClientConnected, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val address = addressOf(param.args[0]) ?: return
                    val connected = param.args[2] as Boolean
                    val connId = param.args[3] as Int
                    val serverIf = param.args[4] as Int
                    suppressIfNeeded(param, serverIf, connected, connId, address)
                } catch (throwable: Throwable) {
                    log(throwable)
                }
            }
        })
        XposedBridge.hookMethod(unregisterServer, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val serverIf = appIdForCallback(param.thisObject, param.args[0]) ?: return
                    param.setObjectExtra(UNREGISTER_SERVER_IF_EXTRA, serverIf)
                } catch (throwable: Throwable) {
                    log(throwable)
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    if (param.hasThrowable()) return
                    stopTracking(param.getObjectExtra(UNREGISTER_SERVER_IF_EXTRA) as? Int ?: return)
                } catch (throwable: Throwable) {
                    log(throwable)
                }
            }
        })
    }

    /**
     * AOSP keeps these callbacks stable from API 28 through 36. unregisterServer takes only the server ID on
     * API 28-30 and adds AttributionSource on API 31:
     * https://android.googlesource.com/platform/packages/apps/Bluetooth/+/android-9.0.0_r1/src/com/android/bluetooth/gatt/GattService.java#2516
     * https://android.googlesource.com/platform/packages/apps/Bluetooth/+/android-11.0.0_r1/src/com/android/bluetooth/gatt/GattService.java#2977
     * https://android.googlesource.com/platform/packages/apps/Bluetooth/+/android-12.0.0_r1/src/com/android/bluetooth/gatt/GattService.java#3402
     * https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android-16.0.0_r1/android/app/src/com/android/bluetooth/gatt/GattService.java#1753
     */
    private fun installGattServiceHooks(gattService: Class<*>, classLoader: ClassLoader) {
        // Resolve the complete shape before installing any hook. Capability, not SDK_INT, owns dispatch because
        // Bluetooth can be delivered as a Mainline module and vendors can ship a different module revision.
        val onServerRegistered = gattService.getDeclaredMethod("onServerRegistered",
            Integer.TYPE, Integer.TYPE, java.lang.Long.TYPE, java.lang.Long.TYPE)
        val onClientConnected = gattService.getDeclaredMethod("onClientConnected",
            String::class.java, java.lang.Boolean.TYPE, Integer.TYPE, Integer.TYPE)
        val attributionSource = XposedHelpers.findClassIfExists(CLASS_ATTRIBUTION_SOURCE, classLoader)
        val unregisterServer = if (attributionSource == null) {
            gattService.getDeclaredMethod("unregisterServer", Integer.TYPE)
        } else try {
            gattService.getDeclaredMethod("unregisterServer", Integer.TYPE, attributionSource)
        } catch (_: NoSuchMethodException) {
            gattService.getDeclaredMethod("unregisterServer", Integer.TYPE)
        }

        XposedBridge.hookMethod(onServerRegistered, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    if (param.hasThrowable() || param.args[0] as Int != 0) return
                    val serverIf = param.args[1] as Int
                    val serverMap = serverMapFromService(param.thisObject)
                    trackServer(serverMap, serverIf) { connectedAddressesFromService(param.thisObject) }
                } catch (throwable: Throwable) {
                    log(throwable)
                }
            }
        })
        XposedBridge.hookMethod(onClientConnected, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val address = normalizeAddress(param.args[0] as? String ?: return)
                    val connected = param.args[1] as Boolean
                    val connId = param.args[2] as Int
                    val serverIf = param.args[3] as Int
                    suppressIfNeeded(param, serverIf, connected, connId, address)
                } catch (throwable: Throwable) {
                    log(throwable)
                }
            }
        })
        XposedBridge.hookMethod(unregisterServer, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    if (param.hasThrowable()) return
                    stopTracking(param.args[0] as? Int ?: return)
                } catch (throwable: Throwable) {
                    log(throwable)
                }
            }
        })
    }

    private companion object {
        private const val TAG = "PoGoLE-Xposed"
        private const val CLASS_ATTRIBUTION_SOURCE = "android.content.AttributionSource"
        private const val CLASS_GATT_SERVER_CALLBACK = "android.bluetooth.IBluetoothGattServerCallback"
        private const val CLASS_GATT_SERVICE = "com.android.bluetooth.gatt.GattService"
        private const val CLASS_GATT_SERVER_MANAGER = "com.android.bluetooth.gatt.GattServerManager"
        private const val UNREGISTER_SERVER_IF_EXTRA = "be.mygod.pogoplusplus.xposed.serverIf"
        private const val STALE_CALLBACK_WINDOW_MS = 3_000L

        private val serverFilters = ConcurrentHashMap<Int, StaleConnectionFilter>()

        private fun trackServer(serverMap: Any, serverIf: Int, connectedAddresses: () -> Set<String>) {
            serverFilters.remove(serverIf)
            if (appNameForServer(serverMap, serverIf) !in POKEMON_GO_PACKAGES) return
            val baseline = connectedAddresses()
            if (baseline.isEmpty()) return
            serverFilters[serverIf] = StaleConnectionFilter(
                registeredAtMillis = SystemClock.elapsedRealtime(),
                baselineAddresses = baseline,
                callbackWindowMillis = STALE_CALLBACK_WINDOW_MS,
            )
            log("tracking Pokemon GO GATT server $serverIf with ${baseline.size} existing devices")
        }

        private fun suppressIfNeeded(
            param: XC_MethodHook.MethodHookParam,
            serverIf: Int,
            connected: Boolean,
            connId: Int,
            address: String,
        ) {
            val filter = serverFilters[serverIf] ?: return
            if (!filter.shouldSuppress(connected, connId, address, SystemClock.elapsedRealtime())) return
            param.result = null
            log("suppressed stale Pokemon GO server $serverIf ${if (connected) "connect" else "disconnect"} " +
                    "for $address connId=$connId")
        }

        private fun stopTracking(serverIf: Int) {
            if (serverFilters.remove(serverIf) != null) {
                log("stopped tracking Pokemon GO GATT server $serverIf")
            }
        }

        private fun appNameForServer(serverMap: Any, serverIf: Int): String? {
            val app = XposedHelpers.callMethod(serverMap, "getById", serverIf) ?: return null
            return callMethodIfExists(app, "getName") as? String ?:
                XposedHelpers.getObjectField(app, "name") as? String
        }

        private fun appIdForCallback(gattServerManager: Any, callback: Any?): Int? {
            val app = XposedHelpers.callMethod(serverMapFromManager(gattServerManager),
                "getByCallbackId", callback) ?: return null
            return callMethodIfExists(app, "getId") as? Int ?: XposedHelpers.getIntField(app, "id")
        }

        private fun connectedAddressesFromManager(gattServerManager: Any): Set<String> {
            val result = LinkedHashSet<String>()
            result += connectedAddressesFromMap(clientMapFromService(gattServiceFromManager(gattServerManager)))
            result += connectedAddressesFromMap(serverMapFromManager(gattServerManager))
            return Collections.unmodifiableSet(result)
        }

        private fun connectedAddressesFromService(gattService: Any): Set<String> {
            val result = LinkedHashSet<String>()
            result += connectedAddressesFromMap(clientMapFromService(gattService))
            result += connectedAddressesFromMap(serverMapFromService(gattService))
            return Collections.unmodifiableSet(result)
        }

        private fun connectedAddressesFromMap(contextMap: Any?): Set<String> {
            contextMap ?: return emptySet()
            val addresses = XposedHelpers.callMethod(contextMap, "getConnectedDevices") as? Set<*> ?: return emptySet()
            return addresses.mapNotNullTo(LinkedHashSet(), ::addressOf)
        }

        private fun addressOf(value: Any?): String? = when (value) {
            is BluetoothDevice -> value.address?.let(::normalizeAddress)
            is String -> normalizeAddress(value)
            null -> null
            else -> (callMethodIfExists(value, "getAddress") as? String)?.let(::normalizeAddress)
        }

        private fun gattServiceFromManager(gattServerManager: Any) =
            XposedHelpers.getObjectField(gattServerManager, "gatt")

        private fun clientMapFromService(gattService: Any) =
            callMethodIfExists(gattService, "getClientMap") ?: XposedHelpers.getObjectField(gattService, "mClientMap")

        private fun serverMapFromManager(gattServerManager: Any) =
            callMethodIfExists(gattServerManager, "getServerMap") ?:
                XposedHelpers.getObjectField(gattServerManager, "serverMap")

        private fun serverMapFromService(gattService: Any) =
            callMethodIfExists(gattService, "getServerMap") ?: XposedHelpers.getObjectField(gattService, "mServerMap")

        private fun callMethodIfExists(receiver: Any, methodName: String, vararg args: Any?) = try {
            XposedHelpers.callMethod(receiver, methodName, *args)
        } catch (_: NoSuchMethodError) {
            null
        }

        private fun normalizeAddress(address: String) = address.uppercase(Locale.ROOT)

        private fun log(message: String) {
            XposedBridge.log("$TAG: $message")
            Log.i(TAG, message)
        }

        private fun log(throwable: Throwable) {
            XposedBridge.log("$TAG: ${throwable::class.java.name}: ${throwable.message}")
            XposedBridge.log(throwable)
            Log.w(TAG, "${throwable::class.java.name}: ${throwable.message}", throwable)
        }
    }
}
