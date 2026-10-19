package com.example.ui

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.StarCloudApp
import com.example.data.cloud.RemoteFileMetadata
import com.example.data.local.FolderConfigEntity
import com.example.data.local.SyncLogEntity
import com.example.data.model.CloudProviderType
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class ScreenTab {
    DASHBOARD,
    SELECTIVE_SYNC,
    SYNC_LOGS,
    SETTINGS
}

data class DeviceStorageInfo(
    val totalBytes: Long = 64L * 1024 * 1024 * 1024,
    val usedBytes: Long = 38L * 1024 * 1024 * 1024,
    val freeBytes: Long = 26L * 1024 * 1024 * 1024
) {
    val usedRatio: Float get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes) else 0f
}

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

    private val _selectedProvider = MutableStateFlow(CloudProviderType.GOOGLE_DRIVE)
    val selectedProvider: StateFlow<CloudProviderType> = _selectedProvider.asStateFlow()

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
            runCatching {
                val stat = StatFs(Environment.getDataDirectory().path)
                val total = stat.blockCountLong * stat.blockSizeLong
                val free = stat.availableBlocksLong * stat.blockSizeLong
                val used = (total - free).coerceAtLeast(0)
                _deviceStorage.value = DeviceStorageInfo(total, used, free)
            }

            runCatching {
                val provider = cloudRegistry.getProvider(_selectedProvider.value)
                _cloudQuota.value = provider.getQuota()
            }
        }
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

    fun startSyncNow() {
        viewModelScope.launch {
            if (syncProgress.value.isSyncing) return@launch
            val provider = cloudRegistry.getProvider(_selectedProvider.value)
            val success = syncEngine.executeSync(
                provider = provider,
                direction = syncDirection.value,
                folderConfigs = folderConfigs.value,
                selectedContacts = _contacts.value.filter { it.isSelected },
                selectedWhatsAppCategories = _whatsAppCategories.value.filter { it.isSelected },
                selectedApps = _installedApps.value.filter { it.isSelected }
            )
            refreshStorageInfo()
            _userMessage.value = if (success) "اكتملت المزامنة السحابية بنجاح!" else "حدث تنبيه أثناء المزامنة، راجع السجلات"
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
    fun setConnectedAccount(account: String) {
        preferences.setConnectedAccount(account)
        _userMessage.value = "تم تحديث حساب Google Drive إلى: $account"
    }
    fun setCategoryToggle(category: String, enabled: Boolean) = preferences.setCategoryEnabled(category, enabled)
    fun setMaxFileSize(mb: Int) = preferences.setMaxFileSizeMb(mb)
    fun setCloudProvider(type: CloudProviderType) {
        _selectedProvider.value = type
        refreshStorageInfo()
    }
}
