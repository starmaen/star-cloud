package com.example.service

import android.content.Context
import android.os.Environment
import com.example.data.model.WhatsAppMediaCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhatsAppBackupHelper(private val context: Context) {

    suspend fun getWhatsAppMediaCategories(): List<WhatsAppMediaCategory> = withContext(Dispatchers.IO) {
        val categories = mutableListOf<WhatsAppMediaCategory>()

        val rawCategories = listOf(
            Triple("wa_images", "صور واتساب (WhatsApp Images)", "WhatsApp Images"),
            Triple("wa_videos", "مقاطع الفيديو (WhatsApp Video)", "WhatsApp Video"),
            Triple("wa_audio", "الرسائل الصوتية والتسجيلات (Voice Notes & Audio)", "WhatsApp Audio"),
            Triple("wa_docs", "المستندات والملفات (WhatsApp Documents)", "WhatsApp Documents"),
            Triple("wa_databases", "ملفات النسخ الاحتياطي المشفرة (Databases crypt14)", "Databases")
        )

        for ((id, nameAr, subFolder) in rawCategories) {
            var count = 0
            var size = 0L
            var path = ""

            runCatching {
                val mediaRoot1 = File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media")
                val mediaRoot2 = File(Environment.getExternalStorageDirectory(), "WhatsApp/Media")
                val root = if (mediaRoot1.exists()) mediaRoot1 else mediaRoot2
                val dir = File(root, subFolder)
                path = dir.absolutePath

                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            count++
                            size += file.length()
                        }
                    }
                }
            }

            categories.add(
                WhatsAppMediaCategory(
                    id = id,
                    nameAr = nameAr,
                    nameEn = subFolder,
                    folderPath = path,
                    estimatedCount = count,
                    estimatedSizeBytes = size,
                    isSelected = (id != "wa_videos")
                )
            )
        }

        categories
    }
}
