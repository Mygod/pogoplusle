package be.mygod.pogoplusplus

import android.bluetooth.BluetoothDevice
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import be.mygod.pogoplusplus.App.Companion.app

object SfidaSessionManager {
    data class Stats(val deviceAddress: String, val deviceName: String?, val startTime: Long, val stats: String)

    private const val KEY_DEVICE_ADDRESS = "sfidaSession.deviceAddress"
    private const val KEY_DEVICE_NAME = "sfidaSession.deviceName"
    private const val KEY_START_TIME = "sfidaSession.startTime"
    private const val KEY_SPIN_COUNT = "sfidaSession.spinCount"
    private const val KEY_ITEM_COUNT = "sfidaSession.itemCount"
    private const val KEY_CAPTURED_COUNT = "sfidaSession.capturedCount"
    private const val KEY_ESCAPED_COUNT = "sfidaSession.escapedCount"

    private const val KEY_CONNECTION_STATS = "sfidaStats.connectionCount"
    private const val KEY_SPIN_STATS = "sfidaStats.spinCount"
    private const val KEY_ITEM_STATS = "sfidaStats.itemCount"
    private const val KEY_CAPTURED_STATS = "sfidaStats.capturedCount"
    private const val KEY_ESCAPED_STATS = "sfidaStats.escapedCount"

    private val pref by lazy { PreferenceManager.getDefaultSharedPreferences(app) }

    private fun makeStats(
        spinCount: Long = pref.getLong(KEY_SPIN_COUNT, 0),
        itemCount: Long = pref.getLong(KEY_ITEM_COUNT, 0),
        capturedCount: Long = pref.getLong(KEY_CAPTURED_COUNT, 0),
        escapedCount: Long = pref.getLong(KEY_ESCAPED_COUNT, 0),
        deviceAddress: String = pref.getString(KEY_DEVICE_ADDRESS, null) ?: "",
        deviceName: String? = pref.getString(KEY_DEVICE_NAME, null),
        startTime: Long = pref.getLong(KEY_START_TIME, 0),
    ) = Stats(deviceAddress, deviceName, startTime,
        "\uD83D\uDD2E $capturedCount　\uD83E\uDD3E ${capturedCount + escapedCount}　\uD83D\uDCAB $spinCount　\uD83C\uDF92 $itemCount")

    fun onConnect(device: Pair<BluetoothDevice, String?>): Stats {
        var name = device.second
        val time = System.currentTimeMillis()
        pref.edit {
            if (name == null && device.first.address.equals(pref.getString(KEY_DEVICE_ADDRESS, null), true)) {
                name = pref.getString(KEY_DEVICE_NAME, null)
            } else putString(KEY_DEVICE_NAME, name)
            putString(KEY_DEVICE_ADDRESS, device.first.address)
            putLong(KEY_START_TIME, time)
            putLong(KEY_SPIN_COUNT, 0)
            putLong(KEY_ITEM_COUNT, 0)
            putLong(KEY_CAPTURED_COUNT, 0)
            putLong(KEY_ESCAPED_COUNT, 0)
            putLong(KEY_CONNECTION_STATS, pref.getLong(KEY_CONNECTION_STATS, 0) + 1)
        }
        return makeStats(0, 0, 0, 0, device.first.address, name, time)
    }
    fun onDisconnect(device: Pair<BluetoothDevice, String?>?) = when {
        device == null -> makeStats()
        device.second == null -> makeStats(deviceAddress = device.first.address)
        else -> makeStats(deviceAddress = device.first.address, deviceName = device.second)
    }

    fun onSpin(items: Long): Stats {
        val spinCount = pref.getLong(KEY_SPIN_COUNT, 0) + 1
        val itemCount = pref.getLong(KEY_ITEM_COUNT, 0) + items
        pref.edit {
            putLong(KEY_SPIN_COUNT, spinCount)
            putLong(KEY_SPIN_STATS, pref.getLong(KEY_SPIN_STATS, 0) + 1)
            putLong(KEY_ITEM_COUNT, itemCount)
            putLong(KEY_ITEM_STATS, pref.getLong(KEY_ITEM_STATS, 0) + items)
        }
        return makeStats(spinCount, itemCount)
    }
    private fun increment(countKey: String, statsKey: String) = (pref.getLong(countKey, 0) + 1).also {
        pref.edit {
            putLong(countKey, it)
            putLong(statsKey, pref.getLong(statsKey, 0) + 1)
        }
    }
    fun onCaptured() = makeStats(capturedCount = increment(KEY_CAPTURED_COUNT, KEY_CAPTURED_STATS))
    fun onEscaped() = makeStats(escapedCount = increment(KEY_ESCAPED_COUNT, KEY_ESCAPED_STATS))
}
