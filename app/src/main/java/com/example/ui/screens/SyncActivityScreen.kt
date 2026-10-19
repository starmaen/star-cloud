package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SyncLogEntity
import com.example.ui.StarCloudViewModel
import com.example.ui.theme.SyncError
import com.example.ui.theme.SyncSuccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncActivityScreen(
    viewModel: StarCloudViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.syncLogs.collectAsState()
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "SUCCESS" -> logs.filter { it.status == "SUCCESS" }
            "FAILED" -> logs.filter { it.status != "SUCCESS" }
            else -> logs
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "سجلات المزامنة والنشاط",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "سجل مفصل للعمليات السحابية المنفذة محليًا",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (logs.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearAllLogs() },
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "مسح السجلات",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("جميع العمليات (${logs.size})") }
                )
                FilterChip(
                    selected = selectedFilter == "SUCCESS",
                    onClick = { selectedFilter = "SUCCESS" },
                    label = { Text("الناجحة (${logs.count { it.status == "SUCCESS" }})") }
                )
                FilterChip(
                    selected = selectedFilter == "FAILED",
                    onClick = { selectedFilter = "FAILED" },
                    label = { Text("المتعثرة (${logs.count { it.status != "SUCCESS" }})") }
                )
            }
        }

        if (filteredLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا توجد سجلات مطابقة للفلتر المحدد",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ستظهر هنا تفاصيل كل عملية مزامنة مجدولة أو يدوية",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredLogs) { log ->
                SyncLogItemCard(log = log)
            }
        }
    }
}

@Composable
private fun SyncLogItemCard(log: SyncLogEntity) {
    val isSuccess = log.status == "SUCCESS"
    val sdf = remember { SimpleDateFormat("yyyy/MM/dd • HH:mm:ss", Locale.getDefault()) }
    val dateStr = remember(log.timestamp) { sdf.format(Date(log.timestamp)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSuccess) SyncSuccess.copy(alpha = 0.15f)
                            else SyncError.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isSuccess) SyncSuccess else SyncError,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSuccess) "عملية مزامنة ناجحة" else "تنبيه أثناء المزامنة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = if (isSuccess) SyncSuccess.copy(alpha = 0.15f) else SyncError.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = log.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSuccess) SyncSuccess else SyncError,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = log.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "العناصر: ${log.itemsSynced}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "الحجم: ${formatBytes(log.totalSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "المدة: ${log.durationSeconds} ثانية",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format("%.2f GB", mb / 1024.0)
    } else {
        String.format("%.1f MB", mb)
    }
}
