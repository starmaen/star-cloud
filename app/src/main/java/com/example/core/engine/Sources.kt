package com.example.core.engine

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager as SM
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.example.core.QuotaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

data class AppItem(
    val pkg: String,
    val label: String,
    val version: String,
    val isSystem: Boolean,
    val apkFiles: List<File>,
    val apkBytes: Long
)

object Sources {

    // ---------- التطبيقات ----------
    suspend fun installedApps(context: Context): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .filter { it.packageName != context.packageName }
            .map { ai ->
                val files = buildList {
                    add(File(ai.sourceDir))
                    ai.splitSourceDirs?.forEach { add(File(it)) }
                }.filter { it.exists() }
                val ver = runCatching { pm.getPackageInfo(ai.packageName, 0).versionName }.getOrNull() ?: ""
                AppItem(
                    ai.packageName,
                    pm.getApplicationLabel(ai).toString(),
                    ver,
                    (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                    files,
                    files.sumOf { it.length() }
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun appItem(context: Context, pkg: String): AppItem? = runCatching {
        val pm = context.packageManager
        val ai = pm.getApplicationInfo(pkg, 0)
        val files = buildList { add(File(ai.sourceDir)); ai.splitSourceDirs?.forEach { add(File(it)) } }.filter { it.exists() }
        AppItem(pkg, pm.getApplicationLabel(ai).toString(), pm.getPackageInfo(pkg, 0).versionName ?: "",
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0, files, files.sumOf { it.length() })
    }.getOrNull()

    fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    // ---------- جهات الاتصال (vCard كامل بالصور وكل الحقول) ----------
    suspend fun exportContacts(context: Context, out: File): Int = withContext(Dispatchers.IO) {
        var count = 0
        out.outputStream().buffered().use { os ->
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val key = c.getString(0) ?: continue
                    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, key)
                    runCatching {
                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                            afd.createInputStream().use { it.copyTo(os) }
                            os.write("\r\n".toByteArray()); count++
                        }
                    }
                }
            }
        }
        count
    }

    // ---------- الرسائل ----------
    suspend fun exportSms(context: Context, out: File): Int = withContext(Dispatchers.IO) {
        var n = 0
        JsonWriter(out.writer().buffered()).use { w ->
            w.beginArray()
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT,
                    Telephony.Sms.TYPE, Telephony.Sms.READ),
                null, null, "date ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    w.beginObject()
                    w.name("address").value(c.getString(0) ?: "")
                    w.name("body").value(c.getString(1) ?: "")
                    w.name("date").value(c.getLong(2))
                    w.name("dateSent").value(c.getLong(3))
                    w.name("type").value(c.getInt(4).toLong())
                    w.name("read").value(c.getInt(5).toLong())
                    w.endObject(); n++
                }
            }
            w.endArray()
        }
        n
    }

    suspend fun exportCalls(context: Context, out: File): Int = withContext(Dispatchers.IO) {
        var n = 0
        JsonWriter(out.writer().buffered()).use { w ->
            w.beginArray()
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME),
                null, null, "date ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    w.beginObject()
                    w.name("number").value(c.getString(0) ?: "")
                    w.name("date").value(c.getLong(1))
                    w.name("duration").value(c.getLong(2))
                    w.name("type").value(c.getInt(3).toLong())
                    w.name("name").value(c.getString(4) ?: "")
                    w.endObject(); n++
                }
            }
            w.endArray()
        }
        n
    }

    private fun readObjects(input: InputStream, each: (Map<String, Any?>) -> Unit) {
        JsonReader(input.reader().buffered()).use { r ->
            r.beginArray()
            while (r.hasNext()) {
                val m = HashMap<String, Any?>()
                r.beginObject()
                while (r.hasNext()) {
                    val k = r.nextName()
                    m[k] = when (r.peek()) {
                        JsonToken.NUMBER -> r.nextLong()
                        JsonToken.STRING -> r.nextString()
                        else -> { r.skipValue(); null }
                    }
                }
                r.endObject(); each(m)
            }
            r.endArray()
        }
    }

    fun isDefaultSmsApp(context: Context) = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    /** يحتاج أن يكون التطبيق «تطبيق الرسائل الافتراضي» مؤقتاً. يتجاهل المكرر. */
    suspend fun importSms(context: Context, input: InputStream): Int = withContext(Dispatchers.IO) {
        val existing = HashSet<String>()
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf("date", "address", "body"), null, null, null)?.use { c ->
            while (c.moveToNext()) existing.add("${c.getLong(0)}|${c.getString(1)}|${(c.getString(2) ?: "").hashCode()}")
        }
        var added = 0
        readObjects(input) { m ->
            val date = (m["date"] as? Long) ?: 0L
            val addr = (m["address"] as? String) ?: ""
            val body = (m["body"] as? String) ?: ""
            if (existing.add("$date|$addr|${body.hashCode()}")) {
                val v = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, addr); put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, date); put(Telephony.Sms.DATE_SENT, (m["dateSent"] as? Long) ?: date)
                    put(Telephony.Sms.TYPE, ((m["type"] as? Long) ?: 1L).toInt())
                    put(Telephony.Sms.READ, ((m["read"] as? Long) ?: 1L).toInt())
                }
                if (context.contentResolver.insert(Telephony.Sms.CONTENT_URI, v) != null) added++
            }
        }
        added
    }

    suspend fun importCalls(context: Context, input: InputStream): Int = withContext(Dispatchers.IO) {
        val existing = HashSet<String>()
        context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf("date", "number"), null, null, null)?.use { c ->
            while (c.moveToNext()) existing.add("${c.getLong(0)}|${c.getString(1)}")
        }
        var added = 0
        readObjects(input) { m ->
            val date = (m["date"] as? Long) ?: 0L
            val num = (m["number"] as? String) ?: ""
            if (existing.add("$date|$num")) {
                val v = ContentValues().apply {
                    put(CallLog.Calls.NUMBER, num); put(CallLog.Calls.DATE, date)
                    put(CallLog.Calls.DURATION, (m["duration"] as? Long) ?: 0L)
                    put(CallLog.Calls.TYPE, ((m["type"] as? Long) ?: 1L).toInt())
                    put(CallLog.Calls.CACHED_NAME, (m["name"] as? String) ?: "")
                }
                if (context.contentResolver.insert(CallLog.Calls.CONTENT_URI, v) != null) added++
            }
        }
        added
    }
}

/** أرقام التخزين الفعلية للجهاز (لا تقديرات). */
object DeviceStorage {
    fun read(context: Context): QuotaInfo {
        val dir = Environment.getExternalStorageDirectory()
        val st = StatFs(dir.path)
        val free = st.availableBytes
        var total = st.totalBytes
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching {
                val ssm = context.getSystemService(android.app.usage.StorageStatsManager::class.java)
                total = ssm.getTotalBytes(SM.UUID_DEFAULT)
            }
        }
        return QuotaInfo(total, (total - free).coerceAtLeast(0), free)
    }
}
