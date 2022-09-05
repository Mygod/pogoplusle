package be.mygod.pogoplusplus

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

@TargetApi(26)
class BluetoothPairingService : AccessibilityService() {
    companion object {
        const val PACKAGE_SETTINGS = "com.android.settings"

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
        for (window in windows) {
            val root = if (Build.VERSION.SDK_INT >= 33) window.getRoot(0) else window.root
            if (root?.packageName == PACKAGE_SETTINGS && tryConfirm(root)) return
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                onNotification(event.parcelableData as? Notification ?: return)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val source = if (Build.VERSION.SDK_INT >= 33) event.getSource(0) else event.source
                tryConfirm(source ?: return)
            }
            else -> Timber.e(Exception("Unknown event ${event.eventType}"))
        }
    }

    fun onNotification(notification: Notification) {
        if (notification.channelId == "bluetooth_notification_channel" && notification.extras.getString(
                Notification.EXTRA_TEXT)?.contains(BluetoothReceiver.DEVICE_NAME_PGP) == true) try {
            var intent = notification.actions?.firstOrNull()?.actionIntent
            if (intent == null) {
                intent = notification.contentIntent
                Timber.w(Exception("${notification.actions?.joinToString()}; $intent"))
                if (intent == null) return
            }
            intent.send()
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } catch (_: PendingIntent.CanceledException) { }
    }

    private fun tryConfirm(root: AccessibilityNodeInfo): Boolean {
        val node = tryLocateById(root) ?: tryLocateByText(root) ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return true
    }
    private fun tryLocateById(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val confirm = root.findAccessibilityNodeInfosByViewId("android:id/button1")
        if (confirm.size != 1) return null
        val title = root.findAccessibilityNodeInfosByViewId("$PACKAGE_SETTINGS:id/alertTitle")
        if (title.size != 1 || !title[0].text.contains(BluetoothReceiver.DEVICE_NAME_PGP)) {
            // Some devices (eg Samsung) put device name in message (#6)
            val message = root.findAccessibilityNodeInfosByViewId("$PACKAGE_SETTINGS:id/message")
            if (message.size != 1 || !message[0].text.contains(BluetoothReceiver.DEVICE_NAME_PGP)) return null
        }
        return confirm[0]
    }
    private fun tryLocateByText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val resources = packageManager.getResourcesForApplication(PACKAGE_SETTINGS)
        val confirmText = resources.getString(resources.getIdentifier(
            "bluetooth_pairing_accept", "string", PACKAGE_SETTINGS))
        val confirm = root.findAccessibilityNodeInfosByText(confirmText).filter {
            confirmText.equals(it.text?.toString(), true)
        }
        if (confirm.size != 1) return null
        val promptText = resources.getString(resources.getIdentifier(
            "bluetooth_pairing_request", "string", PACKAGE_SETTINGS), BluetoothReceiver.DEVICE_NAME_PGP)
        val prompt = root.findAccessibilityNodeInfosByText(promptText).filter { it.text == promptText }
        if (prompt.isEmpty()) {
            // Some ROM uses nonstandard pair text, like ColorOS seems to use the entire device name as a textview
            val deviceName = root.findAccessibilityNodeInfosByText(BluetoothReceiver.DEVICE_NAME_PGP)
                .filter { it.text == BluetoothReceiver.DEVICE_NAME_PGP }
            Timber.w(Exception("Locate device name via text success: ${confirm[0].viewIdResourceName}; " +
                    deviceName.joinToString { it.viewIdResourceName }))
            if (deviceName.isEmpty()) return null
        } else Timber.w(Exception("Locate standard Pair via text success: ${confirm[0].viewIdResourceName}; " +
                prompt.joinToString { it.viewIdResourceName }))
        return confirm[0]
    }

    override fun onInterrupt() {
        Timber.d("BluetoothPairingService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Timber.d("BluetoothPairingService shutting down")
        return super.onUnbind(intent)
    }
}
