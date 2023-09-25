package be.mygod.pogoplusplus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
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
        private const val CHANNEL_CONNECTION_PENDING = "connection_pending"
        private const val CHANNEL_INACTIVE_TIMEOUT = "inactive_timeout"
        private const val PACKAGE_POKEMON_GO = "com.nianticlabs.pokemongo"
        private const val PACKAGE_POKEMON_GO_ARES = "com.nianticlabs.pokemongo.ares"

        private const val NOTIFICATION_AUXILIARY_DISCONNECTED = 1
        private const val NOTIFICATION_ITEM_FULL = 2
        private const val NOTIFICATION_POKEMON_FULL = 3
        private const val NOTIFICATION_NO_BALL = 4
        private const val NOTIFICATION_SPIN_FAIL = 5
        private const val NOTIFICATION_CONNECTION_PENDING = 6

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

        private val powerManager by lazy { app.getSystemService<PowerManager>()!! }
        @get:RequiresApi(31)
        val foregroundServiceStartNotAllowedPackage get() = when {
            app.packageManager.getLaunchIntentForPackage(PACKAGE_POKEMON_GO) != null &&
                    !powerManager.isIgnoringBatteryOptimizations(PACKAGE_POKEMON_GO) -> PACKAGE_POKEMON_GO
            app.packageManager.getLaunchIntentForPackage(PACKAGE_POKEMON_GO_ARES) != null &&
                    !powerManager.isIgnoringBatteryOptimizations(PACKAGE_POKEMON_GO_ARES) -> PACKAGE_POKEMON_GO_ARES
            else -> null
        }

        @RequiresApi(26)
        private fun makeNotificationChannel(id: String, @StringRes name: Int) = NotificationChannel(
            id, app.getText(name), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableLights(true)
            lightColor = Color.RED
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        fun updateNotificationChannels() {
            if (Build.VERSION.SDK_INT >= 26) notificationManager.createNotificationChannels(listOf(
                makeNotificationChannel(CHANNEL_AUXILIARY_DISCONNECTED,
                    R.string.notification_channel_auxiliary_disconnected),
                makeNotificationChannel(CHANNEL_ITEM_FULL, R.string.notification_channel_item_full),
                makeNotificationChannel(CHANNEL_POKEMON_FULL, R.string.notification_channel_pokemon_full),
                makeNotificationChannel(CHANNEL_NO_BALL, R.string.notification_channel_no_ball),
                makeNotificationChannel(CHANNEL_SPIN_FAIL, R.string.notification_channel_spin_fail),
                NotificationChannel(CHANNEL_CONNECTION_PENDING, app.getText(
                    R.string.notification_channel_connection_pending), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                },
                makeNotificationChannel(CHANNEL_INACTIVE_TIMEOUT, R.string.notification_channel_inactive_timeout),
            ))
        }

        private val notificationManager by lazy { app.getSystemService<NotificationManager>()!! }
        private fun pushNotification(
            id: Int,
            channel: String,
            title: CharSequence,
            @DrawableRes icon: Int,
            packageName: String? = null,
            block: (NotificationCompat.Builder.() -> Unit)? = null,
        ) = notificationManager.notify(id, NotificationCompat.Builder(app, channel).apply {
            setCategory(NotificationCompat.CATEGORY_STATUS)
            setContentTitle(title)
            setGroup(channel)
            setSmallIcon(icon)
            setContentIntent(PendingIntent.getActivity(app, 0,
                if (packageName == null) gameIntent else app.packageManager.getLaunchIntentForPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            setShowWhen(true)
            setAutoCancel(true)
            setLights(Color.RED, 500, 500)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            color = app.getColor(R.color.primaryColor)
            priority = NotificationCompat.PRIORITY_MAX
            block?.invoke(this)
        }.build())

        var isRunning = false
            private set(value) {
                field = value
                MainPreferenceFragment.instance?.updateSwitches()
            }

        private fun setTimeoutIfEnabled() {
            if (Build.VERSION.SDK_INT < 26 || notificationManager.getNotificationChannel(CHANNEL_INACTIVE_TIMEOUT)
                .importance != NotificationManager.IMPORTANCE_NONE) SfidaTimeoutReceiver.reportConnection()
        }
        fun onAuxiliaryConnected(device: BluetoothDevice, deviceName: String) {
            notificationManager.cancel(NOTIFICATION_AUXILIARY_DISCONNECTED)
            notificationManager.notify(NOTIFICATION_CONNECTION_PENDING, NotificationCompat.Builder(app,
                CHANNEL_CONNECTION_PENDING).apply {
                setCategory(NotificationCompat.CATEGORY_STATUS)
                setContentTitle(app.getText(R.string.notification_title_auxiliary_connected_default))
                setGroup(CHANNEL_CONNECTION_PENDING)
                setSmallIcon(R.drawable.ic_maps_mode_of_travel)
                setContentIntent(PendingIntent.getActivity(app, 0, gameIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                setShowWhen(true)
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                addAction(com.google.android.material.R.drawable.ic_m3_chip_close,
                    app.getText(R.string.notification_action_disconnect),
                    PendingIntent.getBroadcast(app, 0, Intent(app, SfidaDisconnectReceiver::class.java).apply {
                        data = Uri.fromParts("mac", device.address, null)   // to differentiate as ID
                        putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                    }, PendingIntent.FLAG_IMMUTABLE))
                color = app.getColor(R.color.primaryColor)
                setPublicVersion(build())
                setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                setContentTitle(app.getString(R.string.notification_title_auxiliary_connected, deviceName))
            }.build())
            setTimeoutIfEnabled()
        }
        fun onAuxiliaryDisconnected(deviceName: String? = null, packageName: String? = null) {
            notificationManager.cancel(NOTIFICATION_CONNECTION_PENDING)
            pushNotification(NOTIFICATION_AUXILIARY_DISCONNECTED, CHANNEL_AUXILIARY_DISCONNECTED,
                app.getText(R.string.notification_title_auxiliary_disconnected_default),
                R.drawable.ic_device_bluetooth_disabled, packageName) {
                setOnlyAlertOnce(true)
                if (deviceName != null) {
                    setPublicVersion(build())
                    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    setContentTitle(app.getString(R.string.notification_title_auxiliary_disconnected, deviceName))
                }
            }
            SfidaTimeoutReceiver.reportDisconnection()
        }
        fun onAuxiliaryTimeout() = pushNotification(NOTIFICATION_CONNECTION_PENDING, CHANNEL_INACTIVE_TIMEOUT,
            app.getText(R.string.notification_channel_inactive_timeout), R.drawable.ic_notification_sync_problem) {
            addAction(com.google.android.material.R.drawable.ic_m3_chip_close,
                app.getText(R.string.notification_action_disconnect), PendingIntent.getBroadcast(app, 0,
                    Intent(app, SfidaDisconnectReceiver::class.java), PendingIntent.FLAG_IMMUTABLE))
        }

        private fun isInterested(sbn: StatusBarNotification): Boolean {
            if (sbn.packageName != PACKAGE_POKEMON_GO && sbn.packageName != PACKAGE_POKEMON_GO_ARES) return false
            // com.nianticlabs.pokemongoplus.service.BackgroundService.notificationId
            return if (Build.VERSION.SDK_INT < 26) sbn.id == 2000 else sbn.notification.channelId == sbn.packageName
        }
        private fun onConnect() {
            notificationManager.cancel(NOTIFICATION_AUXILIARY_DISCONNECTED)
            notificationManager.cancel(NOTIFICATION_CONNECTION_PENDING)
            setTimeoutIfEnabled()
        }
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
        val text = sbn.notification.extras.getString(NotificationCompat.EXTRA_TEXT)
        Timber.d("PGP notification updated: $text")
        if (text.isNullOrEmpty()) return onConnect()
        val resources = try {
            packageManager.getResourcesForApplication(sbn.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return
        }
        if (text == resources.findString("Disconnecting_Companion_Device", sbn.packageName)) {
            return onAuxiliaryDisconnected()
        }
        if (SfidaManager.isConnected != false) onConnect()
        var str = resources.findString("Item_Inventory_Full", sbn.packageName)
        if (text == str) return pushNotification(NOTIFICATION_ITEM_FULL, CHANNEL_ITEM_FULL, str,
            R.drawable.ic_action_shopping_bag, sbn.packageName) {
            setOnlyAlertOnce(Build.VERSION.SDK_INT < 26 ||
                    !notificationManager.getNotificationChannel(CHANNEL_ITEM_FULL).canBypassDnd())
        }
        str = resources.findString("Pokemon_Inventory_Full", sbn.packageName)
        if (text == str) return pushNotification(NOTIFICATION_POKEMON_FULL, CHANNEL_POKEMON_FULL, str,
            R.drawable.ic_notification_disc_full, sbn.packageName)
        str = resources.findString("Out_Of_Pokeballs", sbn.packageName)
        when (text) {
            str -> pushNotification(NOTIFICATION_NO_BALL, CHANNEL_NO_BALL, str,
                R.drawable.ic_action_hide_source, sbn.packageName)
            resources.findString("Captured_Pokemon", sbn.packageName),
            resources.findString("Pokemon_Escaped", sbn.packageName) -> {
                notificationManager.cancel(NOTIFICATION_POKEMON_FULL)
                notificationManager.cancel(NOTIFICATION_NO_BALL)
            }
            resources.findString("Retrieved_an_Item", sbn.packageName, "") -> { // remove %s if present
                notificationManager.cancel(NOTIFICATION_ITEM_FULL)
                notificationManager.cancel(NOTIFICATION_SPIN_FAIL)
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
                        text.substring(split[0].length, text.length - split[1].length).toInt()
                    } catch (e: NumberFormatException) {
                        return Timber.e(Exception("Unrecognized notification text: $text", e))
                    }
                    notificationManager.cancel(NOTIFICATION_ITEM_FULL)
                    if (items == 0) {
                        pushNotification(NOTIFICATION_SPIN_FAIL, CHANNEL_SPIN_FAIL, text,
                            R.drawable.ic_alert_error_outline, sbn.packageName)
                    } else notificationManager.cancel(NOTIFICATION_SPIN_FAIL)
                } else Timber.e(Exception("Unrecognized notification text: $text"))
            }
        }
    }

    @Deprecated("Could be dropped since API 26")
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (isInterested(sbn ?: return)) onAuxiliaryDisconnected()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        @Suppress("DEPRECATION")
        when (reason) {
            REASON_APP_CANCEL,
            REASON_CLEAR_DATA,
            REASON_PACKAGE_CHANGED,
            REASON_PACKAGE_SUSPENDED,
            REASON_USER_STOPPED,
            -> onNotificationRemoved(sbn)
        }
    }
}
