package com.example.core.dest

import android.net.Uri
import android.util.Base64
import android.util.Xml
import com.example.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** WebDAV: Nextcloud / ownCloud / Synology / Box / أي خادم WebDAV. */
class WebDavDestination(
    override val id: String,
    override val label: String,
    baseUrl: String,
    private val user: String,
    private val password: String
) : Destination {

    private val base: HttpUrl = (baseUrl.trim().trimEnd('/') + "/").toHttpUrlOrNull()
        ?: throw IllegalArgumentException("رابط الخادم غير صالح")

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    private val auth = "Basic " + Base64.encodeToString("$user:$password".toByteArray(), Base64.NO_WRAP)
    private val madeDirs = HashSet<String>()

    private fun urlOf(dir: List<String>, name: String? = null, trailingSlash: Boolean = false): HttpUrl {
        val b = base.newBuilder()
        dir.forEach { b.addPathSegment(it) }
        if (name != null) b.addPathSegment(name)
        if (trailingSlash) b.addPathSegment("")
        return b.build()
    }

    private fun req(url: HttpUrl) = Request.Builder().url(url).header("Authorization", auth)

    private fun check(r: Response, what: String, allow: Set<Int> = emptySet()) {
        if (r.isSuccessful || r.code in allow) return
        val hint = when (r.code) {
            401, 403 -> "اسم المستخدم أو كلمة المرور غير صحيحة (أو لا توجد صلاحية)"
            404 -> "المسار غير موجود على الخادم"
            507 -> "مساحة الخادم ممتلئة"
            else -> "رمز الخطأ ${r.code}"
        }
        error("$what: $hint")
    }

    private fun ensureDirs(dir: List<String>) {
        val cur = ArrayList<String>()
        for (seg in dir) {
            cur.add(seg)
            val key = cur.joinToString("/")
            if (!madeDirs.add(key)) continue
            client.newCall(
                req(urlOf(cur, trailingSlash = true)).method("MKCOL", null).build()
            ).execute().use { check(it, "إنشاء مجلد «$seg»", setOf(405, 301)) } // 405 = موجود مسبقاً
        }
    }

    override suspend fun prepare() = withContext(Dispatchers.IO) {
        client.newCall(req(base).method("PROPFIND", "".toRequestBody()).header("Depth", "0").build())
            .execute().use { check(it, "الاتصال بالخادم", setOf(207)) }
        ensureDirs(listOf("StarCloud"))
    }

    override suspend fun upload(
        dir: List<String>, name: String, length: Long, modified: Long,
        open: () -> InputStream, onBytes: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        ensureDirs(dir)
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = length
            override fun writeTo(sink: BufferedSink) {
                open().use { input ->
                    val src = input.source()
                    val buf = okio.Buffer()
                    while (true) {
                        val n = src.read(buf, 128 * 1024L); if (n < 0) break
                        sink.write(buf, n); onBytes(n)
                    }
                }
            }
        }
        client.newCall(req(urlOf(dir, name)).put(body).build()).execute()
            .use { check(it, "رفع «$name»") }
        Unit
    }

    override suspend fun list(dir: List<String>): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val url = urlOf(dir, trailingSlash = true)
        client.newCall(req(url).method("PROPFIND", "".toRequestBody()).header("Depth", "1").build())
            .execute().use { r ->
                if (r.code == 404) return@withContext emptyList()
                check(r, "قراءة المجلد", setOf(207))
                parse(r.body?.byteStream() ?: return@withContext emptyList(), url.encodedPath)
            }
    }

    private fun parse(stream: InputStream, selfPath: String): List<RemoteEntry> {
        val out = ArrayList<RemoteEntry>()
        val p = Xml.newPullParser()
        p.setInput(stream, "UTF-8")
        var href = ""; var len = 0L; var mod = 0L; var dir = false
        var tag = ""
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = p.name.substringAfter(':').lowercase()
                    if (tag == "response") { href = ""; len = 0; mod = 0; dir = false }
                    if (tag == "collection") dir = true
                }
                XmlPullParser.TEXT -> {
                    val t = p.text.trim()
                    if (t.isNotEmpty()) when (tag) {
                        "href" -> href = t
                        "getcontentlength" -> len = t.toLongOrNull() ?: 0
                        "getlastmodified" -> mod = runCatching { fmt.parse(t)?.time ?: 0L }.getOrDefault(0L)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name.substringAfter(':').lowercase() == "response" && href.isNotEmpty()) {
                        val path = if (href.startsWith("http")) (Uri.parse(href).path ?: "") else Uri.decode(href.substringBefore('?'))
                        val self = Uri.decode(selfPath)
                        if (path.trimEnd('/') != self.trimEnd('/')) {
                            val name = path.trimEnd('/').substringAfterLast('/')
                            if (name.isNotEmpty()) out.add(RemoteEntry(name, dir, len, mod))
                        }
                    }
                    tag = ""
                }
            }
            ev = p.next()
        }
        return out
    }

    override suspend fun download(dir: List<String>, name: String): InputStream = withContext(Dispatchers.IO) {
        val r = client.newCall(req(urlOf(dir, name)).get().build()).execute()
        if (!r.isSuccessful) { val c = r.code; r.close(); error("تنزيل «$name»: رمز الخطأ $c") }
        r.body!!.byteStream()
    }

    override suspend fun delete(dir: List<String>, name: String) {
        withContext(Dispatchers.IO) {
            client.newCall(req(urlOf(dir, name)).delete().build()).execute().use { check(it, "حذف «$name»", setOf(404)) }
        }
    }

    override suspend fun quota(): QuotaInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val xml = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:quota-available-bytes/><d:quota-used-bytes/></d:prop></d:propfind>"""
            client.newCall(
                req(base).method("PROPFIND", xml.toRequestBody("application/xml".toMediaType())).header("Depth", "0").build()
            ).execute().use { r ->
                val text = r.body?.string().orEmpty()
                fun grab(tag: String) = Regex("<[^>]*$tag[^>]*>\\s*(-?\\d+)\\s*<").find(text)?.groupValues?.get(1)?.toLongOrNull()
                val free = grab("quota-available-bytes"); val used = grab("quota-used-bytes")
                if (free == null && used == null) null
                else QuotaInfo(if (free != null && used != null && free >= 0) free + used else null, used, free?.takeIf { it >= 0 })
            }
        }.getOrNull()
    }
}
