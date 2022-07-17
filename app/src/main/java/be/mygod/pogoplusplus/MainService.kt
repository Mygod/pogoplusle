package be.mygod.pogoplusplus

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import be.mygod.pogoplusplus.App.Companion.app
import timber.log.Timber

class MainService : AccessibilityService() {
    companion object {
        private const val CHANNEL_AUXILIARY_DISCONNECTED = "auxiliary_disconnected"
        private const val CHANNEL_ITEM_FULL = "item_inventory_full"
        private const val CHANNEL_POKEMON_FULL = "pokemon_full"
        private const val CHANNEL_NO_BALL = "out_of_pokeballs"
        private const val DEVICE_NAME_PBP = "Pokemon PBP"
        private const val DEVICE_NAME_PGP = "Pokemon GO Plus"
        private const val PACKAGE_POKEMON_GO = "com.nianticlabs.pokemongo"
        private const val PACKAGE_SETTINGS = "com.android.settings"

        private const val NOTIFICATION_AUXILIARY_DISCONNECTED = 1
        private const val NOTIFICATION_ITEM_FULL = 2
        private const val NOTIFICATION_POKEMON_FULL = 3
        private const val NOTIFICATION_NO_BALL = 4

        private val notificationManager by lazy { app.getSystemService<NotificationManager>()!! }
        @RequiresApi(26)
        private fun createNotificationChannel(id: String, name: CharSequence) = NotificationChannel(id, name,
            NotificationManager.IMPORTANCE_HIGH).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(this)
        }
        fun updateNotificationChannels() {
            if (Build.VERSION.SDK_INT >= 26) {
                createNotificationChannel(CHANNEL_AUXILIARY_DISCONNECTED, "Pokémon GO Plus session ended")
                createNotificationChannel(CHANNEL_ITEM_FULL, "Bag is full")
                createNotificationChannel(CHANNEL_POKEMON_FULL, "No more room for Pokémon")
                createNotificationChannel(CHANNEL_NO_BALL, "Out of Poké Balls")
            }
        }

