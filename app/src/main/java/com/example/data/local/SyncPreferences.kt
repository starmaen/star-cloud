package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ConflictPolicy
import com.example.data.model.ScheduleInterval
import com.example.data.model.SyncDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SyncPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("star_cloud_prefs", Context.MODE_PRIVATE)

    private val _syncDirection = MutableStateFlow(getSyncDirection())
    val syncDirection: StateFlow<SyncDirection> = _syncDirection

    private val _conflictPolicy = MutableStateFlow(getConflictPolicy())
    val conflictPolicy: StateFlow<ConflictPolicy> = _conflictPolicy

    private val _scheduleInterval = MutableStateFlow(getScheduleInterval())
    val scheduleInterval: StateFlow<ScheduleInterval> = _scheduleInterval

    private val _wifiOnly = MutableStateFlow(isWifiOnly())
    val wifiOnly: StateFlow<Boolean> = _wifiOnly

    private val _requireCharging = MutableStateFlow(isRequireCharging())
    val requireCharging: StateFlow<Boolean> = _requireCharging

    private val _clientEncryption = MutableStateFlow(isClientEncryptionEnabled())
    val clientEncryption: StateFlow<Boolean> = _clientEncryption

    private val _connectedAccount = MutableStateFlow(getConnectedAccount())
    val connectedAccount: StateFlow<String> = _connectedAccount

    private val _cloudEnabled = MutableStateFlow(isCloudEnabled())
    val cloudEnabled: StateFlow<Boolean> = _cloudEnabled

    private val _destinationLabel = MutableStateFlow(getDestinationLabel())
    val destinationLabel: StateFlow<String> = _destinationLabel

    private val _destinationUri = MutableStateFlow(getDestinationUri())
    val destinationUri: StateFlow<String?> = _destinationUri

    private val _googleDriveMode = MutableStateFlow(isGoogleDriveMode())
    val googleDriveMode: StateFlow<Boolean> = _googleDriveMode

    private val _googleEmail = MutableStateFlow(getGoogleEmail())
    val googleEmail: StateFlow<String?> = _googleEmail

    private val _syncContacts = MutableStateFlow(isCategoryEnabled("contacts", true))
    val syncContacts: StateFlow<Boolean> = _syncContacts

    private val _syncMessages = MutableStateFlow(isCategoryEnabled("messages", true))
    val syncMessages: StateFlow<Boolean> = _syncMessages

    private val _syncCalls = MutableStateFlow(isCategoryEnabled("calls", true))
    val syncCalls: StateFlow<Boolean> = _syncCalls

    private val _syncWhatsApp = MutableStateFlow(isCategoryEnabled("whatsapp", true))
    val syncWhatsApp: StateFlow<Boolean> = _syncWhatsApp

    private val _syncApps = MutableStateFlow(isCategoryEnabled("apps", false))
    val syncApps: StateFlow<Boolean> = _syncApps

    private val _maxFileSizeMb = MutableStateFlow(prefs.getInt(KEY_MAX_FILE_SIZE, 100))
    val maxFileSizeMb: StateFlow<Int> = _maxFileSizeMb

    fun setSyncDirection(direction: SyncDirection) {
        prefs.edit().putString(KEY_SYNC_DIRECTION, direction.name).apply()
        _syncDirection.value = direction
    }

    fun getSyncDirection(): SyncDirection {
        val name = prefs.getString(KEY_SYNC_DIRECTION, SyncDirection.BACKUP.name)
        return runCatching { SyncDirection.valueOf(name ?: SyncDirection.BACKUP.name) }.getOrDefault(SyncDirection.BACKUP)
    }

    fun setConflictPolicy(policy: ConflictPolicy) {
        prefs.edit().putString(KEY_CONFLICT_POLICY, policy.name).apply()
        _conflictPolicy.value = policy
    }

    fun getConflictPolicy(): ConflictPolicy {
        val name = prefs.getString(KEY_CONFLICT_POLICY, ConflictPolicy.NEWEST_WINS.name)
        return runCatching { ConflictPolicy.valueOf(name ?: ConflictPolicy.NEWEST_WINS.name) }.getOrDefault(ConflictPolicy.NEWEST_WINS)
    }

    fun setScheduleInterval(interval: ScheduleInterval) {
        prefs.edit().putString(KEY_SCHEDULE_INTERVAL, interval.name).apply()
        _scheduleInterval.value = interval
    }

    fun getScheduleInterval(): ScheduleInterval {
        val name = prefs.getString(KEY_SCHEDULE_INTERVAL, ScheduleInterval.DAILY.name)
        return runCatching { ScheduleInterval.valueOf(name ?: ScheduleInterval.DAILY.name) }.getOrDefault(ScheduleInterval.DAILY)
    }

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        _wifiOnly.value = enabled
    }

    fun isWifiOnly(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, true)

    fun setRequireCharging(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_CHARGING, enabled).apply()
        _requireCharging.value = enabled
    }

    fun isRequireCharging(): Boolean = prefs.getBoolean(KEY_REQUIRE_CHARGING, false)

    fun setClientEncryption(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLIENT_ENCRYPTION, enabled).apply()
        _clientEncryption.value = enabled
    }

    fun isClientEncryptionEnabled(): Boolean = prefs.getBoolean(KEY_CLIENT_ENCRYPTION, false)

    fun setConnectedAccount(account: String) {
        prefs.edit().putString(KEY_CONNECTED_ACCOUNT, account).apply()
        _connectedAccount.value = account
    }

    fun isGoogleDriveMode(): Boolean = prefs.getBoolean(KEY_GOOGLE_MODE, false)

    fun setGoogleDriveMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GOOGLE_MODE, enabled).apply()
        _googleDriveMode.value = enabled
    }

    fun getGoogleEmail(): String? = prefs.getString(KEY_GOOGLE_EMAIL, null)

    fun setGoogleEmail(email: String?) {
        prefs.edit().putString(KEY_GOOGLE_EMAIL, email).apply()
        _googleEmail.value = email
        _connectedAccount.value = email ?: ""
    }

    fun getConnectedAccount(): String = getGoogleEmail() ?: ""

    fun isCloudEnabled(): Boolean = prefs.getBoolean(KEY_CLOUD_ENABLED, false)

    fun setCloudEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLOUD_ENABLED, enabled).apply()
        _cloudEnabled.value = enabled
    }

    fun getDestinationUri(): String? = prefs.getString(KEY_DESTINATION_URI, null)

    fun getDestinationLabel(): String =
        prefs.getString(KEY_DESTINATION_LABEL, "مساحة التطبيق الداخلية") ?: "مساحة التطبيق الداخلية"

    fun setDestination(uri: String?, label: String) {
        prefs.edit()
            .putString(KEY_DESTINATION_URI, uri)
            .putString(KEY_DESTINATION_LABEL, label)
            .putBoolean(KEY_GOOGLE_MODE, false)
            .apply()
        _destinationUri.value = uri
        _destinationLabel.value = label
        _googleDriveMode.value = false
    }

    fun useInternalDestination() {
        prefs.edit()
            .remove(KEY_DESTINATION_URI)
            .putString(KEY_DESTINATION_LABEL, "مساحة التطبيق الداخلية")
            .apply()
        _destinationUri.value = null
        _destinationLabel.value = "مساحة التطبيق الداخلية"
        setCloudEnabled(false)
    }

    fun setCategoryEnabled(category: String, enabled: Boolean) {
        prefs.edit().putBoolean("cat_$category", enabled).apply()
        when (category) {
            "contacts" -> _syncContacts.value = enabled
            "messages" -> _syncMessages.value = enabled
            "calls" -> _syncCalls.value = enabled
            "whatsapp" -> _syncWhatsApp.value = enabled
            "apps" -> _syncApps.value = enabled
        }
    }

    fun isCategoryEnabled(category: String, default: Boolean): Boolean =
        prefs.getBoolean("cat_$category", default)

    fun setMaxFileSizeMb(mb: Int) {
        prefs.edit().putInt(KEY_MAX_FILE_SIZE, mb).apply()
        _maxFileSizeMb.value = mb
    }

    fun getMaxFileSizeMb(): Int = prefs.getInt(KEY_MAX_FILE_SIZE, 100)

    companion object {
        private const val KEY_SYNC_DIRECTION = "pref_sync_direction"
        private const val KEY_CONFLICT_POLICY = "pref_conflict_policy"
        private const val KEY_SCHEDULE_INTERVAL = "pref_schedule_interval"
        private const val KEY_WIFI_ONLY = "pref_wifi_only"
        private const val KEY_REQUIRE_CHARGING = "pref_require_charging"
        private const val KEY_CLIENT_ENCRYPTION = "pref_client_encryption"
        private const val KEY_CONNECTED_ACCOUNT = "pref_connected_account"
        private const val KEY_CLOUD_ENABLED = "pref_cloud_enabled"
        private const val KEY_DESTINATION_URI = "pref_destination_uri"
        private const val KEY_DESTINATION_LABEL = "pref_destination_label"
        private const val KEY_MAX_FILE_SIZE = "pref_max_file_size"
        private const val KEY_GOOGLE_MODE = "pref_google_drive_mode"
        private const val KEY_GOOGLE_EMAIL = "pref_google_email"
    }
}
