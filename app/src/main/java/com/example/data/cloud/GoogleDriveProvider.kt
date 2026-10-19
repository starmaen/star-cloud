package com.example.data.cloud

import android.content.Context
import com.example.data.model.CloudProviderType
import com.example.data.model.CloudQuota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Real Google Drive provider backed by the Drive REST API v3 and the OAuth
 * access token of the signed-in Google account. Files are stored inside a
 * "Star Cloud" folder in the user's own Drive, so they are visible in the
 * Google Drive app.
 */
class GoogleDriveProvider(
    private val context: Context,
    private val accountManager: GoogleAccountManager
) : CloudStorageProvider {

    override val providerType: CloudProviderType = CloudProviderType.GOOGLE_DRIVE
    override var isConnected: Boolean = false
        private set
    override var userEmail: String? = null
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private val api = "https://www.googleapis.com/drive/v3"
    private val uploadApi = "https://www.googleapis.com/upload/drive/v3"
    private val jsonMedia = "application/json; charset=utf-8".toMediaTypeOrNull()
    private val folderCache = HashMap<String, String>()
    private var cachedToken: String? = null
    private var tokenExpiresAt = 0L

    private suspend fun token(forceRefresh: Boolean = false): String {
        val now = System.currentTimeMillis()
        val cached = cachedToken
        if (!forceRefresh && cached != null && now < tokenExpiresAt) return cached
        val fresh = accountManager.accessToken(forceRefresh)
            ?: throw IOException("لم يتم تسجيل الدخول إلى حساب Google")
        cachedToken = fresh
        tokenExpiresAt = now + 50 * 60 * 1000L
        return fresh
    }

    override suspend fun connect(accountEmail: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = api.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegment("about")
                .addQueryParameter("fields", "user")
                .build().toString()
            requestText("GET", url)
            userEmail = accountEmail ?: accountManager.accountEmail()
            isConnected = true
            true
        } catch (e: Exception) {
            isConnected = false
            false
        }
    }

    override suspend fun disconnect() {
        isConnected = false
    }

    override suspend fun getQuota(): CloudQuota = withContext(Dispatchers.IO) {
        try {
            val url = api.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegment("about")
                .addQueryParameter("fields", "storageQuota,user")
                .build().toString()
            val json = JSONObject(requestText("GET", url))
            val quota = json.optJSONObject("storageQuota")
            val limit = quota?.optString("limit")?.toLongOrNull() ?: 0L
            val usage = quota?.optString("usage")?.toLongOrNull() ?: 0L
            val available = if (limit > 0L) (limit - usage).coerceAtLeast(0L) else null
            CloudQuota(
                totalBytes = limit,
                usedBytes = usage,
                starCloudUsedBytes = runCatching { starCloudUsed() }.getOrDefault(0L),
                availableBytes = available
            )
        } catch (e: Exception) {
            CloudQuota()
        }
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
            val parentId = ensureFolderPath(remotePath)
            val existingId = findChild(parentId, fileName, folder = false)
            val effectiveMime = mimeType.ifBlank { "application/octet-stream" }

            val urlBuilder = uploadApi.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegment("files")
            if (existingId != null) urlBuilder.addPathSegment(existingId)
            val startUrl = urlBuilder
                .addQueryParameter("uploadType", "resumable")
                .addQueryParameter("fields", "id,name,size,mimeType,modifiedTime,md5Checksum")
                .addQueryParameter("supportsAllDrives", "true")
                .build().toString()

            val metadata = JSONObject().apply {
                put("name", fileName)
                put("mimeType", effectiveMime)
                if (existingId == null) {
                    put("parents", JSONArray().put(parentId))
                }
            }
            val startBody = metadata.toString().toRequestBody(jsonMedia)
            val startRequest = Request.Builder()
                .url(startUrl)
                .header("Authorization", "Bearer ${token()}")
                .header("X-Upload-Content-Type", effectiveMime)
                .header("X-Upload-Content-Length", fileSize.toString())
                .method(if (existingId != null) "PATCH" else "POST", startBody)
                .build()
            val location = client.newCall(startRequest).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("تعذر بدء الرفع (${response.code}): ${text.take(300)}")
                }
                response.header("Location")
                    ?: throw IOException("لم يُرجع Drive رابط الجلسة")
            }

            var uploaded = 0L
            val mediaBody = object : RequestBody() {
                override fun contentType() = effectiveMime.toMediaTypeOrNull()
                override fun contentLength(): Long = fileSize
                override fun writeTo(sink: BufferedSink) {
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        uploaded += read
                        if (fileSize > 0) onProgress(uploaded, fileSize)
                    }
                }
            }
            val putRequest = Request.Builder()
                .url(location)
                .header("Content-Type", effectiveMime)
                .header("Content-Length", fileSize.toString())
                .put(mediaBody)
                .build()
            val responseText = client.newCall(putRequest).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("فشل رفع الملف (${response.code}): ${text.take(300)}")
                }
                text
            }
            val json = JSONObject(responseText)
            Result.success(
                RemoteFileMetadata(
                    fileId = json.optString("id"),
                    name = json.optString("name", fileName),
                    sizeBytes = json.optString("size").toLongOrNull()
                        ?: uploaded.takeIf { it > 0 } ?: fileSize,
                    md5Checksum = json.optString("md5Checksum").takeIf { it.isNotBlank() },
                    modifiedTime = parseRfc3339(json.optString("modifiedTime")),
                    mimeType = json.optString("mimeType", effectiveMime),
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
            val url = api.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegment("files")
                .addPathSegment(remoteFileId)
                .addQueryParameter("alt", "media")
                .addQueryParameter("supportsAllDrives", "true")
                .build().toString()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${token()}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("فشل التنزيل (${response.code})")
                }
                val body = response.body ?: throw IOException("استجابة فارغة")
                val total = body.contentLength()
                val out = ByteArrayOutputStream()
                var readTotal = 0L
                body.byteStream().use { stream ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        readTotal += read
                        if (total > 0) onProgress(readTotal, total)
                    }
                }
                Result.success(out.toByteArray())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listFiles(remoteFolder: String): List<RemoteFileMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val parentId = ensureFolderPath(remoteFolder)
            queryChildren(parentId, fields = FILE_FIELDS)
        }.getOrDefault(emptyList())
    }

    override suspend fun deleteFile(remoteFileId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val url = api.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegment("files")
                .addPathSegment(remoteFileId)
                .addQueryParameter("supportsAllDrives", "true")
                .build().toString()
            requestText("DELETE", url)
            true
        }.getOrDefault(false)
    }

    private suspend fun queryChildren(parentId: String, fields: String): List<RemoteFileMetadata> {
        val result = mutableListOf<RemoteFileMetadata>()
        var pageToken: String? = null
        var pages = 0
        do {
            val builder = api.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegment("files")
                .addQueryParameter("q", "'$parentId' in parents and trashed=false")
                .addQueryParameter("fields", "nextPageToken,files($fields)")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
            pageToken?.let { builder.addQueryParameter("pageToken", it) }
            val json = JSONObject(requestText("GET", builder.build().toString()))
            val files = json.optJSONArray("files") ?: break
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                result += RemoteFileMetadata(
                    fileId = file.optString("id"),
                    name = file.optString("name"),
                    sizeBytes = file.optString("size").toLongOrNull() ?: 0L,
                    md5Checksum = file.optString("md5Checksum").takeIf { it.isNotBlank() },
                    modifiedTime = parseRfc3339(file.optString("modifiedTime")),
                    mimeType = file.optString("mimeType", "application/octet-stream"),
                    isDirectory = file.optString("mimeType") == FOLDER_MIME
                )
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            pages++
        } while (pageToken != null && pages < 10)
        return result
    }

    private suspend fun starCloudUsed(): Long {
        val rootId = rootFolderId()
        return sumSizes(rootId, depth = 0)
    }

    private suspend fun sumSizes(folderId: String, depth: Int): Long {
        if (depth > 4) return 0L
        var total = 0L
        for (item in queryChildren(folderId, fields = "id,name,size,mimeType")) {
            total += if (item.isDirectory) sumSizes(item.fileId, depth + 1) else item.sizeBytes
        }
        return total
    }

    private suspend fun findChild(parentId: String, name: String, folder: Boolean): String? {
        val query = buildString {
            append("name='").append(escape(name)).append("'")
            append(" and '").append(parentId).append("' in parents")
            append(" and trashed=false")
            if (folder) append(" and mimeType='$FOLDER_MIME'")
        }
        val url = api.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegment("files")
            .addQueryParameter("q", query)
            .addQueryParameter("fields", "files(id)")
            .addQueryParameter("pageSize", "10")
            .addQueryParameter("supportsAllDrives", "true")
            .build().toString()
        val files = JSONObject(requestText("GET", url)).optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        return files.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
    }

    private suspend fun createFolder(parentId: String, name: String): String {
        val metadata = JSONObject().apply {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            put("parents", JSONArray().put(parentId))
        }
        val url = api.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegment("files")
            .addQueryParameter("fields", "id")
            .addQueryParameter("supportsAllDrives", "true")
            .build().toString()
        val id = JSONObject(requestText("POST", url, metadata.toString().toRequestBody(jsonMedia)))
            .optString("id")
        if (id.isBlank()) throw IOException("تعذر إنشاء المجلد: $name")
        return id
    }

    private suspend fun ensureFolder(parentId: String, name: String): String {
        val key = "$parentId/$name"
        folderCache[key]?.let { return it }
        val id = findChild(parentId, name, folder = true) ?: createFolder(parentId, name)
        folderCache[key] = id
        return id
    }

    private suspend fun rootFolderId(): String = ensureFolder("root", BASE_FOLDER)

    private suspend fun ensureFolderPath(remotePath: String): String {
        var parent = rootFolderId()
        val segments = remotePath.split('/', '\\')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        for (segment in segments) {
            parent = ensureFolder(parent, segment)
        }
        return parent
    }

    private suspend fun requestText(method: String, url: String, body: RequestBody? = null): String {
        var refresh = false
        repeat(2) { attempt ->
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${token(forceRefresh = refresh)}")
                .method(method, body)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) return text
                if (response.code == 401 && attempt == 0) {
                    refresh = true
                } else {
                    throw IOException("Drive API ${response.code}: ${text.take(300)}")
                }
            }
        }
        throw IOException("تعذر الاتصال بـ Google Drive")
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    private fun parseRfc3339(value: String): Long {
        if (value.isBlank()) return System.currentTimeMillis()
        return runCatching {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(value)?.time ?: System.currentTimeMillis()
        }.getOrDefault(System.currentTimeMillis())
    }

    companion object {
        const val BASE_FOLDER = "Star Cloud"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val FILE_FIELDS = "id,name,size,mimeType,modifiedTime,md5Checksum"
    }
}
