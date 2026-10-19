package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.StarCloudApp
import com.example.data.local.FolderConfigEntity
import com.example.data.local.SyncLogEntity
import com.example.data.model.CloudQuota
import com.example.data.model.ConflictPolicy
import com.example.data.model.ContactItem
import com.example.data.model.InstalledAppItem
import com.example.data.model.ScheduleInterval
import com.example.data.model.SmsLogItem
import com.example.data.model.CallLogItem
import com.example.data.model.SyncDirection
import com.example.data.model.SyncProgressState
import com.example.data.model.WhatsAppMediaCategory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ScreenTab {
    DASHBOARD,
    SELECTIVE_SYNC,
    SYNC_LOGS,
    SETTINGS
}

data class StorageVolumeInfo(
    val label: String,
    val path: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val isRemovable: Boolean
) {
    val usedRatio: Float get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes) else 0f
}

data class DeviceStorageInfo(
    val internal: StorageVolumeInfo? = null,
    val appUsedBytes: Long = 0L,
    val externalVolumes: List<StorageVolumeInfo> = emptyList()
)

class StarCloudViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as StarCloudApp
    private val database = app.database
    private val preferences = app.preferences
    private val syncEngine = app.syncEngine
    private val cloudRegistry = app.cloudRegistry
    private val scheduler = app.syncScheduler

    private val _currentTab = MutableStateFlow(ScreenTab.DASHBOARD)
    val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

    private val _showTechnicalProposal = MutableStateFlow(false)
    val showTechnicalProposal: StateFlow<Boolean> = _showTechnicalProposal.asStateFlow()

    private val _deviceStorage = MutableStateFlow(DeviceStorageInfo())
    val deviceStorage: StateFlow<DeviceStorageInfo> = _deviceStorage.asStateFlow()

    private val _cloudQuota = MutableStateFlow(CloudQuota())
    val cloudQuota: StateFlow<CloudQuota> = _cloudQuota.asStateFlow()

    val syncDirection = preferences.syncDirection
    val conflictPolicy = preferences.conflictPolicy
    val scheduleInterval = preferences.scheduleInterval
    val wifiOnly = preferences.wifiOnly
    val requireCharging = preferences.requireCharging
    val clientEncryption = preferences.clientEncryption
    val connectedAccount = preferences.connectedAccount
    val maxFileSizeMb = preferences.maxFileSizeMb
    val cloudEnabled = preferences.cloudEnabled
    val destinationLabel = preferences.destinationLabel
    val destinationUri = preferences.destinationUri
    val googleDriveMode = preferences.googleDriveMode
    val googleEmail = preferences.googleEmail

    val syncContactsPref = preferences.syncContacts
    val syncMessagesPref = preferences.syncMessages
    val syncCallsPref = preferences.syncCalls
    val syncWhatsAppPref = preferences.syncWhatsApp
    val syncAppsPref = preferences.syncApps

    val folderConfigs: StateFlow<List<FolderConfigEntity>> = database.syncDao()
        .getAllFolderConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncLogs: StateFlow<List<SyncLogEntity>> = database.syncDao()
        .getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _contacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val contacts: StateFlow<List<ContactItem>> = _contacts.asStateFlow()

    private val _smsList = MutableStateFlow<List<SmsLogItem>>(emptyList())
    val smsList: StateFlow<List<SmsLogItem>> = _smsList.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLogItem>>(emptyList())
    val callLogs: StateFlow<List<CallLogItem>> = _callLogs.asStateFlow()

    private val _whatsAppCategories = MutableStateFlow<List<WhatsAppMediaCategory>>(emptyList())
    val whatsAppCategories: StateFlow<List<WhatsAppMediaCategory>> = _whatsAppCategories.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    private val _destinationReady = MutableStateFlow(false)
    val destinationReady: StateFlow<Boolean> = _destinationReady.asStateFlow()

    val syncProgress: StateFlow<SyncProgressState> = syncEngine.progressState

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        refreshStorageInfo()
        loadSelectiveData()
    }

    fun selectTab(tab: ScreenTab) {
        _currentTab.value = tab
    }

    fun showProposal(show: Boolean) {
        _showTechnicalProposal.value = show
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun refreshStorageInfo() {
        viewModelScope.launch {
            _deviceStorage.value = withContext(Dispatchers.IO) { readDeviceStorage() }
            _destinationReady.value = cloudRegistry.destinationReady()
            _cloudQuota.value = runCatching {
                withContext(Dispatchers.IO) { cloudRegistry.getActiveProvider().getQuota() }
            }.getOrDefault(CloudQuota())
        }
    }

    private fun readDeviceStorage(): DeviceStorageInfo {
        val internal = runCatching {
            val internalPath = Environment.getDataDirectory().path.takeIf { it.isNotBlank() }
                ?: runCatching { Environment.getExternalStorageDirectory().path }.getOrNull()
                ?: return@runCatching null
            val stat = StatFs(internalPath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            StorageVolumeInfo(
                label = "ذاكرة الجهاز الداخلية",
                path = internalPath,
                totalBytes = total,
                usedBytes = (total - free).coerceAtLeast(0),
                freeBytes = free,
                isRemovable = false
            )
        }.getOrNull()

        val appUsed = runCatching {
            app.filesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)

        val volumes = mutableListOf<StorageVolumeInfo>()
        val seen = mutableSetOf<String>()
        runCatching {
            app.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
                runCatching {
                    val rootPath = dir.absolutePath.substringBefore("/Android/")
                    if (rootPath.isBlank() || !seen.add(rootPath)) return@forEach
                    val stat = StatFs(rootPath)
                    val total = stat.blockCountLong * stat.blockSizeLong
                    val free = stat.availableBlocksLong * stat.blockSizeLong
                    val removable = !rootPath.contains("/emulated/")
                    if (removable) {
                        volumes += StorageVolumeInfo(
                            label = "بطاقة SD",
                            path = rootPath,
                            totalBytes = total,
                            usedBytes = (total - free).coerceAtLeast(0),
                            freeBytes = free,
                            isRemovable = true
                        )
                    }
                }
            }
        }

        return DeviceStorageInfo(
            internal = internal,
            appUsedBytes = appUsed,
            externalVolumes = volumes
        )
    }

    fun loadSelectiveData() {
        viewModelScope.launch {
            runCatching { _contacts.value = app.contactsHelper.getContacts() }
            runCatching { _smsList.value = app.smsHelper.getSmsMessages() }
            runCatching { _callLogs.value = app.smsHelper.getCallLogs() }
            runCatching { _whatsAppCategories.value = app.waHelper.getWhatsAppMediaCategories() }
            runCatching { _installedApps.value = app.appHelper.getInstalledApps(includeSystemApps = false) }
        }
    }

    fun addFolderConfigFromSaf(uri: Uri, folderName: String) {
        viewModelScope.launch {
            val entity = FolderConfigEntity(
                id = UUID.randomUUID().toString(),
                name = folderName,
                treeUriString = uri.toString(),
                isEnabled = true,
                fileCount = 0,
                totalSizeBytes = 0L,
                lastSyncTime = System.currentTimeMillis()
            )
            database.syncDao().insertFolderConfig(entity)
            _userMessage.value = "تمت إضافة المجلد $folderName للمزامنة الانتقائية بنجاح"
        }
    }

    fun toggleFolderEnabled(folder: FolderConfigEntity) {
        viewModelScope.launch {
            database.syncDao().insertFolderConfig(folder.copy(isEnabled = !folder.isEnabled))
        }
    }

    fun removeFolderConfig(id: String) {
        viewModelScope.launch {
            database.syncDao().deleteFolderConfig(id)
            _userMessage.value = "تمت إزالة المجلد من قائمة المزامنة"
        }
    }

    fun toggleContact(id: String) {
        _contacts.value = _contacts.value.map {
            if (it.id == id) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun selectAllContacts(select: Boolean) {
        _contacts.value = _contacts.value.map { it.copy(isSelected = select) }
    }

    fun toggleWhatsAppCategory(id: String) {
        _whatsAppCategories.value = _whatsAppCategories.value.map {
            if (it.id == id) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun toggleInstalledApp(packageName: String) {
        _installedApps.value = _installedApps.value.map {
            if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun selectAllApps(select: Boolean) {
        _installedApps.value = _installedApps.value.map { it.copy(isSelected = select) }
    }

    fun setAppSearchQuery(query: String) {
        _appSearchQuery.value = query
    }

    fun googleSignInIntent(): Intent = app.googleAccountManager.signInIntent()

    fun onGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    GoogleSignIn.getSignedInAccountFromIntent(data)
                        .getResult(ApiException::class.java)
                }
            }
            result.onSuccess { account ->
                val email = account?.email
                if (account == null || email.isNullOrBlank()) {
                    _userMessage.value = "لم يتم اختيار أي حساب Google"
                    return@onSuccess
                }
                preferences.setGoogleEmail(email)
                preferences.setGoogleDriveMode(true)
                preferences.setCloudEnabled(true)
                _userMessage.value = "تم تسجيل الدخول: $email. ستُرفع الملفات إلى مجلد Star Cloud في Google Drive."
                refreshStorageInfo()
            }.onFailure { error ->
                val message = if ((error as? ApiException)?.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                    "تم إلغاء تسجيل الدخول إلى Google"
                } else {
                    "فشل تسجيل الدخول إلى Google (${error.message}). تأكد من تفعيل Google Drive API وإضافة بصمة التطبيق SHA-1 في مشروع Google Cloud."
                }
                _userMessage.value = message
            }
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.googleAccountManager.signOut() }
            preferences.setGoogleEmail(null)
            preferences.setGoogleDriveMode(false)
            preferences.setCloudEnabled(false)
            _userMessage.value = "تم تسجيل الخروج من Google. المزامنة الآن محلية."
            refreshStorageInfo()
        }
    }

    fun startSyncNow() {
        viewModelScope.launch {
            if (syncProgress.value.isSyncing) return@launch
            val externalRequested = preferences.isCloudEnabled()
            if (externalRequested && !cloudRegistry.destinationReady()) {
                _userMessage.value = if (preferences.isGoogleDriveMode()) {
                    "سجّل الدخول بحساب Google أولًا من الإعدادات لتشغيل المزامنة السحابية."
                } else {
                    "إذن الكتابة على المجلد المختار غير متاح. أعد اختيار وجهة المزامنة من الإعدادات."
                }
                return@launch
            }
            if (preferences.syncContacts.value && _contacts.value.isEmpty()) {
                runCatching { _contacts.value = app.contactsHelper.getContacts() }
            }
            if (preferences.syncWhatsApp.value && _whatsAppCategories.value.isEmpty()) {
                runCatching { _whatsAppCategories.value = app.waHelper.getWhatsAppMediaCategories() }
            }
            val provider = cloudRegistry.getActiveProvider()
            val success = syncEngine.executeSync(
                provider = provider,
                direction = syncDirection.value,
                folderConfigs = folderConfigs.value,
                selectedContacts = _contacts.value.filter { it.isSelected },
                selectedWhatsAppCategories = _whatsAppCategories.value.filter { it.isSelected },
                selectedApps = _installedApps.value.filter { it.isSelected }
            )
            refreshStorageInfo()
            val destination = when {
                !externalRequested -> "مساحة التطبيق الداخلية"
                preferences.isGoogleDriveMode() ->
                    "Google Drive (${preferences.getGoogleEmail() ?: "حسابك"})"
                else -> preferences.getDestinationLabel()
            }
            _userMessage.value = if (success) {
                "تمت المزامنة إلى $destination. تجد الملفات داخل مجلد Star Cloud."
            } else {
                "حدث خطأ أثناء المزامنة، راجع سجل العمليات"
            }
        }
    }

    fun setDestination(uri: Uri, label: String) {
        val granted = app.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        if (!granted) {
            _userMessage.value = "تعذر الحصول على إذن الكتابة على المجلد. اختر مجلدًا آخر."
            return
        }
        preferences.setDestination(uri.toString(), label)
        preferences.setCloudEnabled(true)
        refreshStorageInfo()
        _userMessage.value = "تم ربط الوجهة: $label. ستُحفظ الملفات داخل مجلد Star Cloud."
    }

    fun useInternalStorage() {
        preferences.useInternalDestination()
        refreshStorageInfo()
        _userMessage.value = "تم اختيار مساحة التطبيق الداخلية. المزامنة السحابية اختيارية ويمكن تفعيلها لاحقًا."
    }

    fun setCloudEnabled(enabled: Boolean) {
        if (enabled && preferences.getDestinationUri() == null && !preferences.isGoogleDriveMode()) {
            _userMessage.value = "سجّل الدخول بحساب Google أو اختر مجلدًا أولًا."
            return
        }
        preferences.setCloudEnabled(enabled)
        refreshStorageInfo()
        val target = if (preferences.isGoogleDriveMode()) {
            "Google Drive (${preferences.getGoogleEmail() ?: "حسابك"})"
        } else {
            preferences.getDestinationLabel()
        }
        _userMessage.value = if (enabled) {
            "تم تفعيل المزامنة إلى $target"
        } else {
            "تم تحويل المزامنة إلى مساحة التطبيق الداخلية"
        }
    }

    fun cancelSync() {
        syncEngine.cancelSync()
        _userMessage.value = "تم إلغاء المزامنة"
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            database.syncDao().clearLogs()
            _userMessage.value = "تم مسح جميع سجلات المزامنة"
        }
    }

    fun setSyncDirection(dir: SyncDirection) = preferences.setSyncDirection(dir)
    fun setConflictPolicy(pol: ConflictPolicy) = preferences.setConflictPolicy(pol)
    fun setScheduleInterval(interval: ScheduleInterval) {
        preferences.setScheduleInterval(interval)
        scheduler.applySchedule(interval)
        _userMessage.value = "تم ضبط جدولة المزامنة: ${interval.titleAr}"
    }
    fun setWifiOnly(enabled: Boolean) = preferences.setWifiOnly(enabled)
    fun setRequireCharging(enabled: Boolean) = preferences.setRequireCharging(enabled)
    fun setClientEncryption(enabled: Boolean) = preferences.setClientEncryption(enabled)
    fun setCategoryToggle(category: String, enabled: Boolean) = preferences.setCategoryEnabled(category, enabled)
    fun setMaxFileSize(mb: Int) = preferences.setMaxFileSizeMb(mb)
}
