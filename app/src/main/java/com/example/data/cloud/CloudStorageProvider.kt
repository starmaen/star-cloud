package com.example.data.cloud

import com.example.data.model.CloudProviderType
import com.example.data.model.CloudQuota
import java.io.InputStream

data class RemoteFileMetadata(
    val fileId: String,
    val name: String,
    val sizeBytes: Long,
    val md5Checksum: String?,
    val modifiedTime: Long,
    val mimeType: String,
    val isDirectory: Boolean = false
)

interface CloudStorageProvider {
    val providerType: CloudProviderType
    val isConnected: Boolean
    val userEmail: String?

    suspend fun connect(accountEmail: String? = null): Boolean
    suspend fun disconnect()
    suspend fun getQuota(): CloudQuota

    suspend fun uploadFile(
        remotePath: String,
        fileName: String,
        fileSize: Long,
        inputStream: InputStream,
        mimeType: String,
        onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit
    ): Result<RemoteFileMetadata>

    suspend fun downloadFile(
        remoteFileId: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<ByteArray>

    suspend fun listFiles(remoteFolder: String): List<RemoteFileMetadata>
    suspend fun deleteFile(remoteFileId: String): Boolean
}
