package com.example.data.model

enum class SyncCategory(val titleAr: String, val titleEn: String) {
    FOLDERS("المجلدات والملفات", "Folders & Files"),
    CONTACTS("جهات الاتصال", "Contacts"),
    MESSAGES("الرسائل والمكالمات", "Messages & Calls"),
    WHATSAPP("تطبيقات المراسلة (واتساب)", "Messaging Apps (WhatsApp)"),
    INSTALLED_APPS("تطبيقات الجهاز (APK)", "Installed Apps (APK)"),
    APP_DATA("بيانات التطبيقات (القيود)", "App Data (Sandbox)")
}

enum class SyncDirection(val titleAr: String, val titleEn: String) {
    BACKUP("نسخ احتياطي (الجهاز ➔ السحابة)", "Backup (Device ➔ Cloud)"),
    RESTORE("استعادة (السحابة ➔ الجهاز)", "Restore (Cloud ➔ Device)"),
    TWO_WAY("مزامنة ثنائية الاتجاه (⇋)", "Two-Way Sync (Bi-directional)")
}

enum class ConflictPolicy(val titleAr: String, val titleEn: String) {
    NEWEST_WINS("الأحدث يفوز دائمًا", "Newest Wins"),
    KEEP_BOTH("الاحتفاظ بنسختين (_conflict)", "Keep Both Copies"),
    DEVICE_WINS("اعتماد نسخة الجهاز", "Device Wins"),
    CLOUD_WINS("اعتماد نسخة السحابة", "Cloud Wins")
}

enum class ScheduleInterval(val titleAr: String, val titleEn: String, val hours: Long) {
    MANUAL_ONLY("يدوي فقط", "Manual Only", 0),
    HOURLY("كل ساعة", "Every Hour", 1),
    EVERY_6_HOURS("كل 6 ساعات", "Every 6 Hours", 6),
    DAILY("يوميًا", "Daily", 24),
    WEEKLY("أسبوعيًا", "Weekly", 168)
}

enum class CloudProviderType(val displayName: String, val iconRes: String) {
    GOOGLE_DRIVE("Google Drive (الأساسي)", "google_drive"),
    DROPBOX("Dropbox (قريبًا)", "dropbox"),
    ONE_DRIVE("Microsoft OneDrive (قريبًا)", "onedrive"),
    LOCAL_SECURE_STORAGE("Star Cloud Sandbox (مدمج)", "local_sandbox")
}

data class CloudQuota(
    val totalBytes: Long = 15L * 1024 * 1024 * 1024,
    val usedBytes: Long = 4L * 1024 * 1024 * 1024,
    val starCloudUsedBytes: Long = 1200L * 1024 * 1024
) {
    val freeBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0)
    val usedPercent: Float get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes) else 0f
}

data class SyncFolderConfig(
    val id: String,
    val name: String,
    val treeUriString: String,
    val isEnabled: Boolean = true,
    val fileCount: Int = 0,
    val totalSizeBytes: Long = 0,
    val lastSyncTime: Long = 0
)

data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val email: String? = null,
    val isSelected: Boolean = true
)

data class SmsLogItem(
    val id: String,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val isSelected: Boolean = true
)

data class CallLogItem(
    val id: String,
    val number: String,
    val name: String?,
    val date: Long,
    val durationSeconds: Long,
    val type: Int,
    val isSelected: Boolean = true
)

data class WhatsAppMediaCategory(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val folderPath: String,
    val estimatedCount: Int,
    val estimatedSizeBytes: Long,
    val isSelected: Boolean = true
)

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val apkSizeBytes: Long,
    val sourceDir: String,
    val isSystemApp: Boolean = false,
    val isSelected: Boolean = false
)

data class AdvancedFilterSettings(
    val maxFileSizeMb: Int = 100,
    val excludeFileExtensions: List<String> = listOf("tmp", "cache", "log", "bak"),
    val skipHiddenFiles: Boolean = true,
    val wifiOnly: Boolean = true,
    val requireCharging: Boolean = false,
    val enableClientSideEncryption: Boolean = false,
    val encryptionPassword: String = ""
)

data class SyncProgressState(
    val isSyncing: Boolean = false,
    val currentItemName: String = "",
    val currentCategory: SyncCategory? = null,
    val currentProgress: Float = 0f,
    val itemsProcessed: Int = 0,
    val totalItems: Int = 0,
    val bytesTransferred: Long = 0,
    val totalBytes: Long = 0,
    val transferRateBytesPerSec: Long = 0,
    val errorMessage: String? = null,
    val stage: String = ""
)

data class SyncHistoryLog(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val direction: SyncDirection,
    val status: String,
    val itemsSynced: Int,
    val totalSizeBytes: Long,
    val durationSeconds: Long,
    val summary: String,
    val provider: CloudProviderType = CloudProviderType.GOOGLE_DRIVE
)
