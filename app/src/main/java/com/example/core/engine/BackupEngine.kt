package com.example.core.engine

import android.content.Context
import com.example.core.*
import com.example.core.data.*
import com.example.core.dest.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Layout {
    const val ROOT = "StarCloud"
    const val FILES = "Files"
    const val APPS = "Apps"
    const val CONTACTS = "Contacts"
    const val MESSAGES = "Messages"
    const val MANIFEST = "manifest.json"
    fun base(device: String) = listOf(ROOT, device)
    fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}

private class LocalFile(val file: File, val dir: List<String>, val size: Long, val modified: Long) {
    val name get() = file.name
    val key get() = (dir + file.name).joinToString("/")
}

/**
 * محرك النسخ الاحتياطي.
 * - تزايدي: لا يرفع إلا الجديد أو المتغيّر (حجم + تاريخ تعديل) بالاعتماد على فهرس محلي.
 * - يدخل كل المجلدات الفرعية.
 * - يدعم أكثر من وجهة في نفس العملية.
 * - أي خطأ في ملف لا يوقف باقي النسخ، ويُعرض للمستخدم بوضوح.
 */
class BackupEngine(
    private val context: Context,
    private val settings: Settings,
    private val db: StarDb,
    private val google: GoogleAccount,
    private val tracker: Tracker
) {
    private val dao = db.dao()

    private class DestRun(val dest: Destination, val index: HashMap<String, FileIndexEntity>) {
        val pending = ArrayList<FileIndexEntity>()
        var consecutiveFailures = 0
        var dead = false
    }

    suspend fun run(): Boolean = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val sel = settings.selection
        tracker.begin(false, "تجهيز النسخ الاحتياطي…")
        val base = Layout.base(settings.deviceName)
        var ok = false
        var summary = ""
        try {
            val configs = settings.destinations.filter { it.enabled }
            if (configs.isEmpty()) error("لم تُضف وجهة للنسخ. أضف وجهة من تبويب «الوجهات».")
            if (sel.isEmpty) error("لم تختر شيئاً لنسخه. اختر تطبيقات أو ملفات أو جهات اتصال.")

            // 1) تجهيز الوجهات (فحص فعلي)
            val runs = ArrayList<DestRun>()
            for (c in configs) {
                tracker.phase("الاتصال بـ «${c.label}»…")
                try {
                    val d = DestinationFactory.create(context, c, google)
                    d.prepare()
                    val idx = HashMap<String, FileIndexEntity>()
                    dao.indexFor(c.id).forEach { idx[it.path] = it }
                    runs.add(DestRun(d, idx))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { tracker.error("«${c.label}»: ${e.message ?: "تعذّر الاتصال"}") }
            }
            if (runs.isEmpty()) error("تعذّر الاتصال بأي وجهة. راجع الأخطاء أدناه.")

            // 2) مسح الملفات
            tracker.phase("فحص الملفات المختارة…")
            val files = if (sel.paths.isNotEmpty()) scan(sel.paths) else emptyList()
            val appItems = sel.apps.mapNotNull { ch -> Sources.appItem(context, ch.pkg)?.let { it to ch } }
            val destCount = runs.size
            val smallItems = (if (sel.contacts) 1 else 0) + (if (sel.sms) 1 else 0) + (if (sel.calls) 1 else 0)
            tracker.totals(
                (files.size + appItems.size + smallItems) * destCount,
                (files.sumOf { it.size } + appItems.sumOf { pair -> pair.first.apkBytes }) * destCount
            )

            // 3) الأصناف الصغيرة أولاً (الأهم)
            val tmp = File(context.cacheDir, "run").apply { deleteRecursively(); mkdirs() }
            val stamp = Layout.stamp()
            if (sel.contacts) versioned(runs, base, Layout.CONTACTS, "contacts", "vcf", tmp, stamp, "جهات الاتصال") { f -> Sources.exportContacts(context, f) }
            if (sel.sms) versioned(runs, base, Layout.MESSAGES, "sms", "json", tmp, stamp, "الرسائل") { f -> Sources.exportSms(context, f) }
            if (sel.calls) versioned(runs, base, Layout.MESSAGES, "calls", "json", tmp, stamp, "سجل المكالمات") { f -> Sources.exportCalls(context, f) }

            // 4) التطبيقات
            val appMeta = ArrayList<JSONObject>()
            if (appItems.isNotEmpty()) backupApps(runs, base, appItems, tmp, appMeta)

            // 5) الملفات
            val seen = HashSet<String>()
            for (lf in files) {
                currentCoroutineContext().ensureActive()
                tracker.current(lf.name)
                seen.add(lf.key)
                val remoteDir = base + Layout.FILES + lf.dir
                for (r in runs) {
                    if (r.dead) continue
                    val key = Layout.FILES + "/" + lf.key
                    val old = r.index[key]
                    if (old != null && old.size == lf.size && old.modified == lf.modified) { tracker.itemSkipped(lf.size); continue }
                    if (uploadFile(r, remoteDir, lf.name, lf.file)) {
                        val e = FileIndexEntity(r.dest.id, key, lf.size, lf.modified)
                        r.index[key] = e; r.pending.add(e)
                        tracker.itemDone()
                        if (r.pending.size >= 200) flush(r)
                    }
                }
            }
            runs.forEach { flush(it) }

            // 6) حذف ما أُزيل من الهاتف (فقط إن طلب المستخدم نسخة مطابقة)
            if (!settings.keepDeleted && sel.paths.isNotEmpty()) mirrorDelete(runs, base, seen, files)

            // 7) manifest
            tracker.phase("إنهاء النسخ…")
            for (r in runs) if (!r.dead) writeManifest(r, base, appMeta, tmp)
            tmp.deleteRecursively()

            val errs = tracker.errorTotal
            summary = buildString {
                append("تم رفع ${tracker.doneItems - tracker.skippedCount} عنصراً")
                append(" (${formatBytes(tracker.doneBytes)})")
                if (tracker.skippedCount > 0) append(" • ${tracker.skippedCount} دون تغيير")
                if (errs > 0) append(" • $errs أخطاء")
            }
            ok = errs == 0
        } catch (e: CancellationException) {
            summary = "أُوقفت النسخ يدوياً"
            tracker.finish(summary)
            logRun(started, false, summary)
            throw e
        } catch (e: Exception) {
            summary = e.message ?: "فشل غير معروف"
            tracker.error(summary)
            ok = false
        }
        tracker.finish(summary)
        logRun(started, ok, summary)
        ok
    }

    private suspend fun logRun(started: Long, ok: Boolean, summary: String) {
        runCatching {
            dao.addLog(RunLogEntity(time = System.currentTimeMillis(), kind = "backup", ok = ok,
                items = tracker.doneItems, bytes = tracker.doneBytes,
                seconds = (System.currentTimeMillis() - started) / 1000, summary = summary))
        }
    }

    // ---------------- مسح الملفات ----------------
    private fun scan(paths: List<String>): List<LocalFile> {
        val out = ArrayList<LocalFile>()
        val maxBytes = if (settings.maxFileMb > 0) settings.maxFileMb * 1024L * 1024L else Long.MAX_VALUE
        val done = HashSet<String>()
        for (p in paths) {
            val f = File(p)
            if (!f.exists()) continue
            if (f.isFile) {
                if (done.add(f.path) && f.length() <= maxBytes && f.canRead())
                    out.add(LocalFile(f, segs(f.parentFile?.path ?: "/"), f.length(), f.lastModified()))
                continue
            }
            f.walkTopDown()
                .onEnter { d -> d.name != ".thumbnails" && d.name != ".cache" && d.canRead() }
                .filter { it.isFile && done.add(it.path) }
                .forEach { x ->
                    val n = x.name
                    if (n.endsWith(".tmp") || n.endsWith(".part") || n.startsWith(".trashed-") || n.endsWith(".crdownload")) return@forEach
                    val len = x.length()
                    if (len <= maxBytes && x.canRead()) out.add(LocalFile(x, segs(x.parentFile?.path ?: "/"), len, x.lastModified()))
                }
        }
        return out
    }

    private fun segs(path: String) = path.split('/').filter { it.isNotEmpty() }

    // ---------------- رفع ----------------
    private suspend fun uploadFile(r: DestRun, dir: List<String>, name: String, file: File, countBytes: Boolean = true): Boolean {
        var lastErr: Exception? = null
        for (attempt in 1..2) {
            currentCoroutineContext().ensureActive()
            var sent = 0L
            try {
                r.dest.upload(dir, name, file.length(), file.lastModified(), { file.inputStream() }) { n ->
                    sent += n; if (countBytes) tracker.bytes(n)
                }
                r.consecutiveFailures = 0
                return true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                lastErr = e
                if (countBytes && sent > 0) tracker.bytes(-sent) // لا نحسب ما لم يكتمل
                if (attempt == 1) delay(1500)
            }
        }
        tracker.error("«$name» → ${r.dest.label}: ${lastErr?.message ?: "فشل الرفع"}")
        if (++r.consecutiveFailures >= 15) {
            r.dead = true
            tracker.error("أُوقف الرفع إلى «${r.dest.label}» بعد فشل متكرر. تحقق من الاتصال أو المساحة.")
        }
        return false
    }

    private suspend fun flush(r: DestRun) {
        if (r.pending.isEmpty()) return
        dao.putIndex(r.pending.toList()); r.pending.clear()
    }

    /** ملفات بإصدارات (جهات اتصال/رسائل): يُحتفظ بآخر N نسخة. */
    private suspend fun versioned(
        runs: List<DestRun>, base: List<String>, folder: String, prefix: String, ext: String,
        tmp: File, stamp: String, title: String, produce: suspend (File) -> Int
    ) {
        currentCoroutineContext().ensureActive()
        tracker.phase("نسخ $title…")
        val f = File(tmp, "${prefix}_$stamp.$ext")
        val n = try { produce(f) } catch (e: SecurityException) {
            tracker.error("$title: لا توجد صلاحية. امنح الصلاحية من الإعدادات."); tracker.itemDone(); return
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { tracker.error("$title: ${e.message}"); tracker.itemDone(); return }
        tracker.current("$title ($n)")
        for (r in runs) {
            if (r.dead) continue
            if (uploadFile(r, base + folder, f.name, f)) {
                tracker.itemDone()
                runCatching {
                    val old = r.dest.list(base + folder).filter { !it.isDir && it.name.startsWith("${prefix}_") }
                        .sortedByDescending { it.name }.drop(settings.keepVersions.coerceAtLeast(1))
                    old.forEach { r.dest.delete(base + folder, it.name) }
                }
            }
        }
        f.delete()
    }

    // ---------------- التطبيقات ----------------
    private suspend fun backupApps(
        runs: List<DestRun>, base: List<String>, apps: List<Pair<AppItem, AppChoice>>,
        tmp: File, meta: MutableList<JSONObject>
    ) {
        val mode = if (apps.any { it.second.withData }) Privileged.detect() else PrivMode.NONE
        var warnedData = false
        for ((app, choice) in apps) {
            currentCoroutineContext().ensureActive()
            tracker.phase("نسخ التطبيقات", app.label)
            val dir = base + Layout.APPS + app.pkg
            var hasData = false
            val names = ArrayList<String>()
            for (apk in app.apkFiles) {
                names.add(apk.name)
                for (r in runs) {
                    if (r.dead) continue
                    val key = Layout.APPS + "/" + app.pkg + "/" + apk.name
                    val old = r.index[key]
                    if (old != null && old.size == apk.length() && old.modified == apk.lastModified()) { tracker.bytes(apk.length()); continue }
                    if (uploadFile(r, dir, apk.name, apk)) {
                        val e = FileIndexEntity(r.dest.id, key, apk.length(), apk.lastModified())
                        r.index[key] = e; r.pending.add(e)
                    }
                }
            }
            if (choice.withData) {
                if (!Privileged.canReadAppData(mode)) {
                    if (!warnedData) {
                        tracker.error("بيانات التطبيقات تحتاج Root أو Shizuku بصلاحية Root. تم نسخ ملفات التطبيق (APK) فقط.")
                        warnedData = true
                    }
                } else {
                    tracker.current("${app.label} — البيانات")
                    val tar = File(tmp, "data_${app.pkg}.tar.gz")
                    try {
                        Privileged.backupAppData(mode, app.pkg, tar)
                        tracker.addTotals(0, tar.length() * runs.count { !it.dead })
                        var any = false
                        for (r in runs) { if (!r.dead && uploadFile(r, dir, "data.tar.gz", tar)) any = true }
                        hasData = any
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { tracker.error("${app.label}: ${e.message}") }
                    finally { tar.delete() }
                }
            }
            val m = JSONObject().put("pkg", app.pkg).put("label", app.label).put("version", app.version)
                .put("hasData", hasData).put("files", JSONArray(names)).put("size", app.apkBytes)
                .put("time", System.currentTimeMillis())
                .put("icon", Sources.iconBase64(context, app.pkg))
            meta.add(m)
            tracker.itemDone()
            runs.forEach { flush(it) }
        }
    }

    // ---------------- حذف المتطابق ----------------
    private suspend fun mirrorDelete(runs: List<DestRun>, base: List<String>, seen: Set<String>, files: List<LocalFile>) {
        tracker.phase("إزالة الملفات المحذوفة من الهاتف…")
        val roots = settings.selection.paths.map { segs(it).joinToString("/") }
        for (r in runs) {
            if (r.dead) continue
            val gone = r.index.keys.filter { k ->
                k.startsWith(Layout.FILES + "/") &&
                    k.removePrefix(Layout.FILES + "/").let { rel -> rel !in seen && roots.any { rel == it || rel.startsWith("$it/") } }
            }
            for (k in gone) {
                currentCoroutineContext().ensureActive()
                val parts = k.removePrefix(Layout.FILES + "/").split('/')
                runCatching { r.dest.delete(base + Layout.FILES + parts.dropLast(1), parts.last()) }
            }
            gone.chunked(500).forEach { dao.removeIndex(r.dest.id, it) }
            gone.forEach { r.index.remove(it) }
        }
    }

    // ---------------- manifest ----------------
    private suspend fun writeManifest(r: DestRun, base: List<String>, newApps: List<JSONObject>, tmp: File) {
        runCatching {
            val merged = LinkedHashMap<String, JSONObject>()
            runCatching {
                r.dest.download(base, Layout.MANIFEST).use { ins ->
                    val old = JSONObject(ins.bufferedReader().readText()).optJSONArray("apps") ?: JSONArray()
                    for (i in 0 until old.length()) old.getJSONObject(i).let { merged[it.getString("pkg")] = it }
                }
            }
            newApps.forEach { n ->
                val prev = merged[n.getString("pkg")]
                if (prev != null && !n.optBoolean("hasData") && prev.optBoolean("hasData")) n.put("hasData", true)
                merged[n.getString("pkg")] = n
            }
            val o = JSONObject().put("app", "Star Cloud").put("format", 2)
                .put("device", settings.deviceName).put("time", System.currentTimeMillis())
                .put("apps", JSONArray().apply { merged.values.forEach { put(it) } })
            val f = File(tmp, Layout.MANIFEST).apply { writeText(o.toString()) }
            r.dest.upload(base, Layout.MANIFEST, f.length(), System.currentTimeMillis(), { f.inputStream() }) {}
        }
    }
}
