package be.mygod.pogoplusplus

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi

@RequiresApi(26)
class BluetoothPairingService : AccessibilityService() {
    private var notificationsCaught = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val notification = event.parcelableData as Notification
                if (notification.channelId != "bluetooth_notification_channel" ||
                    notification.extras.getString(Notification.EXTRA_TEXT)?.contains("Pokemon GO Plus") != true) {
                    return
                }
                notificationsCaught++
                notification.actions[0].actionIntent.send()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (notificationsCaught == 0 ||
                    event.contentChangeTypes != AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED) return
                val root = rootInActiveWindow ?: return
                if (root.findAccessibilityNodeInfosByViewId(
                        "com.android.settings:id/phonebook_sharing_message_confirm_pin").size != 1) return
                val confirm = root.findAccessibilityNodeInfosByViewId("android:id/button1")
                if (confirm.size != 1) return
                val title = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/alertTitle")
                if (title.size != 1 || !title[0].text.contains("Pokemon GO Plus")) return
                notificationsCaught--
                confirm[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            else -> Log.e("BluetoothPairingService", "Unknown event ${event.eventType}")
        }
    }

    override fun onInterrupt() {
        Log.d("BluetoothPairingService", "interrupted")
    }
}
