package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.*
import com.example.core.data.Settings
import com.example.core.dest.WebDavDestination
import com.example.ui.Actions
import com.example.ui.AppViewModel
import com.example.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun DestinationsScreen(vm: AppViewModel, act: Actions, onBack: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<DestConfig?>(null) }
    LaunchedEffect(vm.tick) { vm.refreshInfo() }

    ScreenScaffold("أماكن حفظ النسخ", onBack) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Spacer(Modifier.height(2.dp)) }
            if (vm.destinations.isEmpty()) item {
                Text("يمكنك الحفظ في أكثر من مكان في نفس الوقت، مثلاً Google Drive وبطاقة SD.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            vm.destinations.forEach { d ->
                item(key = d.id) {
                    val info = vm.destInfo[d.id]
                    Card1 {
                        IconRow(
                            when (d.kind) { DestKind.WEBDAV -> Icons.Rounded.Dns; DestKind.DRIVE_API -> Icons.Rounded.CloudQueue; else -> Icons.Rounded.Folder },
                            d.label, d.kind.title, trailing = { Switch(checked = d.enabled, onCheckedChange = { vm.setDestEnabled(d.id, it) }) }
                        )
                        StorageBar("المساحة", info?.quota, extra = info?.let { "حجم نسخك: ${formatBytes(it.backupBytes)}" + (if (it.error != null && it.quota == null) " — ⚠ ${it.error}" else "") })
                        TextButton(onClick = { removing = d }) { Text("حذف الوجهة", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item { BigButton("إضافة مكان", Icons.Rounded.Add) { adding = true } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    removing?.let { d ->
        AlertDialog(onDismissRequest = { removing = null },
            title = { Text("حذف «${d.label}»؟") },
            text = { Text("تُزال الوجهة من التطبيق فقط. الملفات المحفوظة فيها لا تُحذف.") },
            confirmButton = { TextButton(onClick = { vm.removeDestination(d.id); removing = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("إلغاء") } })
    }
    if (adding) AddDestinationDialog(vm, act) { adding = false }
}

@Composable
private fun AddDestinationDialog(vm: AppViewModel, act: Actions, close: () -> Unit) {
    var page by remember { mutableStateOf(0) } // 0 قائمة، 1 WebDAV، 2 Drive مباشر
    AlertDialog(
        onDismissRequest = close,
        title = { Text(when (page) { 1 -> "خادم WebDAV / Nextcloud"; 2 -> "Google Drive مباشر"; else -> "أين تريد حفظ النسخ؟" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (page) {
                    0 -> {
                        IconRow(Icons.Rounded.Cloud, "Google Drive وتطبيقات السحابة", "الأسهل: Drive أو OneDrive أو Dropbox أو Mega",
                            onClick = { act.pickFolder(); close() })
                        IconRow(Icons.Rounded.Folder, "مجلد في الهاتف أو بطاقة SD", "نسخ محلي سريع", onClick = { act.pickFolder(); close() })
                        IconRow(Icons.Rounded.Dns, "خادم WebDAV / Nextcloud", "خادمك الخاص أو NAS", onClick = { page = 1 })
                        IconRow(Icons.Rounded.CloudQueue, "Google Drive مباشر (متقدم)", "يحتاج إعداداً في Google Cloud", onClick = { page = 2 })
                        Spacer(Modifier.height(6.dp))
                        Text("عند فتح المنتقي اختر الخدمة من القائمة الجانبية (☰) ثم افتح أو أنشئ مجلداً وأكّد بزر «استخدام هذا المجلد». لا حاجة لتسجيل دخول داخل التطبيق.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    1 -> WebDavForm(vm, close)
                    2 -> DriveDirect(vm, act, close)
                }
            }
        },
        confirmButton = { if (page != 0) TextButton(onClick = { page = 0 }) { Text("رجوع") } },
        dismissButton = { TextButton(onClick = close) { Text("إغلاق") } }
    )
}

@Composable
private fun WebDavForm(vm: AppViewModel, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("اسم للوجهة (مثال: Nextcloud)") }, singleLine = true)
        OutlinedTextField(url, { url = it }, label = { Text("رابط الخادم") }, singleLine = true,
            supportingText = { Text("Nextcloud: https://موقعك/remote.php/dav/files/اسم_المستخدم/") })
        OutlinedTextField(user, { user = it }, label = { Text("اسم المستخدم") }, singleLine = true)
        OutlinedTextField(pass, { pass = it }, label = { Text("كلمة المرور (يُفضّل كلمة مرور تطبيق)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation())
        if (url.startsWith("http://")) Text("⚠ الاتصال غير مشفّر، كلمة المرور قد تُرى على الشبكة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Button(enabled = !busy && url.length > 8 && user.isNotBlank(), onClick = {
            busy = true; status = "جارٍ الاختبار…"
            scope.launch {
                try {
                    val d = WebDavDestination("test", "test", url, user, pass)
                    d.prepare()
                    vm.addDestination(DestConfig(Settings.newId(), DestKind.WEBDAV, name.ifBlank { "WebDAV" }, true, url.trim(), user.trim(), pass))
                    close()
                } catch (e: Exception) { status = "❌ ${e.message}" }
                busy = false
            }
        }) { Text("اختبار وحفظ") }
    }
}

@Composable
private fun DriveDirect(vm: AppViewModel, act: Actions, close: () -> Unit) {
    val email = remember(vm.tick) { vm.google.email() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("هذه الطريقة تتصل بـ Google Drive مباشرة، وتحتاج مرة واحدة تسجيل التطبيق في Google Cloud Console:", style = MaterialTheme.typography.bodySmall)
        Text("1) أنشئ مشروعاً وفعّل «Google Drive API».\n2) أعدّ شاشة الموافقة (OAuth consent) وأضف بريدك مستخدماً تجريبياً.\n3) أنشئ «OAuth client ID» من نوع Android بهذه البيانات:",
            style = MaterialTheme.typography.bodySmall)
        Text("الحزمة: com.aistudio.starcloud.kxpqr", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text("SHA-1: ${vm.google.signingSha1() ?: "غير متاح"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text("بدون هذا تظهر رسالة الخطأ 10 (DEVELOPER_ERROR). إن أردت تجنّب كل هذا استخدم «Google Drive وتطبيقات السحابة» من القائمة السابقة.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (email != null) {
            Text("الحساب الحالي: $email")
            Button(onClick = {
                vm.addDestination(DestConfig(Settings.newId(), DestKind.DRIVE_API, "Drive — $email")); close()
            }) { Text("استخدام هذا الحساب") }
        }
        Button(onClick = act.signInGoogle) { Text(if (email == null) "تسجيل الدخول بحساب Google" else "تغيير الحساب") }
    }
}
