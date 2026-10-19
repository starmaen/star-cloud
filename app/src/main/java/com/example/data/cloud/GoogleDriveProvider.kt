package com.example.data.cloud

import android.content.Context
import android.util.Log
import com.example.data.model.CloudProviderType
import com.example.data.model.CloudQuota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class GoogleDriveProvider(
    private val context: Context,
    initialEmail: String = "starsyria2500@gmail.com"
) : CloudStorageProvider {

    override val providerType: CloudProviderType = CloudProviderType.GOOGLE_DRIVE
    override var isConnected: Boolean = true
        private set

    override var userEmail: String? = initialEmail
        private set

    private val remoteFileIndex = mutableMapOf<String, RemoteFileMetadata>()

    override suspend fun connect(accountEmail: String?): Boolean = withContext(Dispatchers.IO) {
        userEmail = accountEmail ?: "starsyria2500@gmail.com"
        isConnected = true
        true
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        isConnected = false
    }

    override suspend fun getQuota(): CloudQuota = withContext(Dispatchers.IO) {
        val total = 15L * 1024 * 1024 * 1024
        val starCloudBytes = remoteFileIndex.values.sumOf { it.sizeBytes }
        val used = (4.2 * 1024 * 1024 * 1024).toLong() + starCloudBytes
        CloudQuota(
            totalBytes = total,
            usedBytes = used,
            starCloudUsedBytes = starCloudBytes
        )
    }

    override suspend fun uploadFile(
        remotePath: String,
        fileName: String,
        fileSize: Long,
        inputStream: InputStream,
        mimeType: String,
        onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit
    ): Result<RemoteFileMetadata> = withContext(Dispatchers.IO) {
        try {
            val fileId = UUID.randomUUID().toString()
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(16 * 1024)
            var bytesRead: Int
            var totalUploaded = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
                totalUploaded += bytesRead
                onProgress(totalUploaded, fileSize)
                if (fileSize > 500 * 1024) {
                    delay(15)
                }
            }

            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val metadata = RemoteFileMetadata(
                fileId = fileId,
                name = fileName,
                sizeBytes = totalUploaded,
                md5Checksum = hash,
                modifiedTime = System.currentTimeMillis(),
                mimeType = mimeType,
                isDirectory = false
            )

            remoteFileIndex[fileId] = metadata
            Result.success(metadata)
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "Upload failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(
        remoteFileId: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val meta = remoteFileIndex[remoteFileId]
            ?: return@withContext Result.failure(Exception("File not found on remote"))
        val dummyData = "Star Cloud Restored Payload: ${meta.name}".toByteArray()
        onProgress(dummyData.size.toLong(), dummyData.size.toLong())
        Result.success(dummyData)
    }

    override suspend fun listFiles(remoteFolder: String): List<RemoteFileMetadata> = withContext(Dispatchers.IO) {
        remoteFileIndex.values.toList()
    }

    override suspend fun deleteFile(remoteFileId: String): Boolean = withContext(Dispatchers.IO) {
        remoteFileIndex.remove(remoteFileId) != null
    }
}
