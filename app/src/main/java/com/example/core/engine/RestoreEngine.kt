package com.example.core.engine

import android.content.Context
import android.os.Environment
import com.example.core.*
import com.example.core.data.*
import com.example.core.dest.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class AppMeta(val pkg: String, val label: String, val version: String, val hasData: Boolean, val size: Long, val files: List<String>)

/** عقدة داخل مجلد Files في النسخة: مسارها نسبي (مقاطع). */
data class RemoteNode(val segs: List<String>, val isDir: Boolean)

data class RestorePlan(
    val destId: String,
    val device: String,
    val apps: List<Pair<String, Boolean>> = emptyList(), // pkg -> restore data
    val nodes: List<RemoteNode> = emptyList(),
    val contactsFile: String? = null,
    val smsFile: String? = null,
    val callsFile: String? = null,
    val toOriginal: Boolean = true,
    val overwrite: Boolean = true
)

object RestoreHolder {
    @Volatile var plan: RestorePlan? = null
    @Volatile var pendingVcf: File? = null
}

/** يقرأ محتوى النسخة من الوجهة ليختار المستخدم ما يستعيده. */
class Catalog(private val dest: Destination, private val device: String) {
    private val base = Layout.base(device)

    suspend fun apps(): List<AppMeta> = runCatching {
        dest.download(base, Layout.MANIFEST).use { ins ->
            val arr = JSONObject(ins.bufferedReader().readText()).optJSONArray("apps") ?: return emptyList()
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                AppMeta(o.getString("pkg"), o.optString("label", o.getString("pkg")), o.optString("version"),
                    o.optBoolean("hasData"), o.optLong("size"),
                    o.optJSONArray("files")?.let { a -> (0 until a.length()).map { i -> a.getString(i) } } ?: emptyList())
            }.sortedBy { it.label.lowercase() }
        }
    }.getOrDefault(emptyList())

    suspend fun files(segs: List<String>): List<RemoteEntry> =
        dest.list(base + Layout.FILES + segs).sortedWith(compareByDescending<RemoteEntry> { it.isDir }.thenBy { it.name.lowercase() })

    suspend fun versions(folder: String, prefix: String): List<RemoteEntry> =
        dest.list(base + folder).filter { !it.isDir && it.name.startsWith("${prefix}_") }.sortedByDescending { it.name }
}

object RestoreCatalog {
    suspend fun devices(dest: Destination): List<String> =
        dest.list(listOf(Layout.ROOT)).filter { it.isDir }.map { it.name }
}

