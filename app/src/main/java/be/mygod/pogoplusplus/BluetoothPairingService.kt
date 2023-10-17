package be.mygod.pogoplusplus

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import be.mygod.pogoplusplus.util.findString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class BluetoothPairingService : AccessibilityService() {
    companion object {
        private const val PACKAGE_SETTINGS = "com.android.settings"
        // found in Realme and potentially other ColorOS
        private const val PACKAGE_SETTINGS_COLOROS = "com.coloros.wirelesssettings"
        // found in Oppo and OnePlus
        private const val PACKAGE_SETTINGS_OPPO = "com.oplus.wirelesssettings"

        var instance: BluetoothPairingService? = null
            private set(value) {
                field = value
                MainPreferenceFragment.instance?.updateSwitches()
            }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.d("BluetoothPairingService started")
        instance = this
        tryConfirm()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                onNotification(event.parcelableData as? Notification ?: return, event.packageName)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val source = if (Build.VERSION.SDK_INT >= 33) event.getSource(0) else event.source
                tryConfirm(source ?: return)
            }
            else -> Timber.e(Exception("Unknown event ${event.eventType}"))
        }
    }

    fun onNotification(notification: Notification, packageName: CharSequence): Boolean {
        when (packageName) {
            PACKAGE_SETTINGS, PACKAGE_SETTINGS_COLOROS, PACKAGE_SETTINGS_OPPO -> { }
            else -> return false
        }
        when (notification.channelId) {
            "bluetooth_notification_channel",
            "Wireless_bt_channel",  // Found for PACKAGE_SETTINGS_COLOROS
            -> { }
            else -> return true
        }
        if (notification.extras.getString(Notification.EXTRA_TEXT)?.contains(
                SfidaManager.DEVICE_NAME_PGP) == true) try {
            var intent = notification.actions?.firstOrNull()?.actionIntent
            if (intent == null) {
                intent = notification.contentIntent
                if (intent == null) {
                    Timber.w(Exception("Notification found but no actionable found"))
                    return true
                }
            }
            intent.send()
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            setTimeout()
        } catch (_: PendingIntent.CanceledException) { }
        return true
    }

    // workaround for some devices not firing TYPE_WINDOW_STATE_CHANGED
    private var job: Job? = null
    private fun setTimeout() {
        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main.immediate) {
            delay(1000)
            val node = tryConfirm()
            if (node != null) Timber.w("TYPE_WINDOW_STATE_CHANGED not fired: $node")
            job = null
        }
    }
    private fun resetTimeout() {
        job?.cancel()
        job = null
    }

    private fun tryConfirm(): AccessibilityNodeInfo? {
        for (window in windows) {
            val root = if (Build.VERSION.SDK_INT >= 33) window.getRoot(0) else window.root
            tryConfirm(root ?: continue)?.let { return it }
        }
        return null
    }
    private fun tryConfirm(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val node = tryLocateById(root) ?: tryLocateByText(root) ?: return null
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        resetTimeout()
        return node
    }
    private fun tryLocateById(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var confirm = root.findAccessibilityNodeInfosByViewId("android:id/button1")
        when (confirm.size) {
            0 -> {
                // Some devices (eg Samsung) use AppCompat (?) AlertDialog and "OK" instead of "Pair"
                confirm = root.findAccessibilityNodeInfosByViewId("${root.packageName}:id/button1")
                if (confirm.size != 1) return null
            }
            1 -> { }
            else -> return null
        }
        var title = root.findAccessibilityNodeInfosByViewId("${root.packageName}:id/alertTitle")
        when (title.size) {
            0 -> {
                title = root.findAccessibilityNodeInfosByViewId("android:id/alertTitle")
                if (title.size == 1 && title[0].text.contains(SfidaManager.DEVICE_NAME_PGP)) {
                    // Some devices (eg Huawei) use Android AlertDialog instead of AppCompat
                    Timber.w("Locate title success: ${title[0].text}")
                    return confirm[0]
                }
            }
            1 -> if (title[0].text.contains(SfidaManager.DEVICE_NAME_PGP)) return confirm[0]
        }
        // Some devices (eg Samsung) put device name in message (#6)
        val message = root.findAccessibilityNodeInfosByViewId("${root.packageName}:id/message")
        if (message.size != 1 || !message[0].text.contains(SfidaManager.DEVICE_NAME_PGP)) return null
        else Timber.w("Locate message success: ${message[0].text}")
        return confirm[0]
    }
    private fun tryLocateByText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val packageName = root.packageName?.toString()
        if (packageName == null) {
            Timber.w(NullPointerException("packageName is null: $root"))
            return null
        }
        val resources = try {
            packageManager.getResourcesForApplication(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            // ignore if the package is not found, or if Android prevents querying because it's not system app
            return null
        }
        val confirmText = resources.findString("bluetooth_pairing_accept", packageName) ?: return null
        val confirm = root.findAccessibilityNodeInfosByText(confirmText).filter {
            confirmText.equals(it.text?.toString(), true)
        }
        if (confirm.size != 1) return null
        val promptText = resources.findString(
            "bluetooth_pairing_request", packageName, SfidaManager.DEVICE_NAME_PGP)
        val prompt = root.findAccessibilityNodeInfosByText(promptText).filter { it.text == promptText }
        if (prompt.isEmpty()) {
            // Some ROM uses nonstandard pair text, like ColorOS seems to use the entire device name as a textview
            val deviceName = root.findAccessibilityNodeInfosByText(SfidaManager.DEVICE_NAME_PGP)
            if (deviceName.none { it.text == SfidaManager.DEVICE_NAME_PGP }) {
                if (deviceName.isNotEmpty()) Timber.w(Exception("Locate device name suspect: $packageName; " +
                        confirm[0].viewIdResourceName + "; " +
                        deviceName.joinToString { "${it.viewIdResourceName}: ${it.text}" }))
                return null
            }
            Timber.w(Exception("Locate device name via text success: $packageName; ${confirm[0].viewIdResourceName}; " +
                    deviceName.joinToString { it.viewIdResourceName }))
        } else Timber.w(Exception("Locate standard via text success: $packageName; ${confirm[0].viewIdResourceName}; " +
                prompt.joinToString { it.viewIdResourceName }))
        return confirm[0]
    }

    override fun onInterrupt() {
        Timber.d("BluetoothPairingService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Timber.d("BluetoothPairingService shutting down")
        resetTimeout()
        return super.onUnbind(intent)
    }
}
