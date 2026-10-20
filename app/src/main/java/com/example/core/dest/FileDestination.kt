package com.example.core.dest

import android.os.StatFs
import com.example.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** مجلد على التخزين الداخلي أو بطاقة SD بوصول مباشر (سريع ودقيق). */
class FileDestination(
    override val id: String,
    override val label: String,
    private val root: File
) : Destination {

    private fun dirOf(dir: List<String>): File = dir.fold(root) { f, s -> File(f, s) }

    override suspend fun prepare() = withContext(Dispatchers.IO) {
        if (!root.exists() && !root.mkdirs()) error("تعذّر إنشاء المجلد: ${root.path}")
        if (!root.canWrite()) error("لا توجد صلاحية كتابة على: ${root.path}")
    }

    override suspend fun upload(
        dir: List<String>, name: String, length: Long, modified: Long,
        open: () -> InputStream, onBytes: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val d = dirOf(dir); d.mkdirs()
        val tmp = File(d, "$name.part")
        open().use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); onBytes(n.toLong())
                }
            }
        }
        val dst = File(d, name)
        if (dst.exists()) dst.delete()
        if (!tmp.renameTo(dst)) { tmp.copyTo(dst, true); tmp.delete() }
        if (modified > 0) dst.setLastModified(modified)
    }

    override suspend fun list(dir: List<String>): List<RemoteEntry> = withContext(Dispatchers.IO) {
        dirOf(dir).listFiles().orEmpty()
            .filter { !it.name.endsWith(".part") }
            .map { RemoteEntry(it.name, it.isDirectory, if (it.isFile) it.length() else 0, it.lastModified()) }
    }

    override suspend fun download(dir: List<String>, name: String): InputStream =
        withContext(Dispatchers.IO) { File(dirOf(dir), name).inputStream() }

    override suspend fun delete(dir: List<String>, name: String) {
        withContext(Dispatchers.IO) { File(dirOf(dir), name).deleteRecursively() }
    }

    override suspend fun quota(): QuotaInfo? = withContext(Dispatchers.IO) {
        runCatching {
            var f: File? = root
            while (f != null && !f.exists()) f = f.parentFile
            val s = StatFs((f ?: root).path)
            val total = s.totalBytes; val free = s.availableBytes
            QuotaInfo(total, total - free, free)
        }.getOrNull()
    }
}
