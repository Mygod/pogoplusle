package be.mygod.pogoplusplus

import android.bluetooth.BluetoothDevice
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import be.mygod.pogoplusplus.App.Companion.app

object SfidaSessionManager {
    data class Stats(val deviceAddress: String, val deviceName: String?, val startTime: Long, val stats: String)

    private const val KEY_ACTIVE = "sfidaSession.active"
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
        delta: Long = 0,
    ) = Stats(deviceAddress, deviceName, startTime, StringBuilder("🔮 ").apply {
        append(capturedCount)
        if (delta == -1L) append('⁺')
        append("　🤾 ")
        append(capturedCount + escapedCount)
        if (delta == -2L) append('⁺')
        append("　💫 ")
        append(spinCount)
        append("　🎒 ")
        append(itemCount)
        if (delta > 0) {
            append('⁺')
            for (char in delta.toString()) append("⁰¹²³⁴⁵⁶⁷⁸⁹"[char.digitToInt()])
        }
    }.toString())

    private fun SharedPreferences.Editor.setActive(time: Long, spinCount: Long = 0, itemCount: Long = 0,
                                                   capturedCount: Long = 0, escapedCount: Long = 0) {
        putBoolean(KEY_ACTIVE, true)
        putLong(KEY_CONNECTION_STATS, pref.getLong(KEY_CONNECTION_STATS, 0) + 1)
        putLong(KEY_START_TIME, time)
        putLong(KEY_SPIN_COUNT, spinCount)
        putLong(KEY_ITEM_COUNT, itemCount)
        putLong(KEY_CAPTURED_COUNT, capturedCount)
        putLong(KEY_ESCAPED_COUNT, escapedCount)
    }
    fun onConnect() = System.currentTimeMillis().let { time ->
        pref.edit { setActive(time) }
        makeStats(0, 0, 0, 0, startTime = time)
    }
    fun onConnect(device: Pair<BluetoothDevice, String?>): Stats {
        val time = System.currentTimeMillis()
        var name = device.second
        val wasActive = pref.getBoolean(KEY_ACTIVE, false)
        pref.edit {
            if (!wasActive) setActive(time)
            if (name == null && device.first.address.equals(pref.getString(KEY_DEVICE_ADDRESS, null), true)) {
                name = pref.getString(KEY_DEVICE_NAME, null)
            } else putString(KEY_DEVICE_NAME, name)
            putString(KEY_DEVICE_ADDRESS, device.first.address)
        }
        return makeStats(deviceAddress = device.first.address, deviceName = name)
    }
    fun onDisconnect(device: Pair<BluetoothDevice, String?>?) = when {
        device == null -> {
            pref.edit { remove(KEY_ACTIVE) }
            makeStats()
        }
        device.second == null -> {
            pref.edit { remove(KEY_ACTIVE) }
            makeStats(deviceAddress = device.first.address)
        }
        else -> {
            pref.edit {
                remove(KEY_ACTIVE)
                putString(KEY_DEVICE_ADDRESS, device.first.address)
                putString(KEY_DEVICE_NAME, device.second)
            }
            makeStats(deviceAddress = device.first.address, deviceName = device.second)
        }
    }

    fun onSpin(items: Long, isConnected: Boolean): Stats {
        if (isConnected && !pref.getBoolean(KEY_ACTIVE, false)) {
            val time = System.currentTimeMillis()
            pref.edit {
                setActive(time, 1, items)
                putLong(KEY_SPIN_STATS, pref.getLong(KEY_SPIN_STATS, 0) + 1)
                putLong(KEY_ITEM_STATS, pref.getLong(KEY_ITEM_STATS, 0) + items)
            }
            return makeStats(1, items, 0, 0, startTime = time, delta = items)
        }
        val spinCount = pref.getLong(KEY_SPIN_COUNT, 0) + 1
        val itemCount = pref.getLong(KEY_ITEM_COUNT, 0) + items
        pref.edit {
            putBoolean(KEY_ACTIVE, isConnected)
            putLong(KEY_SPIN_COUNT, spinCount)
            putLong(KEY_SPIN_STATS, pref.getLong(KEY_SPIN_STATS, 0) + 1)
            putLong(KEY_ITEM_COUNT, itemCount)
            putLong(KEY_ITEM_STATS, pref.getLong(KEY_ITEM_STATS, 0) + items)
        }
        return makeStats(spinCount, itemCount, delta = items)
    }
    fun onCaptured(isConnected: Boolean): Stats {
        if (isConnected && !pref.getBoolean(KEY_ACTIVE, false)) {
            val time = System.currentTimeMillis()
            pref.edit {
                setActive(time, capturedCount = 1)
                putLong(KEY_CAPTURED_STATS, pref.getLong(KEY_CAPTURED_STATS, 0) + 1)
            }
            return makeStats(0, 0, 1, 0, startTime = time, delta = -1)
        }
        val count = pref.getLong(KEY_CAPTURED_COUNT, 0) + 1
        pref.edit {
            putBoolean(KEY_ACTIVE, isConnected)
            putLong(KEY_CAPTURED_COUNT, count)
            putLong(KEY_CAPTURED_STATS, pref.getLong(KEY_CAPTURED_STATS, 0) + 1)
        }
        return makeStats(capturedCount = count, delta = -1)
    }
    fun onEscaped(isConnected: Boolean): Stats {
        if (isConnected && !pref.getBoolean(KEY_ACTIVE, false)) {
            val time = System.currentTimeMillis()
            pref.edit {
                setActive(time, escapedCount = 1)
                putLong(KEY_ESCAPED_STATS, pref.getLong(KEY_ESCAPED_STATS, 0) + 1)
            }
            return makeStats(0, 0, 0, 1, startTime = time, delta = -2)
        }
        val count = pref.getLong(KEY_ESCAPED_COUNT, 0) + 1
        pref.edit {
            putBoolean(KEY_ACTIVE, isConnected)
            putLong(KEY_ESCAPED_COUNT, count)
            putLong(KEY_ESCAPED_STATS, pref.getLong(KEY_ESCAPED_STATS, 0) + 1)
        }
        return makeStats(escapedCount = count, delta = -2)
    }
}
