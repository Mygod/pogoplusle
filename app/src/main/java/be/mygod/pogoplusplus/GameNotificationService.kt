package be.mygod.pogoplusplus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import be.mygod.pogoplusplus.App.Companion.app
import timber.log.Timber

class GameNotificationService : NotificationListenerService() {
    companion object {
        private const val CHANNEL_AUXILIARY_DISCONNECTED = "auxiliary_disconnected"
        private const val CHANNEL_ITEM_FULL = "item_inventory_full"
        private const val CHANNEL_POKEMON_FULL = "pokemon_full"
        private const val CHANNEL_NO_BALL = "out_of_pokeballs"
        private const val PACKAGE_POKEMON_GO = "com.nianticlabs.pokemongo"
        private const val PACKAGE_POKEMON_GO_ARES = "com.nianticlabs.pokemongo.ares"

        private const val NOTIFICATION_AUXILIARY_DISCONNECTED = 1
        private const val NOTIFICATION_ITEM_FULL = 2
        private const val NOTIFICATION_POKEMON_FULL = 3
        private const val NOTIFICATION_NO_BALL = 4

        private fun gameIntent(packageName: String) = Intent(Intent.ACTION_MAIN).apply {
            setClassName(packageName, "com.nianticproject.holoholo.libholoholo.unity.UnityMainActivity")
        }
        val gameIntent: Intent? get() {
            gameIntent(PACKAGE_POKEMON_GO).apply { if (resolveActivity(app.packageManager) != null) return this }
            gameIntent(PACKAGE_POKEMON_GO_ARES).apply { if (resolveActivity(app.packageManager) != null) return this }
            return null
        }

        @RequiresApi(26)
        private fun createNotificationChannel(id: String, name: CharSequence) = NotificationChannel(
            id, name, NotificationManager.IMPORTANCE_HIGH
        ).apply {
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

        private val notificationManager by lazy { app.getSystemService<NotificationManager>()!! }
        private fun pushNotification(
            id: Int,
            channel: String,
            title: CharSequence,
            @DrawableRes icon: Int,
            packageName: String? = null,
        ) = notificationManager.notify(id, NotificationCompat.Builder(app, channel).apply {
            setCategory(NotificationCompat.CATEGORY_STATUS)
            setContentTitle(title)
            setSmallIcon(icon)
            setContentIntent(PendingIntent.getActivity(app, 0,
                if (packageName == null) gameIntent else gameIntent(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            setShowWhen(true)
            setAutoCancel(true)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            color = app.getColor(R.color.primaryColor)
            priority = NotificationCompat.PRIORITY_MAX
        }.build())

        var isRunning = false
            private set(value) {
                field = value
                MainPreferenceFragment.instance?.updateSwitches()
            }

        fun onAuxiliaryConnected() = notificationManager.cancel(NOTIFICATION_AUXILIARY_DISCONNECTED)
        fun onAuxiliaryDisconnected(deviceName: String = BluetoothReceiver.DEVICE_NAME_PGP,
                                    packageName: String? = null) = pushNotification(
            NOTIFICATION_AUXILIARY_DISCONNECTED, CHANNEL_AUXILIARY_DISCONNECTED, "$deviceName disconnected",
            R.drawable.ic_device_bluetooth_disabled, packageName)

        private fun isInterested(sbn: StatusBarNotification): Boolean {
            if (sbn.packageName != PACKAGE_POKEMON_GO && sbn.packageName != PACKAGE_POKEMON_GO_ARES) return false
            // com.nianticlabs.pokemongoplus.service.BackgroundService.notificationId
            return if (Build.VERSION.SDK_INT < 26) sbn.id == 2000 else sbn.notification.channelId == sbn.packageName
        }
    }

    override fun onListenerConnected() {
        isRunning = true
    }
    override fun onListenerDisconnected() {
        isRunning = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        BluetoothPairingService.instance?.apply {
            if (sbn.packageName == BluetoothPairingService.PACKAGE_SETTINGS) return onNotification(sbn.notification)
        }
        if (!isInterested(sbn)) return
        onAuxiliaryConnected()
        val text = sbn.notification.extras.getString(Notification.EXTRA_TEXT)
        Timber.d("PGP notification updated: $text")
        if (text.isNullOrEmpty()) return
        val resources = packageManager.getResourcesForApplication(sbn.packageName)
        fun getPogoString(name: String) = resources.getString(resources.getIdentifier(
            name, "string", sbn.packageName))
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

    @Deprecated("Could be dropped since API 26")
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (isInterested(sbn)) onAuxiliaryDisconnected()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
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
