package com.example.core.dest

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.example.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * أي مجلد يختاره المستخدم عبر منتقي النظام: Google Drive / OneDrive / Dropbox / Mega ...
 * (كل تطبيق سحابي مثبّت يظهر هناك). لا يحتاج أي مفاتيح أو إعداد.
 */
class SafDestination(
    private val context: Context,
    override val id: String,
    override val label: String,
    private val tree: Uri
) : Destination {

    private class Node(val docId: String, val isDir: Boolean, val size: Long, val modified: Long) {
        val dups = ArrayList<String>()
    }

    /** true لمزوّدي السحابة (Drive وغيره): يخزّنون الملف محلياً في هاتفك حتى يكتمل رفعه. */
    val isCloud: Boolean get() = tree.authority != "com.android.externalstorage.documents"

    private val resolver get() = context.contentResolver
    private val rootDocId: String = DocumentsContract.getTreeDocumentId(tree)
    private val childCache = HashMap<String, MutableMap<String, Node>>()
    private val dirIds = HashMap<String, String>() // "a/b" -> docId

    private fun docUri(docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)

    private fun children(parentId: String): MutableMap<String, Node> =
        childCache.getOrPut(parentId) { loadChildren(parentId) }

    /** مزوّدو السحابة (Drive) يعيدون القائمة ناقصة أثناء التحميل؛ ننتظر حتى تكتمل كي لا ننشئ مجلدات مكررة. */
    private fun loadChildren(parentId: String): MutableMap<String, Node> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        var attempt = 0
        while (true) {
            val map = LinkedHashMap<String, Node>()
            var loading = false
            resolver.query(
                uri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ), null, null, null
            )?.use { c ->
                loading = c.extras?.getBoolean(DocumentsContract.EXTRA_LOADING, false) == true
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    val id = c.getString(0)
                    val prev = map[name]
                    if (prev != null) { prev.dups.add(id); continue }
                    map[name] = Node(
                        id,
                        c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                        if (c.isNull(3)) 0 else c.getLong(3),
                        if (c.isNull(4)) 0 else c.getLong(4)
                    )
                }
            }
            if (!loading || attempt >= 40) return map
            attempt++
            Thread.sleep(750)
        }
    }

    private fun ensureDir(dir: List<String>, create: Boolean): String? {
        var parent = rootDocId
        val key = StringBuilder()
        for (seg in dir) {
            key.append('/').append(seg)
            val cached = dirIds[key.toString()]
            if (cached != null) { parent = cached; continue }
            var kids = children(parent)
            var existing = kids[seg]
            if (existing == null && create) {
                // إعادة قراءة حديثة قبل الإنشاء لتفادي التكرار
                kids = loadChildren(parent); childCache[parent] = kids; existing = kids[seg]
            }
            val id = if (existing != null && existing.isDir) existing.docId else {
                if (!create) return null
                val created = DocumentsContract.createDocument(
                    resolver, docUri(parent), DocumentsContract.Document.MIME_TYPE_DIR, seg
                ) ?: error("تعذّر إنشاء المجلد «$seg» في الوجهة")
                val newId = DocumentsContract.getDocumentId(created)
                kids[seg] = Node(newId, true, 0, 0)
                newId
            }
            dirIds[key.toString()] = id
            parent = id
        }
        return parent
    }

    override suspend fun prepare() = withContext(Dispatchers.IO) {
        val persisted = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (!persisted) error("انتهت صلاحية الوصول للمجلد. أعد اختيار المجلد من شاشة الوجهات.")
        // اختبار فعلي للكتابة بدل الافتراض
        val p = ensureDir(listOf("StarCloud"), true) ?: error("تعذّر الوصول للوجهة")
        children(p)
        Unit
    }

    override suspend fun upload(
        dir: List<String>, name: String, length: Long, modified: Long,
        open: () -> InputStream, onBytes: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val parent = ensureDir(dir, true)!!
        val kids = children(parent)
        kids[name]?.let {
            (listOf(it.docId) + it.dups).forEach { id ->
                runCatching { DocumentsContract.deleteDocument(resolver, docUri(id)) }
            }
            kids.remove(name)
        }
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val created = DocumentsContract.createDocument(resolver, docUri(parent), mime, name)
            ?: error("تعذّر إنشاء الملف «$name» في الوجهة")
        val os = runCatching { resolver.openOutputStream(created, "w") }.getOrNull()
            ?: runCatching { resolver.openOutputStream(created, "wt") }.getOrNull()
        os?.use { out ->
            open().use { input ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); onBytes(n.toLong())
                }
            }
        } ?: error("تعذّر الكتابة في «$name» (رفض المزوّد فتح الملف للكتابة)")
        kids[name] = Node(DocumentsContract.getDocumentId(created), false, length, modified)
        Unit
    }

    override suspend fun list(dir: List<String>): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val p = ensureDir(dir, false) ?: return@withContext emptyList()
        childCache.remove(p) // قراءة جديدة دائماً عند التصفح
        children(p).map { (n, v) -> RemoteEntry(n, v.isDir, v.size, v.modified) }
    }

    override suspend fun download(dir: List<String>, name: String): InputStream = withContext(Dispatchers.IO) {
        val p = ensureDir(dir, false) ?: error("المجلد غير موجود في الوجهة")
        val n = children(p)[name] ?: error("الملف غير موجود: $name")
        resolver.openInputStream(docUri(n.docId)) ?: error("تعذّر فتح $name")
    }

    override suspend fun delete(dir: List<String>, name: String) {
        withContext(Dispatchers.IO) {
            val p = ensureDir(dir, false) ?: return@withContext
            val n = children(p)[name] ?: return@withContext
            runCatching { DocumentsContract.deleteDocument(resolver, docUri(n.docId)) }
            children(p).remove(name)
        }
    }

    /** المساحة تُعرض فقط إن أعطاها المزوّد فعلاً — لا أرقام تقديرية. */
    override suspend fun quota(): QuotaInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val roots = DocumentsContract.buildRootsUri(tree.authority)
            var best: QuotaInfo? = null
            val volId = rootDocId.substringBefore(':')
            resolver.query(
                roots,
                arrayOf(
                    DocumentsContract.Root.COLUMN_ROOT_ID,
                    DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Root.COLUMN_CAPACITY_BYTES,
                    DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
                ), null, null, null
            )?.use { c ->
                val rows = ArrayList<Triple<String, Long?, Long?>>()
                while (c.moveToNext()) {
                    val rid = c.getString(0) ?: ""
                    val did = c.getString(1) ?: ""
                    val cap = if (c.isNull(2)) null else c.getLong(2)
                    val av = if (c.isNull(3)) null else c.getLong(3)
                    rows.add(Triple("$rid|$did", cap, av))
                }
                val match = rows.firstOrNull { it.first.startsWith("$volId|") || it.first.endsWith("|$rootDocId") }
                    ?: rows.singleOrNull()
                if (match != null && (match.second != null || match.third != null)) {
                    val total = match.second; val free = match.third
                    best = QuotaInfo(total, if (total != null && free != null) total - free else null, free)
                }
            }
            best
        }.getOrNull()
    }
}
