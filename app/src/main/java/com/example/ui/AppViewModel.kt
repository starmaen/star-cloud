package com.example.ui

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.StarApp
import com.example.core.*
import com.example.core.data.RunLogEntity
import com.example.core.data.Settings
import com.example.core.dest.DestinationFactory
import com.example.core.engine.*
import com.example.work.RunWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class DestInfo(val quota: QuotaInfo?, val backupBytes: Long, val files: Int, val error: String? = null)

data class Prefs(
    val interval: Interval, val wifiOnly: Boolean, val chargingOnly: Boolean,
    val keepDeleted: Boolean, val maxFileMb: Int, val keepVersions: Int, val deviceName: String
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StarApp
    val settings: Settings = app.settings
    val google = app.google
    val run = app.tracker.state

    /** يزداد عند العودة للتطبيق لإعادة قراءة الصلاحيات. */
    var tick by mutableIntStateOf(0)

    var selection by mutableStateOf(settings.selection); private set
    var destinations by mutableStateOf(settings.destinations); private set
    var prefs by mutableStateOf(readPrefs()); private set
    var deviceStorage by mutableStateOf<QuotaInfo?>(null); private set
    var destInfo by mutableStateOf<Map<String, DestInfo>>(emptyMap()); private set
    var lastBackup by mutableStateOf<RunLogEntity?>(null); private set
    var logs by mutableStateOf<List<RunLogEntity>>(emptyList()); private set
    var priv by mutableStateOf(PrivMode.NONE); private set
    var apps by mutableStateOf<List<AppItem>?>(null); private set
    var message by mutableStateOf<String?>(null)

    private fun readPrefs() = Prefs(settings.interval, settings.wifiOnly, settings.chargingOnly,
        settings.keepDeleted, settings.maxFileMb, settings.keepVersions, settings.deviceName)

    // ---------- الصلاحيات ----------
    fun hasAllFiles() = DestinationFactory.hasAllFilesAccess(app)
    fun granted(p: String) = app.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    fun hasContacts() = granted(android.Manifest.permission.READ_CONTACTS)
    fun hasSms() = granted(android.Manifest.permission.READ_SMS)
    fun hasCalls() = granted(android.Manifest.permission.READ_CALL_LOG)
    fun isDefaultSms() = Sources.isDefaultSmsApp(app)

    // ---------- الاختيارات ----------
    fun updateSelection(f: (Selection) -> Selection) { selection = f(selection); settings.selection = selection }

    fun togglePath(path: String) = updateSelection { s ->
        if (path in s.paths) s.copy(paths = s.paths - path) else s.copy(paths = s.paths + path)
    }

    fun toggleApp(pkg: String) = updateSelection { s ->
        if (s.apps.any { it.pkg == pkg }) s.copy(apps = s.apps.filter { it.pkg != pkg }) else s.copy(apps = s.apps + AppChoice(pkg))
    }

    fun setAppData(pkg: String, on: Boolean) = updateSelection { s ->
        s.copy(apps = s.apps.map { if (it.pkg == pkg) it.copy(withData = on) else it })
    }

    fun setApps(pkgs: Collection<String>, on: Boolean) = updateSelection { s ->
        val keep = s.apps.filter { it.pkg !in pkgs }
        s.copy(apps = if (on) keep + pkgs.map { p -> s.apps.firstOrNull { it.pkg == p } ?: AppChoice(p) } else keep)
    }

    // ---------- الإعدادات ----------
    fun savePrefs(f: Settings.() -> Unit) { settings.f(); prefs = readPrefs(); RunWorker.applySchedule(app) }

    // ---------- الوجهات ----------
    fun addDestination(c: DestConfig) { settings.addDestination(c); destinations = settings.destinations; refreshInfo() }
    fun removeDestination(id: String) {
        settings.removeDestination(id); destinations = settings.destinations
        viewModelScope.launch(Dispatchers.IO) { app.db.dao().clearIndex(id) }
    }
    fun setDestEnabled(id: String, on: Boolean) { settings.setDestinationEnabled(id, on); destinations = settings.destinations }
    fun forceFullBackup() {
        viewModelScope.launch(Dispatchers.IO) { destinations.forEach { app.db.dao().clearIndex(it.id) } }
        message = "سيُعاد فحص ورفع كل الملفات في النسخة القادمة"
    }

    fun refreshInfo() {
        viewModelScope.launch {
            deviceStorage = withContext(Dispatchers.IO) { runCatching { DeviceStorage.read(app) }.getOrNull() }
            lastBackup = withContext(Dispatchers.IO) { app.db.dao().lastGoodBackup() }
            logs = withContext(Dispatchers.IO) { app.db.dao().recentLogs() }
            priv = Privileged.detect(force = true)
            val out = HashMap<String, DestInfo>()
            for (c in destinations) {
                val q = withTimeoutOrNull(15_000) {
                    runCatching { DestinationFactory.create(app, c, google).also { it.prepare() }.quota() }
                }
                val bytes = withContext(Dispatchers.IO) { app.db.dao().indexedBytes(c.id) }
                val n = withContext(Dispatchers.IO) { app.db.dao().indexedCount(c.id) }
                out[c.id] = DestInfo(q?.getOrNull(), bytes, n, q?.exceptionOrNull()?.message ?: if (q == null) "انتهت مهلة الاتصال" else null)
            }
            destInfo = out
        }
    }

    fun loadApps() {
        if (apps != null) return
        viewModelScope.launch { apps = Sources.installedApps(app) }
    }

    // ---------- التشغيل ----------
    fun startBackup(): Boolean {
        val enabled = destinations.filter { it.enabled }
        if (enabled.isEmpty()) { message = "أضف وجهة للنسخ أولاً"; return false }
        if (selection.isEmpty) { message = "اختر ما تريد نسخه أولاً"; return false }
        RunWorker.runNow(app, false); return true
    }

    fun startRestore(plan: RestorePlan) { RestoreHolder.plan = plan; RunWorker.runNow(app, true) }
    fun cancelRun() { RunWorker.cancel(app) }

    // ---------- الاستعادة (تصفّح النسخ) ----------
    var rDest by mutableStateOf<DestConfig?>(null)
    var rDevices by mutableStateOf<List<String>?>(null)
    var rDevice by mutableStateOf<String?>(null)
    var rLoading by mutableStateOf(false)
    var rError by mutableStateOf<String?>(null)
    var rApps by mutableStateOf<List<AppMeta>>(emptyList())
    var rSelApps by mutableStateOf<Map<String, Boolean>>(emptyMap())
    var rNodes by mutableStateOf<List<RemoteNode>>(emptyList())
    var rContacts by mutableStateOf<List<RemoteEntry>>(emptyList())
    var rSms by mutableStateOf<List<RemoteEntry>>(emptyList())
    var rCalls by mutableStateOf<List<RemoteEntry>>(emptyList())
    var rWantContacts by mutableStateOf(false)
    var rWantSms by mutableStateOf(false)
    var rWantCalls by mutableStateOf(false)
    var rToOriginal by mutableStateOf(true)
    var rOverwrite by mutableStateOf(true)
    var rBrowse by mutableStateOf<List<RemoteEntry>>(emptyList())
    var rBrowsePath by mutableStateOf<List<String>>(emptyList())

    private var catalog: Catalog? = null

    private fun openDest(): com.example.core.dest.Destination =
        DestinationFactory.create(app, rDest!!, google)

    fun restoreChooseDest(c: DestConfig) {
        rDest = c; rDevices = null; rDevice = null; rError = null; clearRestoreSel()
        viewModelScope.launch {
            rLoading = true
            try {
                val d = withContext(Dispatchers.IO) { openDest().also { it.prepare() } }
                val list = withContext(Dispatchers.IO) { RestoreCatalog.devices(d) }
                rDevices = list
                if (list.isEmpty()) rError = "لا توجد نسخ احتياطية في هذه الوجهة"
                else if (list.size == 1 || settings.deviceName in list) restoreChooseDevice(if (settings.deviceName in list) settings.deviceName else list[0])
            } catch (e: Exception) { rError = e.message ?: "تعذّر قراءة الوجهة" }
            rLoading = false
        }
    }

    private fun clearRestoreSel() {
        rApps = emptyList(); rSelApps = emptyMap(); rNodes = emptyList(); rContacts = emptyList(); rSms = emptyList(); rCalls = emptyList()
        rWantContacts = false; rWantSms = false; rWantCalls = false; rBrowse = emptyList(); rBrowsePath = emptyList()
    }

    fun restoreChooseDevice(device: String) {
        rDevice = device; clearRestoreSel(); rError = null
        viewModelScope.launch {
            rLoading = true
            try {
                val d = withContext(Dispatchers.IO) { openDest() }
                val cat = Catalog(d, device); catalog = cat
                rApps = withContext(Dispatchers.IO) { cat.apps() }
                rContacts = withContext(Dispatchers.IO) { cat.versions(Layout.CONTACTS, "contacts") }
                rSms = withContext(Dispatchers.IO) { cat.versions(Layout.MESSAGES, "sms") }
                rCalls = withContext(Dispatchers.IO) { cat.versions(Layout.MESSAGES, "calls") }
                rBrowse = withContext(Dispatchers.IO) { cat.files(emptyList()) }
            } catch (e: Exception) { rError = e.message ?: "تعذّر قراءة النسخة" }
            rLoading = false
        }
    }

    fun browseRemote(segs: List<String>) {
        val cat = catalog ?: return
        viewModelScope.launch {
            rLoading = true
            try { rBrowse = withContext(Dispatchers.IO) { cat.files(segs) }; rBrowsePath = segs }
            catch (e: Exception) { rError = e.message }
            rLoading = false
        }
    }

    fun toggleNode(segs: List<String>, isDir: Boolean) {
        rNodes = if (rNodes.any { it.segs == segs }) rNodes.filter { it.segs != segs } else rNodes + RemoteNode(segs, isDir)
    }

    fun toggleRestoreApp(pkg: String) {
        rSelApps = if (pkg in rSelApps) rSelApps - pkg else rSelApps + (pkg to false)
    }
    fun setRestoreAppData(pkg: String, on: Boolean) { rSelApps = rSelApps + (pkg to on) }

    fun buildPlan(): RestorePlan? {
        val dest = rDest ?: return null; val dev = rDevice ?: return null
        return RestorePlan(
            destId = dest.id, device = dev,
            apps = rSelApps.map { it.key to it.value },
            nodes = rNodes,
            contactsFile = if (rWantContacts) rContacts.firstOrNull()?.name else null,
            smsFile = if (rWantSms) rSms.firstOrNull()?.name else null,
            callsFile = if (rWantCalls) rCalls.firstOrNull()?.name else null,
            toOriginal = rToOriginal, overwrite = rOverwrite
        )
    }

    fun storageRoots(): List<java.io.File> {
        val roots = ArrayList<java.io.File>()
        roots.add(Environment.getExternalStorageDirectory())
        app.getExternalFilesDirs(null).forEach { f ->
            val p = f?.path ?: return@forEach
            val i = p.indexOf("/Android/data")
            if (i > 0) { val r = java.io.File(p.substring(0, i)); if (roots.none { it.path == r.path }) roots.add(r) }
        }
        return roots
    }
}
