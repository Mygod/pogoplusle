package be.mygod.pogoplusplus

import android.bluetooth.BluetoothDevice
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import be.mygod.pogoplusplus.App.Companion.app
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.ZoneId

/**
 * Tracks the current session in device preferences and lifetime totals in backed up preferences. Every change saves
 * the session to [SessionDatabase] once it has an event. Called on the main thread only.
 */
object SfidaSessionManager {
    data class Stats(val deviceAddress: String, val deviceName: String?, val startTime: Long, val stats: String)
    data class Lifetime(val connections: Long, val stats: String)
    /** History and lifetime totals as of when clearing was asked for, which is what [clear] removes. */
    class PendingClear internal constructor(
        internal val time: Long,
        /** Address of the device whose sessions to remove, or null for all along with totals from before history. */
        internal val device: String?,
        /** Start time of the session in progress then, which stays. */
        internal val kept: Long?,
        /** Start time of the session that had ended then, whose counts restart if it is still the latest. */
        internal val ended: Long?,
        /** Amount to take off each lifetime total when clearing all, or null to take off what the removed rows hold. */
        internal val totals: Map<String, Long>?,
    ) {
        fun removes(session: SfidaSession) = session.startTime < time && session.startTime != kept &&
                (device == null || session.deviceAddress == device)
    }

    private enum class Counter(val key: String, val of: (SfidaSession) -> Long, val lifetimeKey: String? = null) {
        SPIN("sfidaSession.spinCount", SfidaSession::spins, "sfidaStats.spinCount"),
        ITEM("sfidaSession.itemCount", SfidaSession::items, "sfidaStats.itemCount"),
        CAPTURED("sfidaSession.capturedCount", SfidaSession::caught, "sfidaStats.capturedCount"),
        ESCAPED("sfidaSession.escapedCount", SfidaSession::fled, "sfidaStats.escapedCount"),
        ITEM_FULL("sfidaSession.itemFullCount", SfidaSession::itemFull),
        POKEMON_FULL("sfidaSession.pokemonFullCount", SfidaSession::pokemonFull),
        NO_BALL("sfidaSession.noBallCount", SfidaSession::noBall),
        SPIN_FAIL("sfidaSession.spinFailCount", SfidaSession::spinFail),
    }

    const val LIFETIME_KEY_PREFIX = "sfidaStats."
    private const val KEY_ACTIVE = "sfidaSession.active"
    private const val KEY_DEVICE_ADDRESS = "sfidaSession.deviceAddress"
    private const val KEY_DEVICE_NAME = "sfidaSession.deviceName"
    /** Whether a Bluetooth connection, rather than the game alone, has told which accessory the session is on. */
    private const val KEY_DEVICE_CONFIRMED = "sfidaSession.deviceConfirmed"
    private const val KEY_START_TIME = "sfidaSession.startTime"
    private const val KEY_END_TIME = "sfidaSession.endTime"
    private const val KEY_ZONE = "sfidaSession.zone"
    /** Boot count when the session started, as no connection outlives a reboot. */
    private const val KEY_BOOT = "sfidaSession.boot"
    private const val KEY_CONNECTION_STATS = LIFETIME_KEY_PREFIX + "connectionCount"

    private val pref get() = Prefs.device
    private val lifetime get() = Prefs.backup

    private val _ongoingStartTime = MutableStateFlow(findOngoingStartTime())
    /** Start time of the session in progress, if any. */
    val ongoingStartTime = _ongoingStartTime.asStateFlow()
    private val bootCount get() = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
    /** Whether a session is in progress, which none from before the latest boot can be despite an unreported end. */
    private fun isActive() = pref.getBoolean(KEY_ACTIVE, false) && when (val boot = pref.getInt(KEY_BOOT, -1)) {
        // without a boot count, only time can tell, which network time can throw off
        -1 -> pref.getLong(KEY_START_TIME, 0) >= System.currentTimeMillis() - SystemClock.elapsedRealtime()
        else -> boot == bootCount
    }
    private fun findOngoingStartTime() = if (isActive()) pref.getLong(KEY_START_TIME, 0) else null

