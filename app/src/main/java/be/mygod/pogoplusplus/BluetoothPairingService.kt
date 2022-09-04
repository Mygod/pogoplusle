package be.mygod.pogoplusplus

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
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
        tryConfirm()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                onNotification(event.parcelableData as? Notification ?: return)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> tryConfirm()
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

    private fun tryConfirm() {
        val root = rootInActiveWindow ?: return
        (tryLocateById(root) ?: tryLocateByText(root))?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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
        val prompt = root.findAccessibilityNodeInfosByText(resources.getString(resources.getIdentifier(
            "bluetooth_pairing_request", "string", PACKAGE_SETTINGS), BluetoothReceiver.DEVICE_NAME_PGP))
        Timber.w(Exception("Locate Pair via text success: ${confirm[0].viewIdResourceName}; " +
                prompt.joinToString { it.viewIdResourceName }))
        if (prompt.isEmpty()) return null
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
