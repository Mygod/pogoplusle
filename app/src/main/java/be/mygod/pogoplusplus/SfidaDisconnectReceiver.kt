package be.mygod.pogoplusplus

import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.IntentCompat
import timber.log.Timber

class SfidaDisconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent) {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        val gameAction = IntentCompat.getParcelableExtra(intent, GameNotificationService.EXTRA_GAME_ACTION,
            PendingIntent::class.java)
        try {
            try {
                if (gameAction?.send() != null) return
            } catch (_: PendingIntent.CanceledException) { }
            val stoppedByBluetooth = if (device == null) SfidaManager.disconnectAll() else SfidaManager.disconnect(device)
            if (!stoppedByBluetooth && context != null) {
                Toast.makeText(context, R.string.notification_action_disconnect_failed, Toast.LENGTH_LONG).show()
            }
        } catch (e: SecurityException) {
            Timber.d(e)
            Toast.makeText(context, e.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }
}
