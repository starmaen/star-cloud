package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SyncDirection
import com.example.ui.ScreenTab
import com.example.ui.StarCloudViewModel
import com.example.ui.components.LiveSyncBanner
import com.example.ui.components.StorageMetricsCard
import com.example.ui.theme.CosmicCyan
import com.example.ui.theme.StarBluePrimary
import com.example.ui.theme.StarlightGold
import com.example.ui.theme.SyncSuccess

@Composable
fun DashboardScreen(
    viewModel: StarCloudViewModel,
    modifier: Modifier = Modifier
) {
    val deviceStorage by viewModel.deviceStorage.collectAsState()
    val cloudQuota by viewModel.cloudQuota.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val syncDirection by viewModel.syncDirection.collectAsState()
    val folders by viewModel.folderConfigs.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val waCategories by viewModel.whatsAppCategories.collectAsState()
    val apps by viewModel.installedApps.collectAsState()
    val recentLogs by viewModel.syncLogs.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("star_cloud_hero_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(listOf(StarBluePrimary, CosmicCyan))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Star Cloud Logo",
                                        tint = StarlightGold,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Star Cloud",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "المزامنة الشاملة والانتقائية للسحابة",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { viewModel.showProposal(true) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("view_proposal_button")
                            ) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("العرض الفني", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            StorageMetricsCard(
                deviceStorage = deviceStorage,
                cloudQuota = cloudQuota
            )
        }

        item {
            LiveSyncBanner(
                state = syncProgress,
                onStartSync = { viewModel.startSyncNow() },
                onCancelSync = { viewModel.cancelSync() }
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "طريقة واتجاه المزامنة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SyncDirection.values().forEach { dir ->
                            val isSelected = dir == syncDirection
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setSyncDirection(dir) },
                                label = {
                                    Text(
                                        text = when (dir) {
                                            SyncDirection.BACKUP -> "نسخ احتياطي ➔"
                                            SyncDirection.RESTORE -> "➔ استعادة"
                                            SyncDirection.TWO_WAY -> "ثنائي ⇋"
                                        },
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ملخص المزامنة الانتقائية",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = { viewModel.selectTab(ScreenTab.SELECTIVE_SYNC) },
                    modifier = Modifier.testTag("manage_selective_sync_button")
                ) {
                    Text("تخصيص وتعديل")
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CategoryMiniCard(
                        title = "مجلدات SAF",
                        countText = "${folders.size} مجلدات",
                        icon = Icons.Default.Folder,
                        iconColor = CosmicCyan,
                        onClick = { viewModel.selectTab(ScreenTab.SELECTIVE_SYNC) },
                        modifier = Modifier.weight(1f)
                    )

                    CategoryMiniCard(
                        title = "جهات الاتصال",
                        countText = "${contacts.count { it.isSelected }} جهة",
                        icon = Icons.Default.Contacts,
                        iconColor = StarBluePrimary,
                        onClick = { viewModel.selectTab(ScreenTab.SELECTIVE_SYNC) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CategoryMiniCard(
                        title = "وسائط واتساب",
                        countText = "${waCategories.count { it.isSelected }} فئات",
                        icon = Icons.Default.Chat,
                        iconColor = SyncSuccess,
                        onClick = { viewModel.selectTab(ScreenTab.SELECTIVE_SYNC) },
                        modifier = Modifier.weight(1f)
                    )

                    CategoryMiniCard(
                        title = "تطبيقات (APK)",
                        countText = "${apps.count { it.isSelected }} تطبيق",
                        icon = Icons.Default.Android,
                        iconColor = StarlightGold,
                        onClick = { viewModel.selectTab(ScreenTab.SELECTIVE_SYNC) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "آخر العمليات السحابية",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = { viewModel.selectTab(ScreenTab.SYNC_LOGS) }
                ) {
                    Text("عرض الكل (${recentLogs.size})")
                }
            }
        }

        if (recentLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لا توجد عمليات مزامنة سابقة بعد",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "اضغط على زر 'مزامنة الآن' لبدء النسخ الاحتياطي الأول",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(recentLogs.take(3).size) { idx ->
                val log = recentLogs[idx]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (log.status == "SUCCESS") SyncSuccess.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (log.status == "SUCCESS") Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (log.status == "SUCCESS") SyncSuccess else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = "المزود: ${log.providerName} • المدة: ${log.durationSeconds} ثانية",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryMiniCard(
    title: String,
    countText: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
