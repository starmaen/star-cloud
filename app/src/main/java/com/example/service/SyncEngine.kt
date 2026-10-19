package com.example.service

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.cloud.CloudStorageProvider
import com.example.data.local.FolderConfigEntity
import com.example.data.local.StarCloudDatabase
import com.example.data.local.SyncLogEntity
import com.example.data.local.SyncPreferences
import com.example.data.model.ConflictPolicy
import com.example.data.model.ContactItem
import com.example.data.model.InstalledAppItem
import com.example.data.model.SyncCategory
import com.example.data.model.SyncDirection
import com.example.data.model.SyncProgressState
import com.example.data.model.WhatsAppMediaCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import org.json.JSONArray
import org.json.JSONObject

class SyncEngine(
    private val context: Context,
    private val database: StarCloudDatabase,
    private val preferences: SyncPreferences,
    private val contactsHelper: ContactsBackupHelper,
    private val smsHelper: SmsCallLogBackupHelper,
    private val waHelper: WhatsAppBackupHelper,
    private val appHelper: AppBackupHelper
) {
    private val _progressState = MutableStateFlow(SyncProgressState())
    val progressState: StateFlow<SyncProgressState> = _progressState.asStateFlow()

    private var isCancelled = false

    fun cancelSync() {
        isCancelled = true
        _progressState.value = _progressState.value.copy(
            isSyncing = false,
            stage = "تم إيقاف المزامنة بواسطة المستخدم"
        )
    }

    suspend fun executeSync(
        provider: CloudStorageProvider,
        direction: SyncDirection = preferences.getSyncDirection(),
        folderConfigs: List<FolderConfigEntity>,
        selectedContacts: List<ContactItem>,
        selectedWhatsAppCategories: List<WhatsAppMediaCategory>,
        selectedApps: List<InstalledAppItem>
    ): Boolean = withContext(Dispatchers.IO) {
        if (_progressState.value.isSyncing) return@withContext false

        isCancelled = false
        val startTime = System.currentTimeMillis()
        var totalProcessed = 0
        var totalBytesTransferred = 0L
        var hasError = false
        var errorReason: String? = null

        _progressState.value = SyncProgressState(
            isSyncing = true,
            stage = "بدء تهيئة المزامنة والاتصال بالسحابة...",
            currentProgress = 0.05f
        )

        try {
            if (!provider.isConnected) {
                provider.connect(preferences.getConnectedAccount())
            }

            val maxFileSizeMb = preferences.getMaxFileSizeMb()
            val maxSizeBytes = if (maxFileSizeMb > 0) maxFileSizeMb.toLong() * 1024 * 1024 else Long.MAX_VALUE

            // 1. Folders
            val activeFolders = folderConfigs.filter { it.isEnabled }
            for ((idx, folder) in activeFolders.withIndex()) {
                if (isCancelled) break
                _progressState.value = _progressState.value.copy(
                    currentCategory = SyncCategory.FOLDERS,
                    currentItemName = folder.name,
                    stage = "مزامنة المجلد: ${folder.name} (${idx + 1}/${activeFolders.size})"
                )

                val folderOutcome = runCatching {
                    val treeUri = Uri.parse(folder.treeUriString)
                    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

                    context.contentResolver.query(
                        childrenUri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                        while (cursor.moveToNext() && !isCancelled) {
                            val docId = cursor.getString(idIdx) ?: continue
                            val name = cursor.getString(nameIdx) ?: "file"
                            val mime = cursor.getString(mimeIdx) ?: "application/octet-stream"
                            val size = cursor.getLong(sizeIdx)

                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue

                            if (size > maxSizeBytes) continue
                            val ext = name.substringAfterLast('.', "")
                            if (ext in listOf("tmp", "cache", "log", "bak")) continue

                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                                val res = provider.uploadFile(
                                    remotePath = "Folders/${folder.name}",
                                    fileName = name,
                                    fileSize = size,
                                    inputStream = stream,
                                    mimeType = mime,
                                    onProgress = { uploaded, total ->
                                        _progressState.value = _progressState.value.copy(
                                            currentItemName = name,
                                            currentProgress = if (total > 0) (uploaded.toFloat() / total) else 0.5f,
                                            bytesTransferred = totalBytesTransferred + uploaded
                                        )
                                    }
                                )
                                if (res.isSuccess) {
                                    totalProcessed++
                                    totalBytesTransferred += res.getOrNull()?.sizeBytes ?: size
                                } else {
                                    hasError = true
                                    errorReason = res.exceptionOrNull()?.message ?: "فشل رفع الملف $name"
                                }
                            } ?: run {
                                hasError = true
                                errorReason = "تعذر قراءة الملف: $name"
                            }
                        }
                    }
                }
                folderOutcome.exceptionOrNull()?.let {
                    hasError = true
                    errorReason = it.message ?: "تعذر مزامنة المجلد ${folder.name}"
                }
            }

            // 2. Contacts
            if (preferences.syncContacts.value && selectedContacts.isNotEmpty() && !isCancelled) {
                _progressState.value = _progressState.value.copy(
                    currentCategory = SyncCategory.CONTACTS,
                    currentItemName = "contacts_backup.vcf",
                    stage = "تصدير ومزامنة جهات الاتصال (${selectedContacts.size} جهة)...",
                    currentProgress = 0.35f
                )

                val vcard = contactsHelper.exportToVCard(selectedContacts)
                val bytes = vcard.toByteArray(Charsets.UTF_8)
                val res = provider.uploadFile(
                    remotePath = "Contacts",
                    fileName = "contacts_backup.vcf",
                    fileSize = bytes.size.toLong(),
                    inputStream = ByteArrayInputStream(bytes),
                    mimeType = "text/x-vcard",
                    onProgress = { _, _ -> }
                )
                if (res.isSuccess) {
                    totalProcessed += selectedContacts.size
                    totalBytesTransferred += res.getOrNull()?.sizeBytes ?: bytes.size
                } else {
                    hasError = true
                    errorReason = res.exceptionOrNull()?.message ?: "فشل رفع جهات الاتصال"
                }
            }

            // 3. Messages & Calls
            if (preferences.syncMessages.value && !isCancelled) {
                _progressState.value = _progressState.value.copy(
                    currentCategory = SyncCategory.MESSAGES,
                    currentItemName = "sms_backup.json",
                    stage = "نسخ الرسائل النصية وسجل المكالمات...",
                    currentProgress = 0.55f
                )

                val smsList = smsHelper.getSmsMessages()
                val smsJson = smsHelper.exportSmsToJson(smsList)
                val smsBytes = smsJson.toByteArray(Charsets.UTF_8)
                val smsRes = provider.uploadFile(
                    remotePath = "Messages",
                    fileName = "sms_backup.json",
                    fileSize = smsBytes.size.toLong(),
                    inputStream = ByteArrayInputStream(smsBytes),
                    mimeType = "application/json",
                    onProgress = { _, _ -> }
                )
                if (smsRes.isSuccess) {
                    totalProcessed += smsList.size
                    totalBytesTransferred += smsRes.getOrNull()?.sizeBytes ?: smsBytes.size
                } else {
                    hasError = true
                    errorReason = smsRes.exceptionOrNull()?.message ?: "فشل رفع الرسائل"
                }

                val callList = smsHelper.getCallLogs()
                val callJson = smsHelper.exportCallLogsToJson(callList)
                val callBytes = callJson.toByteArray(Charsets.UTF_8)
                val callRes = provider.uploadFile(
                    remotePath = "Messages",
                    fileName = "call_logs.json",
                    fileSize = callBytes.size.toLong(),
                    inputStream = ByteArrayInputStream(callBytes),
                    mimeType = "application/json",
                    onProgress = { _, _ -> }
                )
                if (callRes.isSuccess) {
                    totalProcessed += callList.size
                    totalBytesTransferred += callRes.getOrNull()?.sizeBytes ?: callBytes.size
                } else {
                    hasError = true
                    errorReason = callRes.exceptionOrNull()?.message ?: "فشل رفع سجل المكالمات"
                }
            }

            // 4. WhatsApp Media
            if (preferences.syncWhatsApp.value && !isCancelled) {
                val selectedWa = selectedWhatsAppCategories.filter { it.isSelected }
                if (selectedWa.isNotEmpty()) {
                    val manifest = JSONObject().apply {
                        put("generatedAt", System.currentTimeMillis())
                        put("note", "ملف تعريف بالوسائط. يتم رفع الملفات الفعلية عندما يسمح النظام بقراءة مجلد واتساب؛ وإلا أضف مجلد WhatsApp من قسم المزامنة الانتقائية.")
                        put("categories", JSONArray().apply {
                            selectedWa.forEach { cat ->
                                put(JSONObject().apply {
                                    put("id", cat.id)
                                    put("name", cat.nameAr)
                                    put("path", cat.folderPath)
                                    put("estimatedCount", cat.estimatedCount)
                                })
                            }
                        })
                    }.toString().toByteArray(Charsets.UTF_8)

                    val manifestRes = provider.uploadFile(
                        remotePath = "WhatsApp",
                        fileName = "whatsapp_manifest.json",
                        fileSize = manifest.size.toLong(),
                        inputStream = ByteArrayInputStream(manifest),
                        mimeType = "application/json",
                        onProgress = { _, _ -> }
                    )
                    if (manifestRes.isFailure) {
                        hasError = true
                        errorReason = manifestRes.exceptionOrNull()?.message ?: "فشل رفع تعريف وسائط واتساب"
                    }

                    for (cat in selectedWa) {
                        if (isCancelled) break
                        _progressState.value = _progressState.value.copy(
                            currentCategory = SyncCategory.WHATSAPP,
                            currentItemName = cat.nameAr,
                            stage = "مزامنة وسائط واتساب: ${cat.nameAr}...",
                            currentProgress = 0.70f
                        )
                        val dir = File(cat.folderPath)
                        val files = runCatching {
                            if (dir.isDirectory && dir.canRead()) {
                                dir.listFiles()?.filter { it.isFile }.orEmpty()
                            } else {
                                emptyList()
                            }
                        }.getOrDefault(emptyList())

                        for (file in files) {
                            if (isCancelled) break
                            if (file.length() > maxSizeBytes) continue
                            val ext = file.extension.lowercase()
                            val mime = when (ext) {
                                "jpg", "jpeg", "png", "webp" -> "image/*"
                                "mp4", "3gp", "mkv" -> "video/*"
                                "mp3", "opus", "m4a", "aac", "ogg" -> "audio/*"
                                "pdf" -> "application/pdf"
                                else -> "application/octet-stream"
                            }
                            runCatching {
                                file.inputStream().use { stream ->
                                    provider.uploadFile(
                                        remotePath = "WhatsApp/${cat.nameEn}",
                                        fileName = file.name,
                                        fileSize = file.length(),
                                        inputStream = stream,
                                        mimeType = mime,
                                        onProgress = { uploaded, total ->
                                            _progressState.value = _progressState.value.copy(
                                                currentItemName = file.name,
                                                currentProgress = if (total > 0) uploaded.toFloat() / total else 0.5f,
                                                bytesTransferred = totalBytesTransferred + uploaded
                                            )
                                        }
                                    )
                                }
                            }.onSuccess { res ->
                                if (res.isSuccess) {
                                    totalProcessed++
                                    totalBytesTransferred += res.getOrNull()?.sizeBytes ?: file.length()
                                } else {
                                    hasError = true
                                    errorReason = res.exceptionOrNull()?.message ?: "فشل رفع ${file.name}"
                                }
                            }.onFailure {
                                hasError = true
                                errorReason = it.message ?: "تعذر قراءة ${file.name}"
                            }
                        }
                    }
                }
            }

            // 5. Installed APKs
            if (preferences.syncApps.value && !isCancelled) {
                val chosenApps = selectedApps.filter { it.isSelected }
                for (app in chosenApps) {
                    if (isCancelled) break
                    _progressState.value = _progressState.value.copy(
                        currentCategory = SyncCategory.INSTALLED_APPS,
                        currentItemName = "${app.appName}.apk",
                        stage = "استخراج ومزامنة حزمة التطبيق: ${app.appName}...",
                        currentProgress = 0.85f
                    )

                    val apkFile = appHelper.extractApk(app)
                    if (apkFile != null && apkFile.exists()) {
                        val apkRes = FileInputStream(apkFile).use { inStream ->
                            provider.uploadFile(
                                remotePath = "Applications",
                                fileName = apkFile.name,
                                fileSize = apkFile.length(),
                                inputStream = inStream,
                                mimeType = "application/vnd.android.package-archive",
                                onProgress = { u, _ ->
                                    _progressState.value = _progressState.value.copy(
                                        bytesTransferred = totalBytesTransferred + u
                                    )
                                }
                            )
                        }
                        if (apkRes.isSuccess) {
                            totalProcessed++
                            totalBytesTransferred += apkRes.getOrNull()?.sizeBytes ?: apkFile.length()
                        } else {
                            hasError = true
                            errorReason = apkRes.exceptionOrNull()?.message ?: "فشل رفع ${app.appName}"
                        }
                    }
                }
            }

            _progressState.value = _progressState.value.copy(
                currentProgress = 1.0f,
                stage = "تمت المزامنة بنجاح وحفظ السجلات."
            )

        } catch (e: Exception) {
            if (e is CancellationException) {
                errorReason = "تم الإلغاء"
            } else {
                hasError = true
                errorReason = e.message
            }
        } finally {
            val durationSec = ((System.currentTimeMillis() - startTime) / 1000).coerceAtLeast(1)
            val status = if (hasError) "FAILED" else if (isCancelled) "CANCELLED" else "SUCCESS"

            val summary = if (status == "SUCCESS") {
                "تمت المزامنة بنجاح إلى ${provider.providerType.displayName}: $totalProcessed عنصر بحجم ${formatBytes(totalBytesTransferred)}"
            } else {
                "فشلت المزامنة: ${errorReason ?: "خطأ غير معروف"}"
            }

            database.syncDao().insertLog(
                SyncLogEntity(
                    timestamp = System.currentTimeMillis(),
                    direction = direction.name,
                    status = status,
                    itemsSynced = totalProcessed,
                    totalSizeBytes = totalBytesTransferred,
                    durationSeconds = durationSec,
                    summary = summary,
                    providerName = provider.providerType.displayName
                )
            )

            _progressState.value = _progressState.value.copy(
                isSyncing = false,
                itemsProcessed = totalProcessed,
                bytesTransferred = totalBytesTransferred,
                stage = summary
            )
        }

        return@withContext !hasError && !isCancelled
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }
}
