package com.example.data.cloud

import android.content.Context
import com.example.data.model.CloudProviderType
import com.example.data.model.CloudQuota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class LocalMockCloudProvider(
    private val context: Context
) : CloudStorageProvider {

    override val providerType: CloudProviderType = CloudProviderType.LOCAL_SECURE_STORAGE
    override var isConnected: Boolean = true
        private set
    override var userEmail: String? = "local_sandbox@starcloud.internal"
        private set

    private val sandboxDir: File by lazy {
        File(context.filesDir, "star_cloud_sandbox").apply { mkdirs() }
    }

    override suspend fun connect(accountEmail: String?): Boolean = true
    override suspend fun disconnect() { isConnected = false }

    override suspend fun getQuota(): CloudQuota = withContext(Dispatchers.IO) {
        val freeSpace = sandboxDir.freeSpace
        val usedSpace = sandboxDir.listFiles()?.sumOf { it.length() } ?: 0L
        CloudQuota(
            totalBytes = freeSpace + usedSpace,
            usedBytes = usedSpace,
            starCloudUsedBytes = usedSpace
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
            val targetDir = File(sandboxDir, remotePath).apply { mkdirs() }
            val targetFile = File(targetDir, fileName)
            val digest = MessageDigest.getInstance("MD5")

            var totalBytes = 0L
            val buffer = ByteArray(8192)

            FileOutputStream(targetFile).use { out ->
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    totalBytes += read
                    onProgress(totalBytes, fileSize)
                    if (fileSize > 200 * 1024) delay(10)
                }
            }

            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val metadata = RemoteFileMetadata(
                fileId = UUID.randomUUID().toString(),
                name = fileName,
                sizeBytes = targetFile.length(),
                md5Checksum = hash,
                modifiedTime = targetFile.lastModified(),
                mimeType = mimeType,
                isDirectory = false
            )
            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(
        remoteFileId: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val files = sandboxDir.walkTopDown().filter { it.isFile }.toList()
            val file = files.firstOrNull() ?: return@withContext Result.failure(Exception("No files found"))
            val bytes = file.readBytes()
            onProgress(bytes.size.toLong(), bytes.size.toLong())
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listFiles(remoteFolder: String): List<RemoteFileMetadata> = withContext(Dispatchers.IO) {
        val folder = File(sandboxDir, remoteFolder)
        if (!folder.exists()) return@withContext emptyList()
        folder.listFiles()?.map { file ->
            RemoteFileMetadata(
                fileId = file.name,
                name = file.name,
                sizeBytes = file.length(),
                md5Checksum = null,
                modifiedTime = file.lastModified(),
                mimeType = "application/octet-stream",
                isDirectory = file.isDirectory
            )
        } ?: emptyList()
    }

    override suspend fun deleteFile(remoteFileId: String): Boolean = withContext(Dispatchers.IO) {
        File(sandboxDir, remoteFileId).delete()
    }
}
