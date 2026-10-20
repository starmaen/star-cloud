package com.example.ui.screens

import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.core.engine.Privileged
import com.example.core.engine.PrivMode
import com.example.core.formatBytes
import com.example.ui.Actions
import com.example.ui.AppViewModel
import com.example.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** تبويب «النسخ»: اختر ما تريد نسخه. */
@Composable
fun BackupTab(vm: AppViewModel, act: Actions, openApps: () -> Unit, openFiles: () -> Unit, openDest: () -> Unit) {
    val s = vm.selection
    ScreenScaffold("ماذا تريد أن تنسخ؟", null) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Spacer(Modifier.height(2.dp)) }
            item {
                Card1(onClick = openApps) {
                    IconRow(Icons.Rounded.Apps, "التطبيقات",
                        if (s.apps.isEmpty()) "لم تختر أي تطبيق" else "${s.apps.size} مختار" + (s.apps.count { it.withData }.takeIf { it > 0 }?.let { " • $it مع البيانات" } ?: ""))
                }
            }
            item {
                Card1(onClick = openFiles) {
                    IconRow(Icons.Rounded.Folder, "الصور والملفات والمجلدات",
                        if (s.paths.isEmpty()) "لم تختر أي مجلد" else "${s.paths.size} مجلد/ملف مختار")
                }
            }
            item {
                Card1 {
                    ToggleRow(Icons.Rounded.Contacts, "جهات الاتصال", "بكل التفاصيل والصور", s.contacts) { on ->
                        vm.updateSelection { it.copy(contacts = on) }; if (on && !vm.hasContacts()) act.askContacts()
                    }
                    ToggleRow(Icons.Rounded.Sms, "الرسائل النصية", "كل الرسائل الواردة والصادرة", s.sms) { on ->
                        vm.updateSelection { it.copy(sms = on) }; if (on && !vm.hasSms()) act.askSms()
                    }
                    ToggleRow(Icons.Rounded.Call, "سجل المكالمات", null, s.calls) { on ->
                        vm.updateSelection { it.copy(calls = on) }; if (on && !vm.hasCalls()) act.askCalls()
                    }
                }
            }
            item {
                val n = vm.destinations.count { it.enabled }
                Card1(onClick = openDest) {
                    IconRow(Icons.Rounded.Cloud, "أين تُحفظ النسخ؟",
                        if (n == 0) "لم تضف وجهة بعد" else vm.destinations.filter { it.enabled }.joinToString("، ") { it.label })
                }
            }
            item {
                val run by vm.run.collectAsState()
                BigButton("ابدأ النسخ الآن", Icons.Rounded.CloudUpload, enabled = !run.running) { vm.startBackup() }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ToggleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String?, on: Boolean, set: (Boolean) -> Unit) {
    IconRow(icon, title, sub, onClick = { set(!on) }) { Switch(checked = on, onCheckedChange = set) }
}

