package com.example.data.cloud

import android.content.Context
import com.example.data.model.CloudProviderType
import com.example.data.model.CloudQuota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Local, app-private destination. It is always available and is used when
 * the user keeps cloud/external sync disabled.
 */
class LocalCloudProvider(
    private val context: Context
) : CloudStorageProvider {

    override val providerType: CloudProviderType = CloudProviderType.LOCAL_SECURE_STORAGE
    override var isConnected: Boolean = true
        private set
    override var userEmail: String? = null
        private set

    private val sandboxDir: File by lazy {
        File(context.filesDir, "StarCloud").apply { mkdirs() }
    }

    override suspend fun connect(accountEmail: String?): Boolean = true
    override suspend fun disconnect() { isConnected = false }

    override suspend fun getQuota(): CloudQuota = withContext(Dispatchers.IO) {
        val used = dirSize(sandboxDir)
        CloudQuota(
            totalBytes = 0L,
            usedBytes = used,
            starCloudUsedBytes = used,
            availableBytes = runCatching { sandboxDir.usableSpace }.getOrNull()
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
            val targetDir = safeChild(remotePath, isDirectory = true)
                ?: return@withContext Result.failure(IOException("مسار غير صالح: $remotePath"))
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                return@withContext Result.failure(IOException("تعذر إنشاء المجلد: $remotePath"))
            }
            val targetFile = File(targetDir, fileName.substringAfterLast('/').substringAfterLast('\\'))
            if (targetFile.canonicalPath != File(targetDir, targetFile.name).canonicalPath) {
                return@withContext Result.failure(IOException("اسم ملف غير صالح"))
            }

            val digest = MessageDigest.getInstance("MD5")
            var totalBytes = 0L
            val buffer = ByteArray(64 * 1024)

            FileOutputStream(targetFile).use { out ->
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    totalBytes += read
                    if (fileSize > 0) onProgress(totalBytes, fileSize)
                }
                out.flush()
            }

            Result.success(
                RemoteFileMetadata(
                    fileId = targetFile.relativeTo(sandboxDir).path,
                    name = targetFile.name,
                    sizeBytes = targetFile.length(),
                    md5Checksum = digest.digest().joinToString("") { "%02x".format(it) },
                    modifiedTime = targetFile.lastModified(),
                    mimeType = mimeType,
                    isDirectory = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(
        remoteFileId: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val file = safeChild(remoteFileId, isDirectory = false)
                ?: return@withContext Result.failure(IOException("مسار غير صالح"))
            if (!file.isFile) {
                return@withContext Result.failure(IOException("الملف غير موجود: $remoteFileId"))
            }
            val bytes = file.readBytes()
            onProgress(bytes.size.toLong(), bytes.size.toLong())
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listFiles(remoteFolder: String): List<RemoteFileMetadata> = withContext(Dispatchers.IO) {
        val folder = safeChild(remoteFolder, isDirectory = true) ?: return@withContext emptyList()
        if (!folder.isDirectory) return@withContext emptyList()
        folder.listFiles()?.map { file ->
            RemoteFileMetadata(
                fileId = file.relativeTo(sandboxDir).path,
                name = file.name,
                sizeBytes = if (file.isFile) file.length() else 0L,
                md5Checksum = null,
                modifiedTime = file.lastModified(),
                mimeType = if (file.isDirectory) "vnd.android.document/directory" else "application/octet-stream",
                isDirectory = file.isDirectory
            )
        } ?: emptyList()
    }

    override suspend fun deleteFile(remoteFileId: String): Boolean = withContext(Dispatchers.IO) {
        safeChild(remoteFileId, isDirectory = false)?.delete() == true
    }

    private fun safeChild(relativePath: String, isDirectory: Boolean): File? {
        val root = sandboxDir.canonicalFile
        val candidate = File(root, relativePath).canonicalFile
        return when {
            candidate == root && isDirectory -> candidate
            candidate.path.startsWith(root.path + File.separator) -> candidate
            else -> null
        }
    }

    private fun dirSize(dir: File): Long = runCatching {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)
}