        val gameIntent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(PACKAGE_POKEMON_GO, "com.nianticproject.holoholo.libholoholo.unity.UnityMainActivity")
        }

        var isRunning = false
            private set
    }

    private var notificationsCaught = 0
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent) {
            if (Build.VERSION.SDK_INT >= 33 && intent.getIntExtra(BluetoothDevice.EXTRA_TRANSPORT,
                    BluetoothDevice.ERROR) != BluetoothDevice.TRANSPORT_LE) return
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)!!
            val name = device.name
            val type = device.type
            val bluetoothClass = device.bluetoothClass
            val uuids = device.uuids
            val shouldSkip = type != BluetoothDevice.DEVICE_TYPE_LE || uuids != null ||
                    bluetoothClass.hashCode() != BluetoothClass.Device.Major.UNCATEGORIZED ||
                    !device.address.startsWith("98:B6:E9:", true) ||
                    name != DEVICE_NAME_PBP && name != DEVICE_NAME_PGP
            Timber.d("${intent.action}: ${device.address}, $name, $type, $bluetoothClass, $uuids, $shouldSkip")
            if (shouldSkip) return
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> onAuxiliaryConnected()
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> onAuxiliaryDisconnected(name)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        Timber.d("MainService started")
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.packageName) {
            PACKAGE_POKEMON_GO -> onPokemonGoEvent(event)
            PACKAGE_SETTINGS -> if (Build.VERSION.SDK_INT >= 26) onSettingsEvent(event)
        }
    }

    private fun onPokemonGoEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val notification = event.parcelableData as Notification
                val resources = packageManager.getResourcesForApplication(PACKAGE_POKEMON_GO)
                fun getPogoString(name: String) = resources.getString(resources.getIdentifier(
                    name, "string", PACKAGE_POKEMON_GO))
                val title = getPogoString("Pokemon_Go_Plus")
                if (notification.extras.getString(Notification.EXTRA_TITLE) != title) return
                onAuxiliaryConnected()
                val text = notification.extras.getString(Notification.EXTRA_TEXT)
                if (text.isNullOrEmpty()) return
                if (text == getPogoString("Disconnecting_GO_Plus")) return onAuxiliaryDisconnected()
                var str = getPogoString("Item_Inventory_Full")
                if (text == str) return pushNotification(NOTIFICATION_ITEM_FULL, CHANNEL_ITEM_FULL, str,
                    R.drawable.ic_action_shopping_bag)
                str = getPogoString("Pokemon_Inventory_Full")
                if (text == str) return pushNotification(NOTIFICATION_POKEMON_FULL, CHANNEL_POKEMON_FULL, str,
                    R.drawable.ic_notification_disc_full)
                str = getPogoString("Out_Of_Pokeballs")
                when (text) {
                    str -> pushNotification(NOTIFICATION_NO_BALL, CHANNEL_NO_BALL, str,
                        R.drawable.ic_action_hide_source)
                    getPogoString("Captured_Pokemon") -> {
                        notificationManager.cancel(NOTIFICATION_POKEMON_FULL)
                        notificationManager.cancel(NOTIFICATION_NO_BALL)
                    }
                    getPogoString("Retrieved_an_Item") -> notificationManager.cancel(NOTIFICATION_ITEM_FULL)
                    else -> {
                        val split = getPogoString("Retrieved_Items").split("%s", limit = 2)
                        val matches = if (split.size == 1) {
                            Timber.e(Exception("Unrecognized Retrieved_Items ${split[0]}"))
                            false
                        } else text.startsWith(split[0]) && text.endsWith(split[1])
                        if (matches) {
                            notificationManager.cancel(NOTIFICATION_ITEM_FULL)
                        } else Timber.e(Exception("Unrecognized notification text: $text"))
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> { }
            else -> Timber.e(Exception("Unknown event ${event.eventType}"))
        }
    }

    @RequiresApi(26)
    private fun onSettingsEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val notification = event.parcelableData as Notification
                if (notification.channelId != "bluetooth_notification_channel" ||
                    notification.extras.getString(Notification.EXTRA_TEXT)?.contains(DEVICE_NAME_PGP) != true) {
                    return
                }
                notificationsCaught++
                notification.actions[0].actionIntent.send()
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (notificationsCaught == 0 ||
                    event.contentChangeTypes != AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED) return
                val root = rootInActiveWindow ?: return
                if (root.findAccessibilityNodeInfosByViewId(
                        "$PACKAGE_SETTINGS:id/phonebook_sharing_message_confirm_pin").size != 1) return
                val confirm = root.findAccessibilityNodeInfosByViewId("android:id/button1")
                if (confirm.size != 1) return
                val title = root.findAccessibilityNodeInfosByViewId("$PACKAGE_SETTINGS:id/alertTitle")
                if (title.size != 1 || !title[0].text.contains(DEVICE_NAME_PGP)) return
                notificationsCaught--
                confirm[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            else -> Timber.e(Exception("Unknown event ${event.eventType}"))
        }
    }

    override fun onInterrupt() {
        Timber.d("MainService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        unregisterReceiver(receiver)
        Timber.d("MainService shutting down")
        return super.onUnbind(intent)
    }

    private fun pushNotification(
        id: Int,
        channel: String,
        title: CharSequence,
        @DrawableRes icon: Int,
    ) = notificationManager.notify(id, NotificationCompat.Builder(this, channel).apply {
        setCategory(NotificationCompat.CATEGORY_STATUS)
        setContentTitle(title)
        setSmallIcon(icon)
        setContentIntent(PendingIntent.getActivity(this@MainService, 0, gameIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        setShowWhen(true)
        setAutoCancel(true)
        setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        color = getColor(R.color.primaryColor)
        priority = NotificationCompat.PRIORITY_MAX
    }.build())

    private fun onAuxiliaryConnected() = notificationManager.cancel(NOTIFICATION_AUXILIARY_DISCONNECTED)
    private fun onAuxiliaryDisconnected(name: String = DEVICE_NAME_PGP) = pushNotification(
        NOTIFICATION_AUXILIARY_DISCONNECTED, CHANNEL_AUXILIARY_DISCONNECTED, "$name disconnected",
        R.drawable.ic_device_bluetooth_disabled)
}
