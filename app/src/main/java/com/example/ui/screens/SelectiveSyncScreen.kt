package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.FolderConfigEntity
import com.example.data.model.ConflictPolicy
import com.example.data.model.ContactItem
import com.example.data.model.InstalledAppItem
import com.example.data.model.WhatsAppMediaCategory
import com.example.ui.StarCloudViewModel
import com.example.ui.theme.CosmicCyan
import com.example.ui.theme.StarBluePrimary
import com.example.ui.theme.StarlightGold
import com.example.ui.theme.SyncSuccess

enum class SelectiveSubTab(val titleAr: String, val icon: ImageVector) {
    FOLDERS("المجلدات والملفات (SAF)", Icons.Default.Folder),
    CONTACTS("جهات الاتصال", Icons.Default.Contacts),
    MESSAGES("الرسائل والمكالمات", Icons.Default.Sms),
    WHATSAPP("وسائط واتساب", Icons.Default.Chat),
    APPS("تطبيقات (APK)", Icons.Default.Android),
    FILTERS("المرشحات والقيود", Icons.Default.FilterList)
}

@Composable
fun SelectiveSyncScreen(
    viewModel: StarCloudViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSubTab by remember { mutableStateOf(SelectiveSubTab.FOLDERS) }

    val folders by viewModel.folderConfigs.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val smsList by viewModel.smsList.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    val waCategories by viewModel.whatsAppCategories.collectAsState()
    val apps by viewModel.installedApps.collectAsState()
    val appSearchQuery by viewModel.appSearchQuery.collectAsState()
    val maxFileSizeMb by viewModel.maxFileSizeMb.collectAsState()
    val conflictPolicy by viewModel.conflictPolicy.collectAsState()

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            }
            val folderName = it.lastPathSegment?.substringAfterLast(':') ?: "مجلد جديد"
            viewModel.addFolderConfigFromSaf(it, folderName)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 12.dp)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SelectiveSubTab.values()) { tab ->
                val isSelected = tab == selectedSubTab
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedSubTab = tab },
                    leadingIcon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = {
                        Text(
                            text = tab.titleAr,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (selectedSubTab) {
            SelectiveSubTab.FOLDERS -> {
                FoldersSubTab(
                    folders = folders,
                    onPickFolder = { openDocumentTreeLauncher.launch(null) },
                    onToggleFolder = { viewModel.toggleFolderEnabled(it) },
                    onRemoveFolder = { viewModel.removeFolderConfig(it.id) }
                )
            }
            SelectiveSubTab.CONTACTS -> {
                ContactsSubTab(
                    contacts = contacts,
                    onToggleContact = { viewModel.toggleContact(it) },
                    onSelectAll = { viewModel.selectAllContacts(it) }
                )
            }
            SelectiveSubTab.MESSAGES -> {
                MessagesSubTab(
                    smsList = smsList,
                    callLogs = callLogs
                )
            }
            SelectiveSubTab.WHATSAPP -> {
                WhatsAppSubTab(
                    categories = waCategories,
                    onToggleCategory = { viewModel.toggleWhatsAppCategory(it) }
                )
            }
            SelectiveSubTab.APPS -> {
                AppsSubTab(
                    apps = apps,
                    searchQuery = appSearchQuery,
                    onSearchQueryChange = { viewModel.setAppSearchQuery(it) },
                    onToggleApp = { viewModel.toggleInstalledApp(it) },
                    onSelectAll = { viewModel.selectAllApps(it) }
                )
            }
            SelectiveSubTab.FILTERS -> {
                FiltersSubTab(
                    maxFileSizeMb = maxFileSizeMb,
                    conflictPolicy = conflictPolicy,
                    onMaxFileSizeChange = { viewModel.setMaxFileSize(it) },
                    onConflictPolicyChange = { viewModel.setConflictPolicy(it) }
                )
            }
        }
    }
}

