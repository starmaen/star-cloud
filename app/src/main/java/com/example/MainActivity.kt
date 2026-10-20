package com.example

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings as AndroidSettings
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.core.DestConfig
import com.example.core.DestKind
import com.example.core.data.Settings
import com.example.core.engine.Privileged
import com.example.core.engine.RestoreHolder
import com.example.ui.Actions
import com.example.ui.AppViewModel
import com.example.ui.screens.*
import com.example.ui.theme.StarCloudTheme
import com.example.work.RunWorker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

private enum class Overlay { APPS, FILES, DEST }

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val pickTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(::onTreePicked) }
    private val googleSignIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val err = runCatching { GoogleSignIn.getSignedInAccountFromIntent(r.data).getResult(ApiException::class.java) }.exceptionOrNull()
        if (err is ApiException) vm.message = when (err.statusCode) {
            10 -> "خطأ 10 (DEVELOPER_ERROR): لم يُسجَّل التطبيق في Google Cloud ببصمة SHA-1 الحالية. اتبع الخطوات في شاشة «Google Drive مباشر»، أو استخدم «Google Drive وتطبيقات السحابة» بدون أي إعداد."
            12501 -> "أُلغي تسجيل الدخول."
            12500 -> "فشل تسجيل الدخول. تأكد من إعداد شاشة الموافقة وإضافة بريدك مستخدماً تجريبياً."
            7 -> "لا يوجد اتصال بالإنترنت."
            else -> "تعذّر تسجيل الدخول (رمز ${err.statusCode})."
        }
        vm.tick++
    }
    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.tick++ }
    private val role = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.tick++ }

    private fun onTreePicked(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val doc = DocumentFile.fromTreeUri(this, uri)
        if (doc == null || !doc.canWrite()) { vm.message = "لا يمكن الكتابة في هذا المجلد. اختر مجلداً آخر."; return }
        val auth = uri.authority.orEmpty()
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrDefault("")
        val folder = docId.substringAfter(':', "").substringAfterLast('/').ifBlank { doc.name ?: "" }
        val kind = when {
            auth.contains("google.android.apps.docs") -> "Google Drive"
            auth.contains("onedrive", true) || auth.contains("microsoft", true) -> "OneDrive"
            auth.contains("dropbox", true) -> "Dropbox"
            auth.contains("externalstorage") -> if (docId.startsWith("primary")) "ذاكرة الهاتف" else "بطاقة SD"
            else -> auth.substringAfterLast('.')
        }
        val label = if (folder.isBlank()) kind else "$kind / $folder"
        if (vm.destinations.any { it.target == uri.toString() }) { vm.message = "هذا المجلد مضاف مسبقاً."; return }
        vm.addDestination(DestConfig(Settings.newId(), DestKind.FOLDER, label, true, uri.toString()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) perms.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        RunWorker.applySchedule(this)

        val actions = Actions(
            pickFolder = { pickTree.launch(null) },
            signInGoogle = { googleSignIn.launch(vm.google.signInIntent()) },
            askContacts = { perms.launch(arrayOf(Manifest.permission.READ_CONTACTS)) },
            askSms = { perms.launch(arrayOf(Manifest.permission.READ_SMS)) },
            askCalls = { perms.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG)) },
            openAllFiles = {
                if (Build.VERSION.SDK_INT >= 30) {
                    val i = Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                    runCatching { startActivity(i) }.onFailure { startActivity(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                } else perms.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            },
            requestShizuku = { Privileged.requestShizuku() },
            requestDefaultSms = {
                if (Build.VERSION.SDK_INT >= 29) {
                    val rm = getSystemService(RoleManager::class.java)
                    role.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
                } else role.launch(Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName))
            },
            importVcf = {
                RestoreHolder.pendingVcf?.let { f ->
                    val u = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, "text/x-vcard").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        )

        setContent {
            StarCloudTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { StarRoot(vm, actions) }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); vm.tick++ }
}

@Composable
private fun StarRoot(vm: AppViewModel, act: Actions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    BackHandler(overlay != null) { overlay = null }

    when (overlay) {
        Overlay.APPS -> AppsPicker(vm) { overlay = null }
        Overlay.FILES -> FilesPicker(vm, act) { overlay = null }
        Overlay.DEST -> DestinationsScreen(vm, act) { overlay = null }
        null -> Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("الرئيسية" to Icons.Rounded.Home, "النسخ" to Icons.Rounded.Backup, "الاستعادة" to Icons.Rounded.Restore, "الإعدادات" to Icons.Rounded.Settings)
                        .forEachIndexed { i, (t, ic) ->
                            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, t) }, label = { Text(t) })
                        }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).consumeWindowInsets(pad)) {
                when (tab) {
                    0 -> HomeScreen(vm, act, goBackupTab = { tab = 1 }, goDestinations = { overlay = Overlay.DEST }, goRestoreTab = { tab = 2 })
                    1 -> BackupTab(vm, act, openApps = { overlay = Overlay.APPS }, openFiles = { overlay = Overlay.FILES }, openDest = { overlay = Overlay.DEST })
                    2 -> RestoreTab(vm, act)
                    else -> SettingsTab(vm, act)
                }
            }
        }
    }
}
