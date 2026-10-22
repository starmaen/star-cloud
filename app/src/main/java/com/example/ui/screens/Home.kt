package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.formatBytes
import com.example.ui.Actions
import com.example.ui.AppViewModel
import com.example.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun timeText(t: Long): String = SimpleDateFormat("EEEE d MMMM • HH:mm", Locale("ar")).format(Date(t))

@Composable
fun HomeScreen(vm: AppViewModel, act: Actions, goBackupTab: () -> Unit, goDestinations: () -> Unit, goRestoreTab: () -> Unit) {
    val run by vm.run.collectAsState()
    LaunchedEffect(vm.tick) { vm.refreshInfo() }
    ScreenScaffold("Star Cloud", null) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Spacer(Modifier.height(4.dp)) }

            if (!vm.hasAllFiles()) item {
                Notice("لنسخ ملفاتك وصورك يحتاج التطبيق إذن «الوصول لكل الملفات».", actionText = "منح الإذن", onAction = act.openAllFiles)
            }
            if (vm.destinations.none { it.enabled }) item {
                Notice("لم تحدد مكان حفظ النسخ بعد. أضف وجهة (Google Drive أو بطاقة أو خادم).", actionText = "إضافة وجهة", onAction = goDestinations)
            }

            item {
                if (run.running) RunCard(vm, run.isRestore) else {
                    Card1 {
                        Text("آخر نسخة ناجحة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(vm.lastBackup?.let { timeText(it.time) } ?: "لم تُجرَ أي نسخة بعد",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        run.summary?.takeIf { run.finishedAt > 0 }?.let {
                            Spacer(Modifier.height(4.dp)); Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(14.dp))
                        BigButton("ابدأ النسخ الآن", Icons.Rounded.CloudUpload) { if (vm.startBackup()) { } }
                    }
                }
            }

            if (!run.running && run.errors.isNotEmpty()) item {
                Card1 {
                    Text("أخطاء آخر عملية", fontWeight = FontWeight.Bold)
                    run.errors.take(8).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (run.errors.size > 8) Text("… و${run.errors.size - 8} أخرى", style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                Card1 {
                    Text("ما سيُنسخ", fontWeight = FontWeight.Bold)
                    val s = vm.selection
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (s.isEmpty) "لم تختر شيئاً بعد" else listOfNotNull(
                            if (s.apps.isNotEmpty()) "${s.apps.size} تطبيق" else null,
                            if (s.paths.isNotEmpty()) "${s.paths.size} مجلد/ملف" else null,
                            if (s.contacts) "جهات الاتصال" else null,
                            if (s.sms) "الرسائل" else null,
                            if (s.calls) "المكالمات" else null
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = goBackupTab) { Text("تعديل الاختيار") }
                }
            }

            item {
                Card1 {
                    Text("التخزين", fontWeight = FontWeight.Bold)
                    val vols = vm.deviceVolumes
                    if (vols.isEmpty()) {
                        StorageBar("ذاكرة الهاتف", vm.deviceStorage)
                    } else {
                        vols.forEachIndexed { i, v ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            StorageBar(v.label, v.quota)
                        }
                    }
                    vm.destinations.filter { it.enabled }.forEach { d ->
                        val info = vm.destInfo[d.id]
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        StorageBar(
                            d.label, info?.quota,
                            extra = when {
                                info == null -> "جارٍ القراءة…"
                                else -> "حجم نسخك هنا: ${formatBytes(info.backupBytes)} (${info.files} ملف)" +
                                    (if (info.error != null && info.quota == null) "\n⚠ ${info.error}" else "")
                            }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun RunCard(vm: AppViewModel, restore: Boolean) {
    val s by vm.run.collectAsState()
    Card1 {
        Text(if (restore) "جارٍ الاستعادة…" else "جارٍ النسخ الاحتياطي…", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(s.phase, style = MaterialTheme.typography.bodyMedium)
        if (s.current.isNotBlank()) Text(s.current, style = MaterialTheme.typography.bodySmall, maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        if (s.totalItems > 0 || s.totalBytes > 0) LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth().height(8.dp))
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (s.totalItems > 0) "${s.doneItems} / ${s.totalItems}" else "${s.doneItems} عنصر", style = MaterialTheme.typography.bodySmall)
            Text("${formatBytes(s.doneBytes)}  •  ${formatBytes(s.speedBps)}/ث", style = MaterialTheme.typography.bodySmall)
        }
        if (s.errors.isNotEmpty()) Text("⚠ ${s.errors.size} أخطاء حتى الآن", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.cancelRun() }, modifier = Modifier.fillMaxWidth()) { Text("إيقاف") }
    }
}