// ======================= التطبيقات =======================
@Composable
fun AppsPicker(vm: AppViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadApps() }
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) } // 0 تطبيقاتي 1 النظام 2 المختارة
    val apps = vm.apps
    val sel = vm.selection.apps.associateBy { it.pkg }
    val canData = Privileged.canReadAppData(vm.priv)

    val shown = remember(apps, q, filter, vm.selection) {
        (apps ?: emptyList()).filter {
            (q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true)) &&
                when (filter) { 0 -> !it.isSystem; 1 -> it.isSystem; else -> it.pkg in sel }
        }
    }

    ScreenScaffold("التطبيقات", onBack, actions = {
        TextButton(onClick = { vm.setApps(shown.map { it.pkg }, true) }) { Text("تحديد الكل") }
        TextButton(onClick = { vm.setApps(shown.map { it.pkg }, false) }) { Text("إلغاء") }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("بحث عن تطبيق") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("تطبيقاتي", "تطبيقات النظام", "المختارة (${sel.size})").forEachIndexed { i, t ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(t) })
                }
            }
            if (!canData) Text(
                "نسخ بيانات التطبيقات (الحسابات والإعدادات والألعاب) يحتاج Root أو Shizuku بصلاحية Root. بدونهما يُنسخ التطبيق نفسه فقط.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            if (apps == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.pkg }) { a ->
                    val choice = sel[a.pkg]
                    Row(Modifier.fillMaxWidth().clickable { vm.toggleApp(a.pkg) }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a.pkg)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text("${a.version.ifBlank { "—" }} • ${formatBytes(a.apkBytes)}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (choice != null && canData) Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = choice.withData, onCheckedChange = { vm.setAppData(a.pkg, it) })
                                Text("مع البيانات", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Checkbox(checked = choice != null, onCheckedChange = { vm.toggleApp(a.pkg) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize()) } ?: Icon(Icons.Rounded.Android, null)
    }
}

// ======================= الملفات =======================
private data class Preset(val title: String, val path: String)

@Composable
fun FilesPicker(vm: AppViewModel, act: Actions, onBack: () -> Unit) {
    val roots = remember { vm.storageRoots() }
    var dir by remember { mutableStateOf<File?>(null) }
    val sel = vm.selection.paths
    val ext = Environment.getExternalStorageDirectory().path
    val presets = remember {
        listOf(
            Preset("الكاميرا", "$ext/DCIM"), Preset("الصور", "$ext/Pictures"),
            Preset("لقطات الشاشة", "$ext/Pictures/Screenshots"), Preset("الفيديو", "$ext/Movies"),
            Preset("الموسيقى", "$ext/Music"), Preset("المستندات", "$ext/Documents"),
            Preset("التنزيلات", "$ext/Download"),
            Preset("واتساب", "$ext/Android/media/com.whatsapp/WhatsApp"),
            Preset("واتساب (قديم)", "$ext/WhatsApp")
        ).filter { File(it.path).exists() }
    }

    BackHandlerCompat(dir != null) { dir = dir?.parentFile?.takeIf { p -> roots.any { r -> p.path.startsWith(r.path) } } }

    ScreenScaffold(dir?.let { if (roots.any { r -> r.path == it.path }) "التخزين" else it.name } ?: "الصور والملفات", onBack = { if (dir == null) onBack() else dir = dir?.parentFile?.takeIf { p -> roots.any { r -> p.path.startsWith(r.path) } } }) { pad ->
        if (!vm.hasAllFiles()) {
            Column(Modifier.padding(pad).padding(16.dp)) {
                Notice("لعرض ملفاتك ونسخها يلزم إذن «الوصول لكل الملفات».", actionText = "منح الإذن", onAction = act.openAllFiles)
            }
            return@ScreenScaffold
        }
        Column(Modifier.padding(pad)) {
            if (dir == null) {
                Text("اختيار سريع", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        FilterChip(selected = p.path in sel, onClick = { vm.togglePath(p.path) }, label = { Text(p.title) })
                    }
                }
                HorizontalDivider()
                Text("أو تصفّح واختر بنفسك", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
                roots.forEach { r ->
                    IconRow(Icons.Rounded.Storage, if (r.path == ext) "ذاكرة الهاتف" else "بطاقة SD (${r.name})", r.path,
                        onClick = { dir = r }, trailing = { Icon(Icons.Rounded.ChevronLeft, null) })
                }
                if (sel.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("المختار (${sel.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp))
                    LazyColumn(Modifier.weight(1f)) { items(sel, key = { it }) { p ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { vm.togglePath(p) }) { Icon(Icons.Rounded.Close, "إزالة") }
                        }
                    } }
                }
            } else {
                val cur = dir!!
                val children = remember(cur) {
                    cur.listFiles().orEmpty()
                        .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
                }
                val parentSelected = sel.any { cur.path == it || cur.path.startsWith("$it/") }
                if (parentSelected) Text("هذا المجلد داخل مجلد محدد مسبقاً وسيُنسخ معه.", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 8.dp))
                if (children.isEmpty()) Empty(if (cur.canRead()) "المجلد فارغ" else "لا يمكن قراءة هذا المجلد بدون Root")
                LazyColumn(Modifier.weight(1f)) {
                    items(children, key = { it.path }) { f ->
                        val covered = sel.any { f.path.startsWith("$it/") }
                        Row(Modifier.fillMaxWidth().clickable { if (f.isDirectory) dir = f else vm.togglePath(f.path) }.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (f.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description, null,
                                tint = if (f.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, maxLines = 1)
                                if (f.isFile) Text(formatBytes(f.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(checked = f.path in sel || covered, enabled = !covered, onCheckedChange = { vm.togglePath(f.path) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) { androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack) }
