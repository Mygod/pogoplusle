package be.mygod.pogoplusplus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.getSystemService
import be.mygod.pogoplusplus.App.Companion.app

class SfidaTimeoutReceiver : BroadcastReceiver() {
    companion object {
        private val alarm by lazy { app.getSystemService<AlarmManager>()!! }
        private val timeoutIntent by lazy {
            PendingIntent.getBroadcast(app, 0, Intent(app, SfidaTimeoutReceiver::class.java),
                PendingIntent.FLAG_MUTABLE)
        }
        // 35m 9.876s - 1h 1m 32.283s (1.75x)
        fun reportConnection() = alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 2109876, timeoutIntent)
        fun reportDisconnection() = alarm.cancel(timeoutIntent)
    }

    override fun onReceive(context: Context?, intent: Intent?) = GameNotificationService.onAuxiliaryTimeout()
}
