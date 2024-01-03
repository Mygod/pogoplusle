package be.mygod.pogoplusplus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import be.mygod.pogoplusplus.App.Companion.app
import be.mygod.pogoplusplus.util.findString
import timber.log.Timber

class GameNotificationService : NotificationListenerService() {
    companion object {
        private const val CHANNEL_AUXILIARY_DISCONNECTED = "auxiliary_disconnected"
        private const val CHANNEL_ITEM_FULL = "item_inventory_full"
        private const val CHANNEL_POKEMON_FULL = "pokemon_full"
        private const val CHANNEL_NO_BALL = "out_of_pokeballs"
        private const val CHANNEL_SPIN_FAIL = "spin_fail"
        private const val CHANNEL_CONNECTION_STATUS = "connection_pending"
        private const val CHANNEL_INACTIVE_TIMEOUT = "inactive_timeout"
        private const val PACKAGE_POKEMON_GO = "com.nianticlabs.pokemongo"
        private const val PACKAGE_POKEMON_GO_ARES = "com.nianticlabs.pokemongo.ares"

        private const val NOTIFICATION_AUXILIARY_DISCONNECTED = 1
        private const val NOTIFICATION_ITEM_FULL = 2
        private const val NOTIFICATION_POKEMON_FULL = 3
        private const val NOTIFICATION_NO_BALL = 4
        private const val NOTIFICATION_SPIN_FAIL = 5
        private const val NOTIFICATION_CONNECTION_STATUS = 6

        val gameIntent get() = listOf(PACKAGE_POKEMON_GO, PACKAGE_POKEMON_GO_ARES)
            .mapNotNull(app.packageManager::getLaunchIntentForPackage).let { list ->
                when (list.size) {
                    0 -> Intent(Intent.ACTION_CHOOSER).putExtra(Intent.EXTRA_INTENT, Intent())
                    1 -> list.first()
                    else -> Intent(Intent.ACTION_CHOOSER).apply {
                        putExtra(Intent.EXTRA_INTENT, list.first())
                        putExtra(Intent.EXTRA_ALTERNATE_INTENTS, list.drop(1).toTypedArray())
                    }
                }
            }


        private fun makeNotificationChannel(id: String, @StringRes name: Int) = NotificationChannel(
            id, app.getText(name), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableLights(true)
            lightColor = Color.RED
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        fun updateNotificationChannels() = notificationManager.createNotificationChannels(listOf(
            makeNotificationChannel(CHANNEL_AUXILIARY_DISCONNECTED,
                R.string.notification_channel_auxiliary_disconnected),
            makeNotificationChannel(CHANNEL_ITEM_FULL, R.string.notification_channel_item_full),
            makeNotificationChannel(CHANNEL_POKEMON_FULL, R.string.notification_channel_pokemon_full),
            makeNotificationChannel(CHANNEL_NO_BALL, R.string.notification_channel_no_ball),
            makeNotificationChannel(CHANNEL_SPIN_FAIL, R.string.notification_channel_spin_fail),
            NotificationChannel(CHANNEL_CONNECTION_STATUS, app.getText(
                R.string.notification_channel_connection_status), NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            },
            makeNotificationChannel(CHANNEL_INACTIVE_TIMEOUT, R.string.notification_channel_inactive_timeout),
        ))

        private val bluetoothAdapter by lazy { app.getSystemService<BluetoothManager>()!!.adapter }
        private val notificationManager by lazy { app.getSystemService<NotificationManager>()!! }
        private fun pushNotification(
            id: Int,
            channel: String,
            title: CharSequence,
            @DrawableRes icon: Int,
            packageName: String? = null,
            block: (Notification.Builder.() -> Unit)? = null,
        ) = notificationManager.notify(id, Notification.Builder(app, channel).apply {
            setCategory(Notification.CATEGORY_STATUS)
            setContentTitle(title)
            setGroup(channel)
            setSmallIcon(icon)
            setContentIntent(PendingIntent.getActivity(app, 0,
                if (packageName == null) gameIntent else app.packageManager.getLaunchIntentForPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            setShowWhen(true)
            setAutoCancel(true)
            setVisibility(Notification.VISIBILITY_PUBLIC)
            setColor(app.getColor(R.color.primaryColor))
            block?.invoke(this)
        }.build())

        var isRunning = false
            private set(value) {
                field = value
                MainPreferenceFragment.instance?.updateSwitches()
            }

        private fun setTimeoutIfEnabled() {
            if (notificationManager.getNotificationChannel(CHANNEL_INACTIVE_TIMEOUT)
                ?.run { importance != NotificationManager.IMPORTANCE_NONE } == true) {
                SfidaTimeoutReceiver.reportConnection()
            }
        }
        private fun updateConnectionStatus(stats: SfidaSessionManager.Stats) {
            notificationManager.cancel(NOTIFICATION_AUXILIARY_DISCONNECTED)
            notificationManager.notify(NOTIFICATION_CONNECTION_STATUS, Notification.Builder(app,
                CHANNEL_CONNECTION_STATUS).apply {
                setCategory(Notification.CATEGORY_STATUS)
                setContentTitle(app.getText(R.string.notification_title_auxiliary_connected_default))
                setGroup(CHANNEL_CONNECTION_STATUS)
                setSmallIcon(R.drawable.ic_maps_mode_of_travel)
                setContentIntent(PendingIntent.getActivity(app, 0, gameIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                setShowWhen(true)
                setWhen(stats.startTime)
                setUsesChronometer(true)
                setLocalOnly(true)
                setVisibility(Notification.VISIBILITY_PUBLIC)
                if (stats.deviceAddress.isNotEmpty()) addAction(Notification.Action.Builder(
                    Icon.createWithResource(app, com.google.android.material.R.drawable.ic_m3_chip_close),
                    app.getText(R.string.notification_action_disconnect),
                    PendingIntent.getBroadcast(app, 0, Intent(app, SfidaDisconnectReceiver::class.java).apply {
                        data = Uri.fromParts("mac", stats.deviceAddress, null)  // to differentiate as ID
                        putExtra(BluetoothDevice.EXTRA_DEVICE, bluetoothAdapter.getRemoteDevice(stats.deviceAddress))
                    }, PendingIntent.FLAG_IMMUTABLE)).build())
                setColor(app.getColor(R.color.primaryColor))
                setOnlyAlertOnce(true)
                if (Build.VERSION.SDK_INT >= 34) setOngoing(true)
                setPublicVersion(build().clone())
                setVisibility(Notification.VISIBILITY_PRIVATE)
                setContentTitle(app.getString(R.string.notification_title_auxiliary_connected, stats.deviceName ?: "❓"))
                setContentText(stats.stats)
            }.build())
        }
        fun onAuxiliaryConnected(device: Pair<BluetoothDevice, String?>) {
            updateConnectionStatus(SfidaSessionManager.onConnect(device))
            setTimeoutIfEnabled()
        }
        fun onAuxiliaryDisconnected(device: Pair<BluetoothDevice, String?>? = null, packageName: String? = null) {
            val stats = SfidaSessionManager.onDisconnect(device)
            notificationManager.cancel(NOTIFICATION_CONNECTION_STATUS)
            pushNotification(NOTIFICATION_AUXILIARY_DISCONNECTED, CHANNEL_AUXILIARY_DISCONNECTED,
                app.getText(R.string.notification_title_auxiliary_disconnected_default),
                R.drawable.ic_device_bluetooth_disabled, packageName) {
                setOnlyAlertOnce(true)
                setPublicVersion(build().clone())
                setVisibility(Notification.VISIBILITY_PRIVATE)
                stats.deviceName?.let {
                    setContentTitle(app.getString(R.string.notification_title_auxiliary_disconnected, it))
                }
                setContentText(stats.stats)
                setSubText(DateUtils.formatElapsedTime((System.currentTimeMillis() - stats.startTime) / 1000))
            }
            SfidaTimeoutReceiver.reportDisconnection()
        }
        fun onAuxiliaryTimeout() = pushNotification(NOTIFICATION_AUXILIARY_DISCONNECTED, CHANNEL_INACTIVE_TIMEOUT,
            app.getText(R.string.notification_channel_inactive_timeout), R.drawable.ic_notification_sync_problem) {
            addAction(Notification.Action.Builder(
                Icon.createWithResource(app, com.google.android.material.R.drawable.ic_m3_chip_close),
                app.getText(R.string.notification_action_disconnect), PendingIntent.getBroadcast(app, 0,
                    Intent(app, SfidaDisconnectReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)).build())
        }

        private fun isInterested(sbn: StatusBarNotification) = sbn.notification.channelId == sbn.packageName &&
                (sbn.packageName == PACKAGE_POKEMON_GO || sbn.packageName == PACKAGE_POKEMON_GO_ARES)
    }

    override fun onListenerConnected() {
        isRunning = true
    }
    override fun onListenerDisconnected() {
        isRunning = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (BluetoothPairingService.instance?.onNotification(sbn.notification, sbn.packageName) == true ||
            !isInterested(sbn)) return
        val text = sbn.notification.extras.getString(Notification.EXTRA_TEXT)
        Timber.d("PGP notification updated @ ${sbn.postTime}: $text")
        if (text.isNullOrEmpty()) {
            updateConnectionStatus(SfidaSessionManager.onConnect())
            return setTimeoutIfEnabled()
        }
        val resources = try {
            packageManager.getResourcesForApplication(sbn.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return
        }
        if (text == resources.findString("Disconnecting_Companion_Device", sbn.packageName)) {
            return onAuxiliaryDisconnected()
        }
        val shouldUpdate = SfidaManager.isConnected != false
        if (shouldUpdate) setTimeoutIfEnabled()
        var str = resources.findString("Item_Inventory_Full", sbn.packageName)
        if (text == str) return pushNotification(NOTIFICATION_ITEM_FULL, CHANNEL_ITEM_FULL, str,
            R.drawable.ic_action_shopping_bag, sbn.packageName) {
            setOnlyAlertOnce(!notificationManager.getNotificationChannel(CHANNEL_ITEM_FULL).canBypassDnd())
        }
        str = resources.findString("Pokemon_Inventory_Full", sbn.packageName)
        if (text == str) return pushNotification(NOTIFICATION_POKEMON_FULL, CHANNEL_POKEMON_FULL, str,
            R.drawable.ic_notification_disc_full, sbn.packageName)
        str = resources.findString("Out_Of_Pokeballs", sbn.packageName)
        when (text) {
            str -> pushNotification(NOTIFICATION_NO_BALL, CHANNEL_NO_BALL, str,
                R.drawable.ic_action_hide_source, sbn.packageName)
            resources.findString("Captured_Pokemon", sbn.packageName) -> {
                notificationManager.cancel(NOTIFICATION_POKEMON_FULL)
                notificationManager.cancel(NOTIFICATION_NO_BALL)
                val stats = SfidaSessionManager.onCaptured()
                if (shouldUpdate) updateConnectionStatus(stats)
            }
            resources.findString("Pokemon_Escaped", sbn.packageName) -> {
                notificationManager.cancel(NOTIFICATION_POKEMON_FULL)
                notificationManager.cancel(NOTIFICATION_NO_BALL)
                val stats = SfidaSessionManager.onEscaped()
                if (shouldUpdate) updateConnectionStatus(stats)
            }
            resources.findString("Retrieved_an_Item", sbn.packageName, "") -> { // remove %s if present
                notificationManager.cancel(NOTIFICATION_ITEM_FULL)
                notificationManager.cancel(NOTIFICATION_SPIN_FAIL)
                val stats = SfidaSessionManager.onSpin(1)
                if (shouldUpdate) updateConnectionStatus(stats)
            }
            resources.findString("Pokestop_Cooldown", sbn.packageName),
            resources.findString("Pokestop_Out_Of_Range", sbn.packageName) -> { }
            else -> {
                val split = resources.findString("Retrieved_Items", sbn.packageName)?.split("%s", limit = 2)
                if (split?.size != 2) {
                    Timber.e(Exception("Unrecognized Retrieved_Items ${split?.getOrNull(0)}"))
                    return
                }
                if (text.startsWith(split[0]) && text.endsWith(split[1])) {
                    val items = try {
                        text.substring(split[0].length, text.length - split[1].length).toLong()
                    } catch (e: NumberFormatException) {
                        return Timber.e(Exception("Unrecognized notification text: $text", e))
                    }
                    notificationManager.cancel(NOTIFICATION_ITEM_FULL)
                    if (items != 0L) {
                        notificationManager.cancel(NOTIFICATION_SPIN_FAIL)
                        val stats = SfidaSessionManager.onSpin(items)
                        if (shouldUpdate) updateConnectionStatus(stats)
                    } else pushNotification(NOTIFICATION_SPIN_FAIL, CHANNEL_SPIN_FAIL, text,
                        R.drawable.ic_alert_error_outline, sbn.packageName)
                    // Ignore Samsung stupid shit: https://github.com/universal9611-dev/framework-res/blob/4645162b385057fb77ee91565c802cdf528613da/res/values/strings.xml#L2895
                } else if (Build.VERSION.SDK_INT < 33 || text != resources.findString("sanitized_content_text_sf",
                        "android")) Timber.e(Exception("Unrecognized notification text: $text"))
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        when (reason) {
            REASON_PACKAGE_CHANGED,
            REASON_USER_STOPPED,
            REASON_APP_CANCEL,
            REASON_PACKAGE_SUSPENDED,
            REASON_PROFILE_TURNED_OFF,
            REASON_CLEAR_DATA -> { }
            REASON_APP_CANCEL_ALL -> if (Build.VERSION.SDK_INT < 31) return
            else -> return
        }
        if (isInterested(sbn ?: return)) onAuxiliaryDisconnected()
    }
}
