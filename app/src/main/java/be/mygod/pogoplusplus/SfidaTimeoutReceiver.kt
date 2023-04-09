package be.mygod.pogoplusplus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SfidaTimeoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) = GameNotificationService.onAuxiliaryTimeout()
}
