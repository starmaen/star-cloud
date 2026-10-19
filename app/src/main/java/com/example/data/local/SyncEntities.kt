package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val direction: String,
    val status: String,
    val itemsSynced: Int,
    val totalSizeBytes: Long,
    val durationSeconds: Long,
    val summary: String,
    val providerName: String
)

@Entity(tableName = "folder_configs")
data class FolderConfigEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val treeUriString: String,
    val isEnabled: Boolean,
    val fileCount: Int,
    val totalSizeBytes: Long,
    val lastSyncTime: Long
)