@Composable
private fun FoldersSubTab(
    folders: List<FolderConfigEntity>,
    onPickFolder: () -> Unit,
    onToggleFolder: (FolderConfigEntity) -> Unit,
    onRemoveFolder: (FolderConfigEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "نظام Scoped Storage في أندرويد 10+",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "يتم الوصول للمجلدات عبر منتقي SAF الرسمي والاحتفاظ بصلاحية الوصول الدائمة (takePersistableUriPermission).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onPickFolder,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_saf_folder_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إضافة مجلد جديد للمزامنة (SAF)", fontWeight = FontWeight.Bold)
            }
        }

        if (folders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "لم تتم إضافة أي مجلدات بعد",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "اختر مجلد المستندات، الصور، أو التنزيلات لمزامنته تلقائيًا",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(folders) { folder ->
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CosmicCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = CosmicCyan)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "تصريح SAF دائم • مشمول في المزامنة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = folder.isEnabled,
                            onCheckedChange = { onToggleFolder(folder) }
                        )

                        IconButton(onClick = { onRemoveFolder(folder) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "حذف المجلد",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsSubTab(
    contacts: List<ContactItem>,
    onToggleContact: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = contacts.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.phoneNumber.contains(searchQuery)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "جهات الاتصال (${contacts.count { it.isSelected }}/${contacts.size} محددة)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    TextButton(onClick = { onSelectAll(true) }) { Text("تحديد الكل") }
                    TextButton(onClick = { onSelectAll(false) }) { Text("إلغاء الكل") }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("بحث في جهات الاتصال...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
        }

        items(filtered) { contact ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleContact(contact.id) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = contact.isSelected,
                        onCheckedChange = { onToggleContact(contact.id) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = contact.phoneNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "vCard",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagesSubTab(
    smsList: List<com.example.data.model.SmsLogItem>,
    callLogs: List<com.example.data.model.CallLogItem>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "النسخ الاحتياطي للرسائل وسجل المكالمات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "يتم حفظ الرسائل وسجلات المكالمات في ملفات JSON منظمة ومشفرة لسهولة الاستعادة على أي جهاز.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text(
                text = "الرسائل النصية SMS (${smsList.size} رسالة جاهزة)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(smsList) { sms ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = sms.address,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (sms.type == 1) "واردة" else "صادرة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = sms.body,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "سجل المكالمات (${callLogs.size} مكالمة جاهزة)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(callLogs) { call ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = call.name ?: call.number,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${call.number} • المدة: ${call.durationSeconds} ثانية",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatsAppSubTab(
    categories: List<WhatsAppMediaCategory>,
    onToggleCategory: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SyncSuccess.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = SyncSuccess,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "مزامنة وسائط WhatsApp و WhatsApp Business",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "اختر أنواع الوسائط المراد نسخها احتياطيًا. قد يمنع Android الحديث قراءة مجلد واتساب مباشرة؛ أضف المجلد من تبويب (المجلدات) لرفع الوسائط الفعلية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(categories) { cat ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCategory(cat.id) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = cat.isSelected,
                        onCheckedChange = { onToggleCategory(cat.id) }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cat.nameAr,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "المتاح: ${cat.estimatedCount} ملف • الحجم: ${formatMb(cat.estimatedSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsSubTab(
    apps: List<InstalledAppItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleApp: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit
) {
    val filtered = apps.filter {
        it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تطبيقات الجهاز (${apps.count { it.isSelected }}/${apps.size} محددة)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    TextButton(onClick = { onSelectAll(true) }) { Text("تحديد الكل") }
                    TextButton(onClick = { onSelectAll(false) }) { Text("إلغاء الكل") }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("بحث عن تطبيق بالاسم أو الحزمة...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
        }

        items(filtered) { app ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleApp(app.packageName) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = app.isSelected,
                        onCheckedChange = { onToggleApp(app.packageName) }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(StarlightGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = StarlightGold)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "الإصدار: ${app.versionName} • الحجم: ${formatMb(app.apkSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltersSubTab(
    maxFileSizeMb: Int,
    conflictPolicy: ConflictPolicy,
    onMaxFileSizeChange: (Int) -> Unit,
    onConflictPolicyChange: (ConflictPolicy) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "بيانات التطبيقات وقيود نظام أندرويد (App Data)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "وفقًا لمعمارية أمان أندرويد 10+ (Application Sandbox)، فإن مجلدات البيانات الخاصة بالبرامج (/data/data/<package_name>) محمية بنظام SELinux ومحجوبة عن التطبيقات الأخرى بدون صلاحية Root.\n\n" +
                                "✓ ما ينسخه Star Cloud بالكامل:\n" +
                                "• حزم التثبيت الأصلية (APK) المستخرجة من النظام.\n" +
                                "• ملفات التطبيقات والبيانات التابعة لها في مساحات التخزين المشتركة وSAF.\n" +
                                "• قواعد البيانات والوسائط المصدرة يدويًا أو عبر النسخ التلقائي.\n\n" +
                                "⚠️ بيانات الجلسات الداخلية المحمية تظل محمية لمنع اختراق خصوصية المستخدمين.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "الحد الأقصى لحجم الملف للمزامنة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (maxFileSizeMb == 0) "بدون حد (مزامنة أي حجم ملف)" else "$maxFileSizeMb ميغابايت (تجاهل الملفات الأكبر)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = maxFileSizeMb.toFloat(),
                        onValueChange = { onMaxFileSizeChange(it.toInt()) },
                        valueRange = 0f..500f,
                        steps = 9
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("بلا حد", style = MaterialTheme.typography.labelSmall)
                        Text("100 MB", style = MaterialTheme.typography.labelSmall)
                        Text("250 MB", style = MaterialTheme.typography.labelSmall)
                        Text("500 MB", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "سياسة حل النزاعات (Conflict Resolution)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    ConflictPolicy.values().forEach { policy ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConflictPolicyChange(policy) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = policy == conflictPolicy,
                                onClick = { onConflictPolicyChange(policy) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = policy.titleAr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = policy.titleEn,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "اللواحق المستبعدة تلقائيًا من المزامنة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(".tmp", ".cache", ".log", ".bak").forEach { ext ->
                            AssistChip(
                                onClick = { },
                                label = { Text(ext) },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMb(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return String.format("%.1f MB", mb)
}
