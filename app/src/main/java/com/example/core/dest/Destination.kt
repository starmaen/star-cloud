package com.example.core.dest

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.example.core.*
import java.io.InputStream

/**
 * وجهة النسخ الاحتياطي. كل الوجهات تعمل بمسارات نصية (مجلدات متداخلة)،
 * فيصبح المحرك مستقلاً تماماً عن نوع التخزين.
 */
interface Destination {
    val id: String
    val label: String

    /** يتحقق من أن الوجهة جاهزة (صلاحية / اتصال / تسجيل دخول). يرمي استثناءً برسالة مفهومة. */
    suspend fun prepare()

    suspend fun upload(
        dir: List<String>, name: String, length: Long, modified: Long,
        open: () -> InputStream, onBytes: (Long) -> Unit
    )

    suspend fun list(dir: List<String>): List<RemoteEntry>
    suspend fun download(dir: List<String>, name: String): InputStream
    suspend fun delete(dir: List<String>, name: String)
    suspend fun quota(): QuotaInfo?
}

object DestinationFactory {
    fun create(context: Context, c: DestConfig, google: GoogleAccount): Destination = when (c.kind) {
        DestKind.FOLDER -> {
            val uri = Uri.parse(c.target)
            val direct = if (hasAllFilesAccess(context)) treeToFile(uri) else null
            if (direct != null) FileDestination(c.id, c.label, direct)
            else SafDestination(context, c.id, c.label, uri)
        }
        DestKind.WEBDAV -> WebDavDestination(c.id, c.label, c.target, c.user, c.secret)
        DestKind.DRIVE_API -> DriveDestination(c.id, c.label, google)
    }

    fun hasAllFilesAccess(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** يحوّل مجلد التخزين الداخلي/البطاقة (externalstorage) إلى مسار ملف مباشر للسرعة. */
    fun treeToFile(tree: Uri): java.io.File? {
        if (tree.authority != "com.android.externalstorage.documents") return null
        val docId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val vol = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "")
        val base = if (vol == "primary") Environment.getExternalStorageDirectory() else java.io.File("/storage/$vol")
        if (!base.exists()) return null
        return if (rel.isEmpty()) base else java.io.File(base, rel)
    }
}
