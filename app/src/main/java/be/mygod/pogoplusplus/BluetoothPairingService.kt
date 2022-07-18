package be.mygod.pogoplusplus

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.app.Notification
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

@TargetApi(26)
class BluetoothPairingService : AccessibilityService() {
    companion object {
        const val PACKAGE_SETTINGS = "com.android.settings"

        var instance: BluetoothPairingService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.d("BluetoothPairingService started")
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                onNotification(event.parcelableData as? Notification ?: return)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val root = rootInActiveWindow ?: return
                if (root.findAccessibilityNodeInfosByViewId(
                        "$PACKAGE_SETTINGS:id/phonebook_sharing_message_confirm_pin").size != 1) return
                val confirm = root.findAccessibilityNodeInfosByViewId("android:id/button1")
                if (confirm.size != 1) return
                val title = root.findAccessibilityNodeInfosByViewId("$PACKAGE_SETTINGS:id/alertTitle")
                if (title.size != 1 || !title[0].text.contains(BluetoothReceiver.DEVICE_NAME_PGP)) return
                confirm[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            else -> Timber.e(Exception("Unknown event ${event.eventType}"))
        }
    }

    fun onNotification(notification: Notification) {
        if (notification.channelId != "bluetooth_notification_channel" || notification.extras.getString(
                Notification.EXTRA_TEXT)?.contains(BluetoothReceiver.DEVICE_NAME_PGP) != true) return
        notification.actions[0].actionIntent.send()
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
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
