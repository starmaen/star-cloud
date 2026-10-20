package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.core.Interval
import com.example.core.engine.Privileged
import com.example.ui.Actions
import com.example.ui.AppViewModel
import com.example.ui.components.*

@Composable
fun SettingsTab(vm: AppViewModel, act: Actions) {
    val p = vm.prefs
    var intervalMenu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf(false) }
    var logsDialog by remember { mutableStateOf(false) }
    LaunchedEffect(vm.tick) { vm.refreshInfo() }

    ScreenScaffold("الإعدادات", null) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Spacer(Modifier.height(2.dp)) }

            item {
                Card1 {
                    Text("النسخ التلقائي", style = MaterialTheme.typography.titleSmall)
                    IconRow(Icons.Rounded.Schedule, "كم مرة؟", p.interval.title, onClick = { intervalMenu = true }) {
                        DropdownMenu(expanded = intervalMenu, onDismissRequest = { intervalMenu = false }) {
                            Interval.values().forEach { i -> DropdownMenuItem(text = { Text(i.title) }, onClick = { vm.savePrefs { interval = i }; intervalMenu = false }) }
                        }
                    }
                    IconRow(Icons.Rounded.Wifi, "عبر Wi-Fi فقط", "لتوفير باقة الإنترنت", onClick = { vm.savePrefs { wifiOnly = !p.wifiOnly } }) {
                        Switch(checked = p.wifiOnly, onCheckedChange = { vm.savePrefs { wifiOnly = it } })
                    }
                    IconRow(Icons.Rounded.BatteryChargingFull, "أثناء الشحن فقط", null, onClick = { vm.savePrefs { chargingOnly = !p.chargingOnly } }) {
                        Switch(checked = p.chargingOnly, onCheckedChange = { vm.savePrefs { chargingOnly = it } })
                    }
                }
            }

            item {
                Card1 {
                    Text("الاحتفاظ بالنسخ", style = MaterialTheme.typography.titleSmall)
                    IconRow(Icons.Rounded.Layers, "الاحتفاظ بما حذفته من الهاتف",
                        if (p.keepDeleted) "الملفات المحذوفة تبقى في النسخة (تراكمي)" else "النسخة تطابق الهاتف تماماً",
                        onClick = { vm.savePrefs { keepDeleted = !p.keepDeleted } }) {
                        Switch(checked = p.keepDeleted, onCheckedChange = { vm.savePrefs { keepDeleted = it } })
                    }
                    IconRow(Icons.Rounded.History, "عدد نسخ جهات الاتصال والرسائل", "تُحفظ آخر ${p.keepVersions} نسخ") {
                        Row {
                            TextButton(onClick = { vm.savePrefs { keepVersions = (p.keepVersions - 1).coerceAtLeast(1) } }) { Text("−") }
                            TextButton(onClick = { vm.savePrefs { keepVersions = (p.keepVersions + 1).coerceAtMost(30) } }) { Text("+") }
                        }
                    }
                    IconRow(Icons.Rounded.Description, "تجاهل الملفات الكبيرة",
                        if (p.maxFileMb == 0) "لا حد" else "أكبر من ${if (p.maxFileMb >= 1024) "${p.maxFileMb / 1024} GB" else "${p.maxFileMb} MB"}",
                        onClick = { sizeMenu = true }) {
                        DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                            listOf(0 to "بلا حد", 100 to "100 MB", 500 to "500 MB", 1024 to "1 GB", 4096 to "4 GB").forEach { (mb, t) ->
                                DropdownMenuItem(text = { Text(t) }, onClick = { vm.savePrefs { maxFileMb = mb }; sizeMenu = false })
                            }
                        }
                    }
                }
            }

            item {
                Card1 {
                    Text("صلاحيات متقدمة", style = MaterialTheme.typography.titleSmall)
                    IconRow(Icons.Rounded.Shield, "Root / Shizuku", Privileged.describe(vm.priv, Privileged.shizukuRunning()))
                    if (Privileged.shizukuRunning() && !Privileged.shizukuGranted())
                        TextButton(onClick = act.requestShizuku) { Text("منح صلاحية Shizuku") }
                    TextButton(onClick = { vm.refreshInfo() }) { Text("إعادة الفحص") }
                    Text("مع Root أو Shizuku يمكن نسخ واستعادة بيانات التطبيقات وتثبيتها بصمت.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Card1 {
                    Text("هذا الهاتف", style = MaterialTheme.typography.titleSmall)
                    IconRow(Icons.Rounded.Smartphone, "اسم الهاتف في النسخة", p.deviceName, onClick = { nameDialog = true })
                    IconRow(Icons.Rounded.Refresh, "إعادة رفع كل شيء", "في النسخة القادمة يُفحص كل الملفات من جديد", onClick = { vm.forceFullBackup() })
                    IconRow(Icons.Rounded.History, "سجل العمليات", "${vm.logs.size} عملية", onClick = { logsDialog = true })
                }
            }

            item {
                Card1 {
                    Text("حول التطبيق", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Star Cloud ${BuildConfig.VERSION_NAME}")
                    Text("تطوير: Star Syria", style = MaterialTheme.typography.bodyMedium)
                    Text("starsyria2500@gmail.com", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    vm.message?.let { m ->
        AlertDialog(onDismissRequest = { vm.message = null }, text = { Text(m) }, confirmButton = { TextButton(onClick = { vm.message = null }) { Text("حسناً") } })
    }
    if (nameDialog) {
        var t by remember { mutableStateOf(p.deviceName) }
        AlertDialog(onDismissRequest = { nameDialog = false }, title = { Text("اسم الهاتف") },
            text = { Column { OutlinedTextField(t, { t = it }, singleLine = true)
                Text("تتجمّع نسخ هذا الهاتف تحت هذا الاسم. غيّره قبل أول نسخة.", style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton(onClick = { vm.savePrefs { deviceName = t }; nameDialog = false }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { nameDialog = false }) { Text("إلغاء") } })
    }
    if (logsDialog) {
        AlertDialog(onDismissRequest = { logsDialog = false }, title = { Text("آخر العمليات") },
            text = {
                LazyColumn { items(vm.logs.size) { i ->
                    val l = vm.logs[i]
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text((if (l.ok) "✅ " else "⚠ ") + (if (l.kind == "backup") "نسخ" else "استعادة") + " — " + timeText(l.time), style = MaterialTheme.typography.bodyMedium)
                        Text(l.summary, style = MaterialTheme.typography.bodySmall)
                    }
                } }
            },
            confirmButton = { TextButton(onClick = { logsDialog = false }) { Text("إغلاق") } })
    }
}
