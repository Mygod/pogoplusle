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
        // 1h 12.457s - 1h 11m 6.778s
        fun reportConnection() = alarm.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 3612457, 654321, timeoutIntent)
        fun reportDisconnection() = alarm.cancel(timeoutIntent)
    }

    override fun onReceive(context: Context?, intent: Intent?) = GameNotificationService.onAuxiliaryTimeout()
}
