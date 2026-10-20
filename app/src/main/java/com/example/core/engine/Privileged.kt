package com.example.core.engine

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

enum class PrivMode { NONE, ROOT, SHIZUKU_SHELL, SHIZUKU_ROOT }

/**
 * تنفيذ أوامر بصلاحيات مرتفعة: Root (su) أو Shizuku.
 * مهم: Shizuku بوضع ADB يعمل بهوية shell ولا يقرأ بيانات التطبيقات الأخرى؛ يقرؤها فقط إن بُدئ بـ Root.
 */
object Privileged {
    @Volatile private var cached: PrivMode? = null
    @Volatile private var cachedAt = 0L

    fun shizukuRunning() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun shizukuGranted() = runCatching {
        shizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
    fun requestShizuku() { runCatching { if (shizukuRunning() && !shizukuGranted()) Shizuku.requestPermission(4210) } }

    private fun suProcess(cmd: String): Process = ProcessBuilder("su", "-c", cmd).start()

    @Suppress("UNCHECKED_CAST")
    private fun shizukuProcess(cmd: String): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
    }

    fun process(mode: PrivMode, cmd: String): Process = when (mode) {
        PrivMode.ROOT -> suProcess(cmd)
        PrivMode.SHIZUKU_ROOT, PrivMode.SHIZUKU_SHELL -> shizukuProcess(cmd)
        PrivMode.NONE -> error("لا توجد صلاحيات Root أو Shizuku")
    }

    private fun idOf(p: Process): String? = runCatching {
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(6, TimeUnit.SECONDS)) { p.destroy(); null } else out
    }.getOrNull()

    /** يحدد أقوى وضع متاح. يُخزَّن النتيجة 30 ثانية. */
    suspend fun detect(force: Boolean = false): PrivMode = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && cached != null && now - cachedAt < 30_000) return@withContext cached!!
        val mode = when {
            runCatching { idOf(suProcess("id"))?.contains("uid=0") == true }.getOrDefault(false) -> PrivMode.ROOT
            shizukuGranted() -> {
                val out = runCatching { idOf(shizukuProcess("id")) }.getOrNull()
                if (out?.contains("uid=0") == true) PrivMode.SHIZUKU_ROOT else PrivMode.SHIZUKU_SHELL
            }
            else -> PrivMode.NONE
        }
        cached = mode; cachedAt = now
        mode
    }

    fun canReadAppData(m: PrivMode) = m == PrivMode.ROOT || m == PrivMode.SHIZUKU_ROOT
    fun canInstallSilently(m: PrivMode) = m != PrivMode.NONE

    fun describe(m: PrivMode, shizukuAlive: Boolean): String = when (m) {
        PrivMode.ROOT -> "Root مفعّل — يمكن نسخ واستعادة بيانات التطبيقات"
        PrivMode.SHIZUKU_ROOT -> "Shizuku (بصلاحية Root) — يمكن نسخ واستعادة بيانات التطبيقات"
        PrivMode.SHIZUKU_SHELL -> "Shizuku (وضع ADB) — يثبّت التطبيقات بصمت، لكنه لا يستطيع قراءة بيانات التطبيقات"
        PrivMode.NONE -> if (shizukuAlive) "Shizuku يعمل لكن لم تُمنح له الصلاحية بعد" else "غير متاح — نسخ التطبيقات (APK) فقط"
    }

    /** نسخ بيانات تطبيق إلى ملف tar.gz. يعيد حجم الملف. */
    suspend fun backupAppData(mode: PrivMode, pkg: String, out: File): Long = withContext(Dispatchers.IO) {
        require(pkg.matches(Regex("[A-Za-z0-9_.]+"))) { "اسم حزمة غير صالح" }
        val cmd = "tar -cf - --exclude=$pkg/cache --exclude=$pkg/code_cache -C /data/data $pkg"
        val p = process(mode, cmd)
        val err = StringBuilder()
        val t = Thread { runCatching { err.append(p.errorStream.bufferedReader().readText().take(400)) } }.also { it.start() }
        p.inputStream.use { input ->
            java.util.zip.GZIPOutputStream(out.outputStream().buffered(256 * 1024)).use { gz -> input.copyTo(gz, 256 * 1024) }
        }
        val code = p.waitFor(); t.join(1000)
        if (code > 1 || out.length() < 100) {
            out.delete()
            error("تعذّر نسخ بيانات $pkg: ${err.toString().ifBlank { "رمز $code" }}")
        }
        out.length()
    }

    /** استعادة بيانات تطبيق من tar.gz مع ضبط المالك وسياق SELinux. التطبيق يجب أن يكون مثبتاً. */
    suspend fun restoreAppData(mode: PrivMode, pkg: String, archive: InputStream) = withContext(Dispatchers.IO) {
        require(pkg.matches(Regex("[A-Za-z0-9_.]+"))) { "اسم حزمة غير صالح" }
        val script = "am force-stop $pkg; " +
            "U=\$(stat -c %u /data/data/$pkg) || exit 3; " +
            "tar -xf - -C /data/data && chown -R \$U:\$U /data/data/$pkg && restorecon -RF /data/data/$pkg"
        val p = process(mode, script)
        val err = StringBuilder()
        val t = Thread { runCatching { err.append(p.errorStream.bufferedReader().readText().take(400)) } }.also { it.start() }
        p.outputStream.use { os: OutputStream ->
            java.util.zip.GZIPInputStream(archive.buffered(256 * 1024)).use { it.copyTo(os, 256 * 1024) }
        }
        val code = p.waitFor(); t.join(1000)
        if (code != 0) error("تعذّرت استعادة بيانات $pkg: ${err.toString().ifBlank { "رمز $code" }}")
    }

    /** تثبيت APK واحد أو عدة ملفات (split) بصمت. */
    suspend fun installApks(mode: PrivMode, files: List<File>) = withContext(Dispatchers.IO) {
        val dir = "/data/local/tmp/sc_install_${System.nanoTime()}"
        val mk = process(mode, "mkdir -p $dir && chmod 777 $dir"); mk.waitFor()
        val remote = files.map { "$dir/${it.name}" }
        for ((i, f) in files.withIndex()) {
            val p = process(mode, "cat > '${remote[i]}' && chmod 644 '${remote[i]}'")
            p.outputStream.use { o -> f.inputStream().use { it.copyTo(o, 256 * 1024) } }
            p.waitFor()
        }
        val cmd = if (remote.size == 1) "pm install -r -d '${remote[0]}'"
        else "pm install-multiple -r -d " + remote.joinToString(" ") { "'$it'" }
        val p = process(mode, "$cmd 2>&1; rm -rf $dir")
        val out = p.inputStream.bufferedReader().readText(); p.waitFor()
        if (!out.contains("Success")) error("فشل التثبيت: ${out.trim().take(200)}")
    }
}
