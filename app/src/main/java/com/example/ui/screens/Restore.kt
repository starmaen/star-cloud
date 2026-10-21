package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.Interval
import com.example.core.engine.Privileged
import com.example.core.formatBytes
import com.example.ui.Actions
import com.example.ui.AppViewModel
import com.example.ui.components.*

enum class RestorePage { MAIN, APPS, FILES }

@Composable
fun RestoreTab(vm: AppViewModel, act: Actions) {
    var page by remember { mutableStateOf(RestorePage.MAIN) }
    val run by vm.run.collectAsState()
    BackHandlerCompat(page != RestorePage.MAIN) { page = RestorePage.MAIN }

    when (page) {
        RestorePage.APPS -> RestoreApps(vm) { page = RestorePage.MAIN }
        RestorePage.FILES -> RestoreFiles(vm) { page = RestorePage.MAIN }
        RestorePage.MAIN -> ScreenScaffold("الاستعادة", null) { pad ->
            LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Spacer(Modifier.height(2.dp)) }
                if (run.running) item { RunCard(vm, run.isRestore) }
                if (!run.running && run.isRestore && run.finishedAt > 0) item {
                    Card1 {
                        Text(run.summary ?: "", fontWeight = FontWeight.Bold)
                        run.errors.take(6).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        if (com.example.core.engine.RestoreHolder.pendingVcf != null)
                            TextButton(onClick = act.importVcf) { Text("استيراد جهات الاتصال المستعادة") }
                    }
                }

                val enabled = vm.destinations.filter { it.enabled }
                if (enabled.isEmpty()) item { Notice("أضف وجهة فيها نسخة احتياطية أولاً (من تبويب النسخ ← أين تُحفظ النسخ).") }
                else {
                    item { SectionTitle("1) اختر مكان النسخة") }
                    items(enabled, key = { it.id }) { d ->
                        Card1(onClick = { vm.restoreChooseDest(d) }) {
                            IconRow(Icons.Rounded.Cloud, d.label, null, trailing = {
                                RadioButton(selected = vm.rDest?.id == d.id, onClick = { vm.restoreChooseDest(d) })
                            })
                        }
                    }
                }
                if (vm.rLoading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                vm.rError?.let { e -> item { Notice(e, error = true) } }

                val devices = vm.rDevices
                if (devices != null && devices.size > 1) {
                    item { SectionTitle("2) اختر الهاتف") }
                    items(devices, key = { it }) { dev ->
                        Card1(onClick = { vm.restoreChooseDevice(dev) }) {
                            IconRow(Icons.Rounded.Smartphone, dev, if (dev == vm.settings.deviceName) "هذا الهاتف" else "هاتف آخر",
                                trailing = { RadioButton(selected = vm.rDevice == dev, onClick = { vm.restoreChooseDevice(dev) }) })
                        }
                    }
                }

                if (vm.rDevice != null && !vm.rLoading) {
                    item { SectionTitle("3) اختر ما تريد استعادته") }
                    item {
                        Card1 {
                            IconRow(Icons.Rounded.Apps, "التطبيقات", if (vm.rApps.isEmpty()) "لا توجد تطبيقات في النسخة" else "${vm.rSelApps.size} مختار من ${vm.rApps.size}",
                                onClick = if (vm.rApps.isNotEmpty()) ({ page = RestorePage.APPS }) else null)
                            IconRow(Icons.Rounded.Folder, "الصور والملفات", if (vm.rNodes.isEmpty()) "تصفّح واختر" else "${vm.rNodes.size} مختار",
                                onClick = { page = RestorePage.FILES })
                            RestoreToggle(Icons.Rounded.Contacts, "جهات الاتصال", vm.rContacts, vm.rWantContacts) { vm.rWantContacts = it }
                            RestoreToggle(Icons.Rounded.Sms, "الرسائل", vm.rSms, vm.rWantSms) { vm.rWantSms = it }
                            RestoreToggle(Icons.Rounded.Call, "سجل المكالمات", vm.rCalls, vm.rWantCalls) { vm.rWantCalls = it }
                        }
                    }
                    item {
                        Card1 {
                            Text("خيارات", fontWeight = FontWeight.Bold)
                            IconRow(Icons.Rounded.Restore, "إلى أماكنها الأصلية", "وإلا تُحفظ في مجلد StarCloud-Restore", onClick = { vm.rToOriginal = !vm.rToOriginal }) {
                                Switch(checked = vm.rToOriginal, onCheckedChange = { vm.rToOriginal = it })
                            }
                            IconRow(Icons.Rounded.FileCopy, "استبدال الملفات الموجودة", "الملف المطابق يُتخطّى دائماً", onClick = { vm.rOverwrite = !vm.rOverwrite }) {
                                Switch(checked = vm.rOverwrite, onCheckedChange = { vm.rOverwrite = it })
                            }
                        }
                    }
                    if (vm.rWantSms && !vm.isDefaultSms()) item {
                        Notice("لكتابة الرسائل يشترط أندرويد أن يكون Star Cloud تطبيق الرسائل الافتراضي مؤقتاً. بعد الانتهاء أعد تطبيق رسائلك المعتاد من إعدادات التطبيقات الافتراضية.",
                            actionText = "جعله الافتراضي مؤقتاً", onAction = act.requestDefaultSms)
                    }
                    item {
                        val plan = vm.buildPlan()
                        val nothing = plan == null || (plan.apps.isEmpty() && plan.nodes.isEmpty() && plan.contactsFile == null && plan.smsFile == null && plan.callsFile == null)
                        BigButton("ابدأ الاستعادة", Icons.Rounded.Restore, enabled = !run.running && !nothing) { vm.buildPlan()?.let { vm.startRestore(it) } }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun RestoreToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, versions: List<com.example.core.RemoteEntry>, on: Boolean, set: (Boolean) -> Unit) {
    val has = versions.isNotEmpty()
    IconRow(icon, title, if (has) "أحدث نسخة: ${versions.first().name.substringAfter('_').substringBefore('.')}" else "غير موجود في النسخة",
        onClick = if (has) ({ set(!on) }) else null) { Switch(checked = on && has, enabled = has, onCheckedChange = set) }
}

@Composable
private fun RestoreApps(vm: AppViewModel, onBack: () -> Unit) {
    val canData = Privileged.canReadAppData(vm.priv)
    ScreenScaffold("تطبيقات النسخة", onBack, actions = {
        TextButton(onClick = { vm.rApps.forEach { if (it.pkg !in vm.rSelApps) vm.toggleRestoreApp(it.pkg) } }) { Text("تحديد الكل") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (!canData) item { Text("استعادة بيانات التطبيقات تحتاج Root أو Shizuku بصلاحية Root. بدونها يُثبَّت التطبيق فقط.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
            items(vm.rApps, key = { it.pkg }) { a ->
                val chosen = a.pkg in vm.rSelApps
                Row(Modifier.fillMaxWidth().clickable { vm.toggleRestoreApp(a.pkg) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconView(a.pkg, a.icon)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(displayLabel(a), fontWeight = FontWeight.Medium)
                        Text("${a.version.ifBlank { "—" }} • ${formatBytes(a.size)}" + if (a.hasData) " • فيه بيانات" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (chosen && a.hasData && canData) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = vm.rSelApps[a.pkg] == true, onCheckedChange = { vm.setRestoreAppData(a.pkg, it) })
                            Text("مع البيانات", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Checkbox(checked = chosen, onCheckedChange = { vm.toggleRestoreApp(a.pkg) })
                }
            }
        }
    }
}

@Composable
private fun RestoreFiles(vm: AppViewModel, onBack: () -> Unit) {
    val path = vm.rBrowsePath
    BackHandlerCompat(path.isNotEmpty()) { vm.browseRemote(path.dropLast(1)) }
    ScreenScaffold(if (path.isEmpty()) "ملفات النسخة" else path.last(), onBack = { if (path.isEmpty()) onBack() else vm.browseRemote(path.dropLast(1)) }) { pad ->
        Box(Modifier.padding(pad)) {
            if (vm.rLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else if (vm.rBrowse.isEmpty()) Empty("لا توجد ملفات في النسخة")
            else LazyColumn {
                items(vm.rBrowse, key = { it.name }) { e ->
                    val segs = path + e.name
                    val covered = vm.rNodes.any { n -> n.isDir && segs.size > n.segs.size && segs.take(n.segs.size) == n.segs }
                    Row(Modifier.fillMaxWidth().clickable { if (e.isDir) vm.browseRemote(segs) else vm.toggleNode(segs, false) }.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (e.isDir) Icons.Rounded.Folder else Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, maxLines = 1)
                            if (!e.isDir && e.size > 0) Text(formatBytes(e.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked = vm.rNodes.any { it.segs == segs } || covered, enabled = !covered, onCheckedChange = { vm.toggleNode(segs, e.isDir) })
                    }
                }
            }
        }
    }
}

@Composable
private fun displayLabel(a: com.example.core.engine.AppMeta): String {
    if (a.label != a.pkg) return a.label
    val pm = androidx.compose.ui.platform.LocalContext.current.packageManager
    return remember(a.pkg) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(a.pkg, 0)).toString() }.getOrNull()
            ?: a.pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
