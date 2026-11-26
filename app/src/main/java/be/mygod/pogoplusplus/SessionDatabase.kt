package be.mygod.pogoplusplus

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Upsert
import androidx.sqlite.driver.AndroidSQLiteDriver
import be.mygod.pogoplusplus.App.Companion.app
import kotlinx.coroutines.flow.Flow
import java.time.DateTimeException
import java.time.ZoneId

/** A connection session with at least one recorded event, keyed by when it started. */
@Entity(tableName = "session")
data class SfidaSession(
    @PrimaryKey val startTime: Long,
    /** When the accessory disconnected, or the latest event if it came later. */
    val endTime: Long,
    /** Time zone the session started in, which decides its day and local times wherever it is viewed. */
    val zoneId: String,
    /** Empty when the session began without a known accessory. */
    val deviceAddress: String,
    val deviceName: String?,
    val caught: Long,
    val fled: Long,
    val spins: Long,
    val items: Long,
    val itemFull: Long,
    val pokemonFull: Long,
    val noBall: Long,
    val spinFail: Long,
) {
    val encounters get() = caught + fled
    /** Never negative, even if the clock went back during the session. */
    val duration get() = (endTime - startTime).coerceAtLeast(0)
    val catchRate get() = if (encounters > 0) caught.toDouble() / encounters else null
    /** Falls back to the current zone in case this device lacks the zone of a restored backup. */
    val zone: ZoneId get() = try {
        ZoneId.of(zoneId)
    } catch (_: DateTimeException) {
        ZoneId.systemDefault()
    }
    val isEmpty get() = caught == 0L && fled == 0L && spins == 0L && items == 0L && itemFull == 0L &&
            pokemonFull == 0L && noBall == 0L && spinFail == 0L
}

@Dao
interface SfidaSessionDao {
    @Query("SELECT * FROM session ORDER BY startTime DESC")
    fun observe(): Flow<List<SfidaSession>>

    @Query("SELECT * FROM session ORDER BY startTime")
    suspend fun list(): List<SfidaSession>

    @Upsert
    suspend fun upsert(session: SfidaSession)

    @Delete
    suspend fun delete(session: SfidaSession)

    @Query("SELECT * FROM session WHERE startTime < :before AND startTime != :kept AND " +
            "(:device IS NULL OR deviceAddress = :device)")
    suspend fun listBefore(before: Long, kept: Long, device: String?): List<SfidaSession>

    @Query("DELETE FROM session WHERE startTime < :before AND startTime != :kept AND " +
            "(:device IS NULL OR deviceAddress = :device)")
    suspend fun deleteBefore(before: Long, kept: Long, device: String?)
}

@Database(entities = [SfidaSession::class], version = 1)
abstract class SessionDatabase : RoomDatabase() {
    companion object {
        /** File name in the database directory, which backups include. */
        const val NAME = "sessions.db"

        val instance by lazy {
            Room.databaseBuilder(app, SessionDatabase::class.java, NAME).apply {
                setDriver(AndroidSQLiteDriver())
                // backups copy files without SQLite, so keep every committed write in the main file
                setJournalMode(JournalMode.TRUNCATE)
                // a restored backup can come from a newer version
                fallbackToDestructiveMigrationOnDowngrade(true)
            }.build()
        }
    }

    abstract fun sessions(): SfidaSessionDao
}
