package com.example.core.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "file_index", primaryKeys = ["destId", "path"])
data class FileIndexEntity(val destId: String, val path: String, val size: Long, val modified: Long)

@Entity(tableName = "run_log")
data class RunLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val kind: String,
    val ok: Boolean,
    val items: Int,
    val bytes: Long,
    val seconds: Long,
    val summary: String
)

@Dao
interface StarDao {
    @Query("SELECT * FROM file_index WHERE destId = :d")
    suspend fun indexFor(d: String): List<FileIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putIndex(list: List<FileIndexEntity>)

    @Query("DELETE FROM file_index WHERE destId = :d")
    suspend fun clearIndex(d: String)

    @Query("DELETE FROM file_index WHERE destId = :d AND path IN (:paths)")
    suspend fun removeIndex(d: String, paths: List<String>)

    @Query("SELECT COALESCE(SUM(size),0) FROM file_index WHERE destId = :d")
    suspend fun indexedBytes(d: String): Long

    @Query("SELECT COUNT(*) FROM file_index WHERE destId = :d")
    suspend fun indexedCount(d: String): Int

    @Insert
    suspend fun addLog(l: RunLogEntity)

    @Query("SELECT * FROM run_log ORDER BY time DESC LIMIT 30")
    suspend fun recentLogs(): List<RunLogEntity>

    @Query("SELECT * FROM run_log WHERE kind = 'backup' AND ok = 1 ORDER BY time DESC LIMIT 1")
    suspend fun lastGoodBackup(): RunLogEntity?
}

@Database(entities = [FileIndexEntity::class, RunLogEntity::class], version = 4, exportSchema = false)
abstract class StarDb : RoomDatabase() {
    abstract fun dao(): StarDao

    companion object {
        @Volatile private var inst: StarDb? = null
        fun get(c: Context): StarDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, StarDb::class.java, "star_cloud_db")
                .fallbackToDestructiveMigration().build().also { inst = it }
        }
    }
}
