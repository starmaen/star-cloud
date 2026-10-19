package com.example.data.cloud

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.CloudProviderType
import com.example.data.model.CloudQuota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Real storage provider backed by a user-selected SAF tree URI.
 *
 * The same implementation works for Google Drive, OneDrive, Dropbox,
 * the internal shared storage and removable SD cards, because every
 * DocumentsProvider exposes the same DocumentsContract API.
 *
 * All Star Cloud files are placed inside a "Star Cloud" folder under the
 * selected tree so they are easy to find and measure.
 */
class SafCloudProvider(
    private val context: Context,
    private val treeUri: Uri,
    override val providerType: CloudProviderType
) : CloudStorageProvider {

    private val root: DocumentFile? by lazy { runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull() }

    override var isConnected: Boolean = false
        private set

    override var userEmail: String? = null
        private set

    override suspend fun connect(accountEmail: String?): Boolean = withContext(Dispatchers.IO) {
        val dir = root
        isConnected = dir != null && dir.exists() && dir.canWrite()
        isConnected
    }

    override suspend fun disconnect() {
        isConnected = false
    }

    override suspend fun getQuota(): CloudQuota = withContext(Dispatchers.IO) {
        val used = runCatching { baseDir()?.let { dirSize(it, 0) } ?: 0L }.getOrDefault(0L)
        val available = runCatching { queryQuotaColumn(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES) }.getOrNull()
        val total = runCatching { queryQuotaColumn(DocumentsContract.Root.COLUMN_CAPACITY_BYTES) }.getOrNull()
        CloudQuota(
            totalBytes = total ?: 0L,
            usedBytes = used,
            starCloudUsedBytes = used,
            availableBytes = available
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
            if (!connect(null)) {
                return@withContext Result.failure(IOException("لا يوجد إذن كتابة على المجلد المختار"))
            }
            val dir = resolveDir(remotePath, create = true)
                ?: return@withContext Result.failure(IOException("تعذر إنشاء المجلد: $remotePath"))

            var target = dir.findFile(fileName)
            if (target?.isDirectory == true) {
                return@withContext Result.failure(IOException("يوجد مجلد بنفس الاسم: $fileName"))
            }
            if (target != null) {
                runCatching { target.delete() }
            }
            val created = dir.createFile(mimeType.ifBlank { "application/octet-stream" }, fileName)
                ?: dir.findFile(fileName)
                ?: return@withContext Result.failure(IOException("تعذر إنشاء الملف: $fileName"))

            val digest = MessageDigest.getInstance("MD5")
            var written = 0L
            val output = openOutput(created.uri)
                ?: return@withContext Result.failure(IOException("تعذر فتح الملف للكتابة: $fileName"))

            output.use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    written += read
                    if (fileSize > 0) onProgress(written, fileSize)
                }
                out.flush()
            }

            Result.success(
                RemoteFileMetadata(
                    fileId = created.uri.toString(),
                    name = created.name ?: fileName,
                    sizeBytes = written,
                    md5Checksum = digest.digest().joinToString("") { "%02x".format(it) },
                    modifiedTime = created.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                    mimeType = mimeType,
                    isDirectory = false
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed for $fileName", e)
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(
        remoteFileId: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(remoteFileId)
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IOException("تعذر فتح الملف"))
            input.use { stream ->
                val expected = DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var readTotal = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    readTotal += read
                    if (expected > 0) onProgress(readTotal, expected)
                }
                Result.success(out.toByteArray())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listFiles(remoteFolder: String): List<RemoteFileMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = resolveDir(remoteFolder, create = false) ?: return@runCatching emptyList()
            dir.listFiles().map { file ->
                RemoteFileMetadata(
                    fileId = file.uri.toString(),
                    name = file.name ?: "",
                    sizeBytes = file.length(),
                    md5Checksum = null,
                    modifiedTime = file.lastModified(),
                    mimeType = file.type ?: "application/octet-stream",
                    isDirectory = file.isDirectory
                )
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun deleteFile(remoteFileId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            DocumentFile.fromSingleUri(context, Uri.parse(remoteFileId))?.delete() ?: false
        }.getOrDefault(false)
    }

    private fun openOutput(uri: Uri): java.io.OutputStream? {
        return runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: runCatching { context.contentResolver.openOutputStream(uri, "w") }.getOrNull()
            ?: runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
    }

    private fun baseDir(): DocumentFile? {
        val dir = root ?: return null
        return dir.findFile(BASE_FOLDER)?.takeIf { it.isDirectory }
            ?: runCatching { dir.createDirectory(BASE_FOLDER) }.getOrNull()
    }

    private fun resolveDir(remotePath: String, create: Boolean): DocumentFile? {
        var current = baseDir() ?: return null
        val segments = remotePath.split('/', '\\')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        for (segment in segments) {
            val existing = current.findFile(segment)
            current = when {
                existing?.isDirectory == true -> existing
                existing != null -> return null
                create -> current.createDirectory(segment) ?: return null
                else -> return null
            }
        }
        return current
    }

    private fun queryQuotaColumn(column: String): Long? {
        val authority = treeUri.authority ?: return null
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()

        // Some providers expose quota columns on the selected document.
        if (treeDocId != null) {
            val docValue = runCatching {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
                context.contentResolver.query(
                    docUri,
                    arrayOf(column),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                }
            }.getOrNull()
            if (docValue != null && docValue > 0L) return docValue
        }

        val rootIds = linkedSetOf<String>()
        treeDocId?.substringBefore(':')?.takeIf { it.isNotBlank() }?.let { rootIds += it }
        rootIds += "root"
        for (rootId in rootIds) {
            val value = runCatching {
                val rootUri = DocumentsContract.buildRootUri(authority, rootId)
                context.contentResolver.query(
                    rootUri,
                    arrayOf(column),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                }
            }.getOrNull()
            if (value != null && value > 0L) return value
        }

        return runCatching {
            val rootsUri = DocumentsContract.buildRootsUri(authority)
            context.contentResolver.query(
                rootsUri,
                arrayOf(column),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (!cursor.isNull(0)) {
                        val value = cursor.getLong(0)
                        if (value > 0L) return@use value
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun dirSize(dir: DocumentFile, depth: Int): Long {
        if (depth > 6) return 0L
        return runCatching {
            dir.listFiles().sumOf { file ->
                if (file.isDirectory) dirSize(file, depth + 1) else file.length().coerceAtLeast(0L)
            }
        }.getOrDefault(0L)
    }

    companion object {
        private const val TAG = "SafCloudProvider"
        const val BASE_FOLDER = "Star Cloud"
    }
}