    /** Database operations, serialized so that later snapshots and deletions always win. */
    private val operations by lazy {
        Channel<suspend SfidaSessionDao.() -> Unit>(Channel.UNLIMITED).also { channel ->
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val dao = SessionDatabase.instance.sessions()
                for (operation in channel) try {
                    dao.operation()
                } catch (e: Exception) {
                    Timber.w(e)
                }
            }
        }
    }
    private fun enqueue(operation: suspend SfidaSessionDao.() -> Unit) {
        ++writes
        operations.trySend(operation)
    }
    /** Like [enqueue], but also hands the result or failure to the caller, who might no longer be waiting. */
    private fun <T> submit(operation: suspend SfidaSessionDao.() -> T) = CompletableDeferred<T>().also { result ->
        enqueue {
            try {
                result.complete(operation())
            } catch (e: Exception) {
                Timber.w(e)
                result.completeExceptionally(e)
            }
        }
    }

    private fun count(counter: Counter) = pref.getLong(counter.key, 0)
    private fun makeStats(delta: Long = 0) = Stats(
        pref.getString(KEY_DEVICE_ADDRESS, null) ?: "",
        pref.getString(KEY_DEVICE_NAME, null),
        pref.getLong(KEY_START_TIME, 0),
        formatStats(count(Counter.CAPTURED), count(Counter.CAPTURED) + count(Counter.ESCAPED), count(Counter.SPIN),
            count(Counter.ITEM), delta),
    )

    private fun lifetime(counter: Counter) = lifetime.getLong(counter.lifetimeKey, 0)
    private fun readLifetime() = Lifetime(lifetime.getLong(KEY_CONNECTION_STATS, 0), formatStats(
        lifetime(Counter.CAPTURED), lifetime(Counter.CAPTURED) + lifetime(Counter.ESCAPED), lifetime(Counter.SPIN),
        lifetime(Counter.ITEM)))
    private val _lifetimeTotals = MutableStateFlow(readLifetime())
    /**
     * All-time totals, including those recorded before session history existed, and the connection count, including
     * connections without any event.
     */
    val lifetimeTotals = _lifetimeTotals.asStateFlow()
    private inline fun updateLifetime(action: SharedPreferences.Editor.() -> Unit) {
        lifetime.edit(action = action)
        _lifetimeTotals.value = readLifetime()
    }

    /** Saves the session once it has an event, since connections without any are not worth listing. */
    private fun persist() {
        _ongoingStartTime.value = findOngoingStartTime()
        val startTime = pref.getLong(KEY_START_TIME, 0)
        if (startTime == 0L) return
        val session = SfidaSession(
            startTime = startTime,
            endTime = pref.getLong(KEY_END_TIME, startTime),
            // sessions started before zones were saved
            zoneId = pref.getString(KEY_ZONE, null) ?: ZoneId.systemDefault().id,
            deviceAddress = pref.getString(KEY_DEVICE_ADDRESS, null) ?: "",
            deviceName = pref.getString(KEY_DEVICE_NAME, null),
            caught = count(Counter.CAPTURED),
            fled = count(Counter.ESCAPED),
            spins = count(Counter.SPIN),
            items = count(Counter.ITEM),
            itemFull = count(Counter.ITEM_FULL),
            pokemonFull = count(Counter.POKEMON_FULL),
            noBall = count(Counter.NO_BALL),
            spinFail = count(Counter.SPIN_FAIL),
        )
        if (!session.isEmpty) enqueue { upsert(session) }
    }

    private fun SharedPreferences.Editor.startSession(time: Long) {
        putBoolean(KEY_ACTIVE, true)
        putLong(KEY_START_TIME, time)
        putLong(KEY_END_TIME, time)
        putString(KEY_ZONE, ZoneId.systemDefault().id)
        putInt(KEY_BOOT, bootCount)
        for (counter in Counter.entries) remove(counter.key)
        remove(KEY_DEVICE_CONFIRMED)
        updateLifetime { putLong(KEY_CONNECTION_STATS, lifetime.getLong(KEY_CONNECTION_STATS, 0) + 1) }
    }
    private fun SharedPreferences.Editor.setDevice(device: Pair<BluetoothDevice, String?>) {
        // a missing name may only reuse the saved name of the same device
        if (device.second != null || !device.first.address.equals(pref.getString(KEY_DEVICE_ADDRESS, null), true)) {
            putString(KEY_DEVICE_NAME, device.second)
        }
        putString(KEY_DEVICE_ADDRESS, device.first.address)
    }
    fun onConnect(): Stats {
        val time = System.currentTimeMillis()
        pref.edit {
            // the Bluetooth report of this same connection can come first, so only date its session from the game
            if (isActive() && Counter.entries.all { count(it) == 0L }) {
                putLong(KEY_START_TIME, time)
                putLong(KEY_END_TIME, time)
            } else startSession(time)
        }
        persist()
        return makeStats()
    }
    fun onConnect(device: Pair<BluetoothDevice, String?>): Stats {
        // another accessory means that the disconnection of the confirmed one was missed
        val newSession = !isActive() || pref.getBoolean(KEY_DEVICE_CONFIRMED, false) &&
                !device.first.address.equals(pref.getString(KEY_DEVICE_ADDRESS, null), true)
        pref.edit {
            if (newSession) startSession(System.currentTimeMillis())
            setDevice(device)
            putBoolean(KEY_DEVICE_CONFIRMED, true)
        }
        persist()
        return makeStats()
    }
    fun onDisconnect(device: Pair<BluetoothDevice, String?>?): Stats {
        val wasActive = isActive()
        pref.edit {
            // later reports of the same disconnection must not extend the session
            if (wasActive) putLong(KEY_END_TIME, System.currentTimeMillis())
            remove(KEY_ACTIVE)
            if (device != null) setDevice(device)
        }
        // a device reported after the session ended belongs to the next one
        if (wasActive) persist()
        return makeStats()
    }

    /** Events while connected without an active session start a new one, else they count toward the last one. */
    private fun record(isConnected: Boolean, delta: Long, vararg increments: Pair<Counter, Long>): Stats {
        val time = System.currentTimeMillis()
        val newSession = isConnected && !isActive()
        pref.edit {
            if (newSession) startSession(time) else putBoolean(KEY_ACTIVE, isConnected)
            putLong(KEY_END_TIME, time)
            for ((counter, increment) in increments) {
                putLong(counter.key, (if (newSession) 0 else count(counter)) + increment)
            }
        }
        updateLifetime {
            for ((counter, increment) in increments) counter.lifetimeKey?.let {
                putLong(it, lifetime.getLong(it, 0) + increment)
            }
        }
        persist()
        return makeStats(delta)
    }
    fun onSpin(items: Long, isConnected: Boolean) =
        record(isConnected, items, Counter.SPIN to 1L, Counter.ITEM to items)
    fun onCaptured(isConnected: Boolean) = record(isConnected, -1, Counter.CAPTURED to 1L)
    fun onEscaped(isConnected: Boolean) = record(isConnected, -2, Counter.ESCAPED to 1L)
    fun onItemFull(isConnected: Boolean) = record(isConnected, 0, Counter.ITEM_FULL to 1L)
    fun onPokemonFull(isConnected: Boolean) = record(isConnected, 0, Counter.POKEMON_FULL to 1L)
    fun onNoBall(isConnected: Boolean) = record(isConnected, 0, Counter.NO_BALL to 1L)
    fun onSpinFailed(isConnected: Boolean) = record(isConnected, 0, Counter.SPIN_FAIL to 1L)

    /** Ends a session left active by a missed disconnection, e.g. after a force stop or a reboot. */
    fun reconcile() {
        // a session can only be active with Bluetooth around
        if (!pref.getBoolean(KEY_ACTIVE, false) || isActive() && SfidaManager.isConnected != false) return
        pref.edit { remove(KEY_ACTIVE) }
        _ongoingStartTime.value = null
    }

    /** Number of database writes requested so far, telling whether there is anything for [flush] to wait for. */
    var writes = 0L
        private set
    /** Completes once every database write requested so far has landed. */
    fun flush() = submit { }
    fun delete(session: SfidaSession) = submit { delete(session) }
    fun restore(session: SfidaSession) = submit { upsert(session) }

    /**
     * Takes stock for [clear] of everything so far except for the session in progress: all history and lifetime totals,
     * or only the sessions of [device] and their share of the totals.
     */
    fun prepareClear(device: String?): PendingClear {
        val kept = findOngoingStartTime()
        val ended = if (kept == null) pref.getLong(KEY_START_TIME, 0).takeIf {
            it != 0L && (device == null || device == (pref.getString(KEY_DEVICE_ADDRESS, null) ?: ""))
        } else null
        return PendingClear(System.currentTimeMillis(), device, kept, ended, if (device != null) null else {
            Counter.entries.mapNotNull { counter ->
                counter.lifetimeKey?.let { it to lifetime.getLong(it, 0) - if (kept == null) 0 else count(counter) }
            }.toMap() + (KEY_CONNECTION_STATS to lifetime.getLong(KEY_CONNECTION_STATS, 0) - if (kept == null) 0 else 1)
        })
    }
    /** Removes what [pending] took stock of, with lifetime totals following once history is gone. */
    fun clear(pending: PendingClear) = submit {
        val kept = pending.kept ?: 0
        // rows hold every write before this one, unlike what the screen showed
        val totals = pending.totals ?: listBefore(pending.time, kept, pending.device).let { removed ->
            Counter.entries.mapNotNull { counter ->
                counter.lifetimeKey?.let { it to removed.sumOf(counter.of) }
            }.toMap() + (KEY_CONNECTION_STATS to removed.size.toLong())
        }
        deleteBefore(pending.time, kept, pending.device)
        withContext(Dispatchers.Main) {
            // whatever came after stays
            updateLifetime {
                for ((key, value) in totals) putLong(key, (lifetime.getLong(key, 0) - value).coerceAtLeast(0))
            }
            // restart the counts of the ended session so that late events cannot bring it back whole
            if (pending.ended != null && pending.ended == pref.getLong(KEY_START_TIME, 0)) pref.edit {
                for (counter in Counter.entries) remove(counter.key)
            }
        }
    }
}
