package com.example.service

import android.content.Context
import android.content.pm.PackageManager
import com.example.data.model.InstalledAppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

enum class PrivilegeMode { NONE, ROOT, SHIZUKU }

/**
 * Full app-data backup using elevated privileges.
 *
 * - Root: runs `su` and archives /data/user/0/<package>.
 * - Shizuku: runs the same archive command through the Shizuku service.
 *
 * Note: Shizuku started through ADB runs as the "shell" user and Android's
 * sandbox may still deny reading other apps' private directories. In that
 * case the backup fails honestly instead of returning fake data. Shizuku
 * started with root, or a real root shell, can read the data.
 */
class PrivilegedBackupHelper(private val context: Context) {

    fun hasRoot(): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            return@runCatching false
        }
        output.contains("uid=0")
    }.getOrDefault(false)

    fun isShizukuRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasShizukuPermission(): Boolean = runCatching {
        isShizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestShizukuPermission() {
        runCatching {
            if (isShizukuRunning() && !hasShizukuPermission()) {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
        }
    }

    fun mode(): PrivilegeMode = when {
        hasRoot() -> PrivilegeMode.ROOT
        hasShizukuPermission() -> PrivilegeMode.SHIZUKU
        else -> PrivilegeMode.NONE
    }

    fun statusText(): String = when {
        hasRoot() -> "Root متاح — يمكن نسخ بيانات التطبيقات كاملة"
        hasShizukuPermission() -> "Shizuku متاح — يعتمد على صلاحية الوصول"
        isShizukuRunning() -> "Shizuku مثبّت — لم تُمنح الصلاحية بعد"
        else -> "لا يوجد Root ولا Shizuku"
    }

    suspend fun backupAppData(app: InstalledAppItem, outFile: File): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pkg = app.packageName
                if (!pkg.matches(Regex("[a-zA-Z0-9_.]+"))) {
                    throw SecurityException("اسم حزمة غير صالح")
                }
                val stream = when {
                    hasRoot() -> runShell(arrayOf("su", "-c", TAR_COMMAND.format(pkg)))
                    hasShizukuPermission() -> runShizuku(arrayOf("sh", "-c", TAR_COMMAND.format(pkg)))
                    else -> throw SecurityException("النسخ الكامل يحتاج Root أو صلاحية Shizuku")
                }
                outFile.parentFile?.mkdirs()
                stream.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (!outFile.exists() || outFile.length() == 0L) {
                    throw SecurityException(
                        "تعذّر قراءة بيانات $pkg. غالباً Shizuku يعمل بصلاحية ADB ولا يستطيع الوصول إلى /data/user/0"
                    )
                }
                outFile.length()
            }
        }

    private fun runShell(command: Array<String>): InputStream {
        val process = ProcessBuilder(*command).redirectErrorStream(false).start()
        return process.inputStream
    }

    private fun runShizuku(command: Array<String>): InputStream {
        val process = shizukuNewProcess(command)
            ?: throw IllegalStateException(
                "تعذّر تشغيل الأمر عبر Shizuku. تأكد من منح الصلاحية وأن خدمة Shizuku تعمل."
            )
        return process.inputStream
    }

    private fun shizukuNewProcess(command: Array<String>): Process? = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        method.invoke(null, command, null, null) as? Process
    }.getOrNull()

    companion object {
        const val SHIZUKU_REQUEST_CODE = 4210
        private const val TAR_COMMAND = "tar -czf - -C /data/user/0 '%s' 2>/dev/null"
    }
}
