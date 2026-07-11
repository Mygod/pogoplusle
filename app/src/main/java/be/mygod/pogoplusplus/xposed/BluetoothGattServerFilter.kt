package be.mygod.pogoplusplus.xposed

import android.bluetooth.BluetoothDevice
import android.content.AttributionSource
import android.os.SystemClock
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BluetoothGattServerFilter : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PACKAGE_BLUETOOTH) return
        try {
            val gattServerManager = XposedHelpers.findClassIfExists(CLASS_GATT_SERVER_MANAGER, lpparam.classLoader)
            if (gattServerManager != null) {
                hookServerManagerRegistration(gattServerManager)
                hookServerManagerClientConnection(gattServerManager)
                runCatching { hookServerManagerUnregister(gattServerManager, lpparam.classLoader) }.onFailure(::log)
                log("installed GattServerManager hooks in ${lpparam.processName}")
                return
            }
            val gattService = XposedHelpers.findClassIfExists(CLASS_GATT_SERVICE, lpparam.classLoader)
            if (gattService == null) {
                log("GattService not found in ${lpparam.packageName}")
                return
            }
            hookServerRegistration(gattService)
            hookClientConnection(gattService)
            hookServerUnregister(gattService)
            log("installed in ${lpparam.processName}")
        } catch (throwable: Throwable) {
            log(throwable)
        }
    }

    private fun hookServerManagerRegistration(gattServerManager: Class<*>) {
        XposedHelpers.findAndHookMethod(gattServerManager, "onServerRegisteredFromNative",
            Integer.TYPE, Integer.TYPE, UUID::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val status = param.args[0] as Int
                        if (status != 0) return
                        val serverIf = param.args[1] as Int
                        val uuid = param.args[2] as UUID
                        val appName = appNameForServer(serverMapFromManager(param.thisObject), serverIf) ?: return
                        if (appName != PACKAGE_POKEMON_GO) return
                        val baseline = connectedAddressesFromManager(param.thisObject)
                        if (baseline.isEmpty()) return
                        serverFilters[serverIf] = ServerFilter(
                            uuid = uuid,
                            registeredAt = SystemClock.elapsedRealtime(),
                            baselineAddresses = baseline,
                        )
                        log("tracking Pokemon GO GATT server $serverIf with ${baseline.size} existing devices")
                    } catch (throwable: Throwable) {
                        log(throwable)
                    }
                }
            })
    }

    private fun hookServerManagerClientConnection(gattServerManager: Class<*>) {
        XposedHelpers.findAndHookMethod(gattServerManager, "onClientConnectedFromNative",
            BluetoothDevice::class.java, Integer.TYPE, java.lang.Boolean.TYPE, Integer.TYPE, Integer.TYPE,
            object : XC_MethodHook() {
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
    }

    private fun hookServerManagerUnregister(gattServerManager: Class<*>, classLoader: ClassLoader) {
        val callbackClass = XposedHelpers.findClass("android.bluetooth.IBluetoothGattServerCallback", classLoader)
        XposedHelpers.findAndHookMethod(gattServerManager, "unregisterServer",
            callbackClass,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val serverIf = appIdForCallback(param.thisObject, param.args[0]) ?: return
                        if (serverFilters.remove(serverIf) != null) {
                            log("stopped tracking Pokemon GO GATT server $serverIf")
                        }
                    } catch (throwable: Throwable) {
                        log(throwable)
                    }
                }
            })
    }

    private fun hookServerRegistration(gattService: Class<*>) {
        XposedHelpers.findAndHookMethod(gattService, "onServerRegistered",
            Integer.TYPE, Integer.TYPE, java.lang.Long.TYPE, java.lang.Long.TYPE,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val status = param.args[0] as Int
                        if (status != 0) return
                        val serverIf = param.args[1] as Int
                        val uuidLsb = param.args[2] as Long
                        val uuidMsb = param.args[3] as Long
                        val appName = appNameForServer(serverMapFromService(param.thisObject), serverIf) ?: return
                        if (appName != PACKAGE_POKEMON_GO) return
                        val baseline = connectedAddressesFromService(param.thisObject)
                        if (baseline.isEmpty()) return
                        serverFilters[serverIf] = ServerFilter(
                            uuid = UUID(uuidMsb, uuidLsb),
                            registeredAt = SystemClock.elapsedRealtime(),
                            baselineAddresses = baseline,
                        )
                        log("tracking Pokemon GO GATT server $serverIf with ${baseline.size} existing devices")
                    } catch (throwable: Throwable) {
                        log(throwable)
                    }
                }
            })
    }

    private fun hookClientConnection(gattService: Class<*>) {
        XposedHelpers.findAndHookMethod(gattService, "onClientConnected",
            String::class.java, java.lang.Boolean.TYPE, Integer.TYPE, Integer.TYPE,
            object : XC_MethodHook() {
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
    }

    private fun hookServerUnregister(gattService: Class<*>) {
        XposedHelpers.findAndHookMethod(gattService, "unregisterServer",
            Integer.TYPE, AttributionSource::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val serverIf = param.args[0] as? Int ?: return
                    if (serverFilters.remove(serverIf) != null) {
                        log("stopped tracking Pokemon GO GATT server $serverIf")
                    }
                }
            })
    }

    private data class ServerFilter(
        val uuid: UUID,
        val registeredAt: Long,
        val baselineAddresses: Set<String>,
        val ignoredConnIds: MutableSet<Int> = ConcurrentHashMap.newKeySet(),
        val ignoredAddresses: MutableSet<String> = ConcurrentHashMap.newKeySet(),
    ) {
        fun shouldIgnoreInitial(address: String): Boolean {
            if (address !in baselineAddresses) return false
            return SystemClock.elapsedRealtime() - registeredAt <= STALE_CALLBACK_WINDOW_MS
        }
    }

    private companion object {
        private const val TAG = "PoGoLE-Xposed"
        private const val PACKAGE_BLUETOOTH = "com.google.android.bluetooth"
        private const val PACKAGE_POKEMON_GO = "com.nianticlabs.pokemongo"
        private const val CLASS_GATT_SERVICE = "com.android.bluetooth.gatt.GattService"
        private const val CLASS_GATT_SERVER_MANAGER = "com.android.bluetooth.gatt.GattServerManager"
        private const val STALE_CALLBACK_WINDOW_MS = 3_000L

        private val serverFilters = ConcurrentHashMap<Int, ServerFilter>()

        private fun suppressIfNeeded(
            param: XC_MethodHook.MethodHookParam,
            serverIf: Int,
            connected: Boolean,
            connId: Int,
            address: String,
        ) {
            val filter = serverFilters[serverIf] ?: return
            if (connected) {
                if (!filter.shouldIgnoreInitial(address)) return
                filter.ignoredConnIds += connId
                filter.ignoredAddresses += address
                param.result = null
                log("suppressed stale Pokemon GO server $serverIf connect for $address connId=$connId")
            } else if (filter.ignoredConnIds.remove(connId) || filter.ignoredAddresses.remove(address)) {
                param.result = null
                log("suppressed stale Pokemon GO server $serverIf disconnect for $address connId=$connId")
            }
        }

        private fun appNameForServer(serverMap: Any, serverIf: Int): String? {
            val app = XposedHelpers.callMethod(serverMap, "getById", serverIf) ?: return null
            return callMethodOrNull(app, "getName") as? String ?: XposedHelpers.getObjectField(app, "name") as? String
        }

        private fun appIdForCallback(gattServerManager: Any, callback: Any?): Int? {
            val app = XposedHelpers.callMethod(serverMapFromManager(gattServerManager), "getByCallbackId", callback) ?: return null
            return callMethodOrNull(app, "getId") as? Int ?: XposedHelpers.getIntField(app, "id")
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
            else -> (callMethodOrNull(value, "getAddress") as? String)?.let(::normalizeAddress)
        }

        private fun gattServiceFromManager(gattServerManager: Any) =
            XposedHelpers.getObjectField(gattServerManager, "gatt")

        private fun clientMapFromService(gattService: Any) =
            callMethodOrNull(gattService, "getClientMap") ?: XposedHelpers.getObjectField(gattService, "mClientMap")

        private fun serverMapFromManager(gattServerManager: Any) =
            callMethodOrNull(gattServerManager, "getServerMap")
                ?: XposedHelpers.getObjectField(gattServerManager, "serverMap")

        private fun serverMapFromService(gattService: Any) =
            callMethodOrNull(gattService, "getServerMap") ?: XposedHelpers.getObjectField(gattService, "mServerMap")

        private fun callMethodOrNull(receiver: Any, methodName: String, vararg args: Any?) =
            runCatching { XposedHelpers.callMethod(receiver, methodName, *args) }.getOrNull()

        private fun normalizeAddress(address: String) = address.uppercase()

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
