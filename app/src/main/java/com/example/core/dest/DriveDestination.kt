package com.example.core.dest

import com.example.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.InputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Google Drive عبر REST + رفع قابل للاستئناف. نطاق drive.file (يرى ملفات التطبيق فقط). */
class DriveDestination(
    override val id: String,
    override val label: String,
    private val google: GoogleAccount
) : Destination {

    private class Item(val id: String, val isDir: Boolean, val size: Long, val modified: Long)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    private var token: String? = null
    private val cache = HashMap<String, MutableMap<String, Item>>()
    private val dirIds = HashMap<String, String>()
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private suspend fun call(build: (String) -> Request): Response {
        var t = token ?: google.token() ?: error("سجّل الدخول بحساب Google أولاً")
        token = t
        var r = client.newCall(build(t)).execute()
        if (r.code == 401) {
            r.close()
            t = google.token(refresh = true) ?: error("انتهت جلسة Google، سجّل الدخول من جديد")
            token = t
            r = client.newCall(build(t)).execute()
        }
        return r
    }

    private fun fail(r: Response, what: String): Nothing {
        val body = r.body?.string().orEmpty().take(200); val code = r.code; r.close()
        val hint = when (code) {
            403 -> "ممنوع — فعّل Drive API في Google Cloud أو المساحة ممتلئة"
            else -> "رمز $code $body"
        }
        error("$what: $hint")
    }

    private suspend fun children(parent: String): MutableMap<String, Item> {
        cache[parent]?.let { return it }
        val map = LinkedHashMap<String, Item>()
        var page: String? = null
        do {
            val q = URLEncoder.encode("'$parent' in parents and trashed=false", "UTF-8")
            val fields = URLEncoder.encode("nextPageToken,files(id,name,mimeType,size,modifiedTime)", "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$q&fields=$fields&pageSize=1000" +
                (page?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") } ?: "")
            val r = call { Request.Builder().url(url).header("Authorization", "Bearer $it").build() }
            if (!r.isSuccessful) fail(r, "قراءة المجلد")
            val j = JSONObject(r.body!!.string()); r.close()
            val arr = j.optJSONArray("files")
            if (arr != null) for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                map[f.getString("name")] = Item(
                    f.getString("id"),
                    f.optString("mimeType") == "application/vnd.google-apps.folder",
                    f.optString("size", "0").toLongOrNull() ?: 0,
                    runCatching { iso.parse(f.optString("modifiedTime"))?.time ?: 0L }.getOrDefault(0L)
                )
            }
            page = j.optString("nextPageToken").ifBlank { null }
        } while (page != null)
        cache[parent] = map
        return map
    }

    private suspend fun ensureDir(dir: List<String>, create: Boolean): String? {
        var parent = "root"; val key = StringBuilder()
        for (seg in dir) {
            key.append('/').append(seg)
            dirIds[key.toString()]?.let { parent = it; continue }
            val kids = children(parent)
            val ex = kids[seg]
            val id = if (ex != null && ex.isDir) ex.id else {
                if (!create) return null
                val meta = JSONObject().put("name", seg).put("mimeType", "application/vnd.google-apps.folder")
                    .put("parents", org.json.JSONArray().put(parent)).toString()
                val r = call {
                    Request.Builder().url("https://www.googleapis.com/drive/v3/files?fields=id")
                        .header("Authorization", "Bearer $it")
                        .post(meta.toRequestBody("application/json".toMediaType())).build()
                }
                if (!r.isSuccessful) fail(r, "إنشاء مجلد «$seg»")
                val nid = JSONObject(r.body!!.string()).getString("id"); r.close()
                kids[seg] = Item(nid, true, 0, 0); nid
            }
            dirIds[key.toString()] = id; parent = id
        }
        return parent
    }

    override suspend fun prepare() {
        withContext(Dispatchers.IO) {
            google.last() ?: error("لم تسجّل الدخول بحساب Google بعد")
            ensureDir(listOf("StarCloud"), true)
        }
    }

    override suspend fun upload(
        dir: List<String>, name: String, length: Long, modified: Long,
        open: () -> InputStream, onBytes: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val parent = ensureDir(dir, true)!!
        val kids = children(parent)
        val existing = kids[name]
        val meta = JSONObject().put("name", name).apply {
            if (modified > 0) put("modifiedTime", iso.format(Date(modified)))
            if (existing == null) put("parents", org.json.JSONArray().put(parent))
        }.toString()
        val startUrl = if (existing == null)
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id"
        else "https://www.googleapis.com/upload/drive/v3/files/${existing.id}?uploadType=resumable&fields=id"
        val start = call {
            val b = Request.Builder().url(startUrl).header("Authorization", "Bearer $it")
                .header("X-Upload-Content-Length", length.toString())
            val body = meta.toRequestBody("application/json; charset=UTF-8".toMediaType())
            (if (existing == null) b.post(body) else b.patch(body)).build()
        }
        if (!start.isSuccessful) fail(start, "بدء رفع «$name»")
        val session = start.header("Location") ?: run { start.close(); error("لم يُرجع Drive رابط الرفع") }
        start.close()

        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = length
            override fun writeTo(sink: BufferedSink) {
                open().use { input ->
                    val src = input.source(); val buf = okio.Buffer()
                    while (true) {
                        val n = src.read(buf, 256 * 1024L); if (n < 0) break
                        sink.write(buf, n); onBytes(n)
                    }
                }
            }
        }
        val up = client.newCall(Request.Builder().url(session).put(body).build()).execute()
        if (!up.isSuccessful) fail(up, "رفع «$name»")
        val nid = JSONObject(up.body!!.string()).optString("id", existing?.id ?: ""); up.close()
        kids[name] = Item(nid, false, length, modified)
        Unit
    }

    override suspend fun list(dir: List<String>): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val p = ensureDir(dir, false) ?: return@withContext emptyList()
        cache.remove(p)
        children(p).map { (n, v) -> RemoteEntry(n, v.isDir, v.size, v.modified) }
    }

    override suspend fun download(dir: List<String>, name: String): InputStream = withContext(Dispatchers.IO) {
        val p = ensureDir(dir, false) ?: error("المجلد غير موجود")
        val item = children(p)[name] ?: error("الملف غير موجود: $name")
        val r = call { Request.Builder().url("https://www.googleapis.com/drive/v3/files/${item.id}?alt=media").header("Authorization", "Bearer $it").build() }
        if (!r.isSuccessful) fail(r, "تنزيل «$name»")
        r.body!!.byteStream()
    }

    override suspend fun delete(dir: List<String>, name: String) {
        withContext(Dispatchers.IO) {
            val p = ensureDir(dir, false) ?: return@withContext
            val item = children(p)[name] ?: return@withContext
            call { Request.Builder().url("https://www.googleapis.com/drive/v3/files/${item.id}").header("Authorization", "Bearer $it").delete().build() }.close()
            children(p).remove(name)
        }
    }

    override suspend fun quota(): QuotaInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val r = call { Request.Builder().url("https://www.googleapis.com/drive/v3/about?fields=storageQuota").header("Authorization", "Bearer $it").build() }
            r.use {
                if (!it.isSuccessful) return@runCatching null
                val q = JSONObject(it.body!!.string()).optJSONObject("storageQuota") ?: return@runCatching null
                val limit = q.optString("limit").toLongOrNull(); val usage = q.optString("usage").toLongOrNull()
                QuotaInfo(limit, usage, if (limit != null && usage != null) (limit - usage).coerceAtLeast(0) else null)
            }
        }.getOrNull()
    }
}