class RestoreEngine(
    private val context: Context,
    private val settings: Settings,
    private val db: StarDb,
    private val google: GoogleAccount,
    private val tracker: Tracker
) {
    suspend fun run(plan: RestorePlan): Boolean = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        tracker.begin(true, "تجهيز الاستعادة…")
        var ok: Boolean
        var summary: String
        try {
            val cfg = settings.destinations.firstOrNull { it.id == plan.destId } ?: error("الوجهة غير موجودة")
            val dest = DestinationFactory.create(context, cfg, google)
            dest.prepare()
            val base = Layout.base(plan.device)

            if (plan.contactsFile != null) restoreContacts(dest, base, plan.contactsFile)
            if (plan.smsFile != null) restoreSms(dest, base, plan.smsFile)
            if (plan.callsFile != null) restoreCalls(dest, base, plan.callsFile)
            if (plan.apps.isNotEmpty()) restoreApps(dest, base, plan.apps)
            for (n in plan.nodes) {
                currentCoroutineContext().ensureActive()
                if (n.isDir) restoreDir(dest, base, n.segs, plan) else restoreFile(dest, base, n.segs.dropLast(1), n.segs.last(), 0, 0, plan)
            }
            val errs = tracker.errorTotal
            summary = "تمت استعادة ${tracker.doneItems - tracker.skippedCount} عنصراً" +
                (if (tracker.skippedCount > 0) " • ${tracker.skippedCount} موجود مسبقاً" else "") +
                (if (errs > 0) " • $errs أخطاء" else "")
            ok = errs == 0
        } catch (e: CancellationException) {
            tracker.finish("أُوقفت الاستعادة يدوياً"); throw e
        } catch (e: Exception) {
            summary = e.message ?: "فشلت الاستعادة"; tracker.error(summary); ok = false
        }
        tracker.finish(summary)
        runCatching {
            db.dao().addLog(RunLogEntity(time = System.currentTimeMillis(), kind = "restore", ok = ok, items = tracker.doneItems,
                bytes = tracker.doneBytes, seconds = (System.currentTimeMillis() - started) / 1000, summary = summary))
        }
        ok
    }

    private fun target(segs: List<String>, name: String, plan: RestorePlan): File {
        val rel = (segs + name).joinToString("/")
        return if (plan.toOriginal) File("/$rel")
        else File(Environment.getExternalStorageDirectory(), "StarCloud-Restore/$rel")
    }

    private suspend fun restoreDir(dest: Destination, base: List<String>, segs: List<String>, plan: RestorePlan) {
        currentCoroutineContext().ensureActive()
        val entries = dest.list(base + Layout.FILES + segs)
        for (e in entries) {
            currentCoroutineContext().ensureActive()
            if (e.isDir) restoreDir(dest, base, segs + e.name, plan)
            else restoreFile(dest, base, segs, e.name, e.size, e.modified, plan)
        }
    }

    private suspend fun restoreFile(dest: Destination, base: List<String>, segs: List<String>, name: String, size: Long, mod: Long, plan: RestorePlan) {
        tracker.current(name)
        val out = target(segs, name, plan)
        try {
            if (out.exists() && (!plan.overwrite || (size > 0 && out.length() == size && (mod <= 0 || kotlin.math.abs(out.lastModified() - mod) < 3000)))) {
                tracker.itemSkipped(size); return
            }
            out.parentFile?.mkdirs()
            val part = File(out.parentFile, "${out.name}.part")
            dest.download(base + Layout.FILES + segs, name).use { ins ->
                part.outputStream().use { os ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = ins.read(buf); if (n < 0) break
                        os.write(buf, 0, n); tracker.bytes(n.toLong())
                    }
                }
            }
            if (out.exists()) out.delete()
            if (!part.renameTo(out)) { part.copyTo(out, true); part.delete() }
            if (mod > 0) out.setLastModified(mod)
            tracker.itemDone()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { tracker.error("«$name»: ${e.message ?: "فشلت الاستعادة"}") }
    }

    private suspend fun restoreContacts(dest: Destination, base: List<String>, file: String) {
        tracker.phase("استعادة جهات الاتصال…", file)
        try {
            val out = File(context.cacheDir, "restored_contacts.vcf")
            dest.download(base + Layout.CONTACTS, file).use { i -> out.outputStream().use { i.copyTo(it) } }
            RestoreHolder.pendingVcf = out
            tracker.itemDone()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { tracker.error("جهات الاتصال: ${e.message}") }
    }

    private suspend fun restoreSms(dest: Destination, base: List<String>, file: String) {
        tracker.phase("استعادة الرسائل…", file)
        try {
            if (!Sources.isDefaultSmsApp(context)) error("يجب جعل Star Cloud تطبيق الرسائل الافتراضي مؤقتاً أثناء الاستعادة")
            val n = dest.download(base + Layout.MESSAGES, file).use { Sources.importSms(context, it) }
            tracker.current("$n رسالة جديدة"); tracker.itemDone()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { tracker.error("الرسائل: ${e.message}") }
    }

    private suspend fun restoreCalls(dest: Destination, base: List<String>, file: String) {
        tracker.phase("استعادة سجل المكالمات…", file)
        try {
            val n = dest.download(base + Layout.MESSAGES, file).use { Sources.importCalls(context, it) }
            tracker.current("$n مكالمة جديدة"); tracker.itemDone()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { tracker.error("سجل المكالمات: ${e.message}") }
    }

    private suspend fun restoreApps(dest: Destination, base: List<String>, apps: List<Pair<String, Boolean>>) {
        val mode = Privileged.detect()
        for ((pkg, withData) in apps) {
            currentCoroutineContext().ensureActive()
            tracker.phase("استعادة التطبيقات", pkg)
            val dir = base + Layout.APPS + pkg
            val work = File(context.cacheDir, "restore_$pkg").apply { deleteRecursively(); mkdirs() }
            try {
                if (!Sources.isInstalled(context, pkg)) {
                    val apks = dest.list(dir).filter { it.name.endsWith(".apk") }
                    if (apks.isEmpty()) error("ملفات التطبيق غير موجودة في النسخة")
                    val files = apks.map { a ->
                        File(work, a.name).also { f ->
                            dest.download(dir, a.name).use { i -> f.outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
                        }
                    }
                    if (Privileged.canInstallSilently(mode)) Privileged.installApks(mode, files)
                    else ApkInstaller.install(context, files)?.let { error(it) }
                }
                if (withData) {
                    if (!Privileged.canReadAppData(mode)) error("استعادة البيانات تحتاج Root أو Shizuku بصلاحية Root")
                    if (!Sources.isInstalled(context, pkg)) error("التطبيق غير مثبّت، لا يمكن استعادة بياناته")
                    dest.download(dir, "data.tar.gz").use { Privileged.restoreAppData(mode, pkg, it) }
                }
                tracker.itemDone()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { tracker.error("$pkg: ${e.message}") }
            finally { work.deleteRecursively() }
        }
    }
}
