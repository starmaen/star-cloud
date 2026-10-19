package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLogEntity): Long

    @Query("DELETE FROM sync_logs")
    suspend fun clearLogs()

    @Query("SELECT * FROM folder_configs")
    fun getAllFolderConfigs(): Flow<List<FolderConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderConfig(folder: FolderConfigEntity)

    @Query("DELETE FROM folder_configs WHERE id = :id")
    suspend fun deleteFolderConfig(id: String)
}
