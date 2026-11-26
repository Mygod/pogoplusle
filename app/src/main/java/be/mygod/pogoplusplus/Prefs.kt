package be.mygod.pogoplusplus

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import be.mygod.pogoplusplus.App.Companion.app

/** Preferences split by whether they follow the user to another device; see res/xml/data_extraction_rules.xml. */
object Prefs {
    /** File name of [backup], which backups include. */
    const val BACKUP_NAME = "backup"

    /** Device-specific state, excluded from backups. */
    val device: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(app) }

    /** Settings and statistics that backups carry to another device. */
    val backup: SharedPreferences by lazy {
        app.getSharedPreferences(BACKUP_NAME, Context.MODE_PRIVATE).also { backup ->
            // these were saved in the device preferences before the split
            val moved = device.all.filterKeys {
                it == SfidaConnectionPriority.KEY_PRIORITY || it.startsWith(SfidaSessionManager.LIFETIME_KEY_PREFIX)
            }
            if (moved.isEmpty()) return@also
            // a retry after a failed copy must not overwrite what was recorded since
            val copied = backup.edit().run {
                for ((key, value) in moved) if (!backup.contains(key)) when (value) {
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                }
                commit()
            }
            // keep the originals until the copy is safe
            if (copied) device.edit { for (key in moved.keys) remove(key) }
        }
    }
}
