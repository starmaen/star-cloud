package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.ScheduleInterval
import com.example.ui.StarCloudViewModel
import com.example.ui.theme.StarBluePrimary
import com.example.ui.theme.StarlightGold

@Composable
fun SettingsScreen(
    viewModel: StarCloudViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheduleInterval by viewModel.scheduleInterval.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val requireCharging by viewModel.requireCharging.collectAsState()
    val cloudEnabled by viewModel.cloudEnabled.collectAsState()
    val destinationLabel by viewModel.destinationLabel.collectAsState()
    val destinationReady by viewModel.destinationReady.collectAsState()
    val googleEmail by viewModel.googleEmail.collectAsState()
    val googleDriveMode by viewModel.googleDriveMode.collectAsState()

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onGoogleSignInResult(result.data)
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            val authority = uri.authority.orEmpty()
            val folderName = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
            val label = when {
                authority.contains("google.android.apps.docs", ignoreCase = true) ->
                    "Google Drive" + if (!folderName.isNullOrBlank()) " — $folderName" else ""
                authority.contains("externalstorage", ignoreCase = true) ->
                    folderName?.takeIf { it.isNotBlank() } ?: "ذاكرة الجهاز"
                else -> folderName?.takeIf { it.isNotBlank() } ?: "المجلد المختار"
            }
            viewModel.setDestination(uri, label)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(StarBluePrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = StarBluePrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "حساب Google",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = googleEmail ?: "لم يتم تسجيل الدخول",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (googleEmail != null) {
                            "المزامنة السحابية الحقيقية مفعّلة. ستجد مجلد Star Cloud داخل Google Drive الخاص بحسابك."
                        } else {
                            "سجّل الدخول بحساب Google لرفع النسخ مباشرة إلى Google Drive. لا نرى كلمة المرور، والصلاحية مقصورة على الملفات التي ينشئها التطبيق."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    if (googleEmail == null) {
                        Button(
                            onClick = { googleSignInLauncher.launch(viewModel.googleSignInIntent()) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تسجيل الدخول بحساب Google")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "يتطلب هذا الدخول تسجيل التطبيق في Google Cloud بـ OAuth client نوع Android. Package: com.aistudio.starcloud.kxpqr — SHA-1: 3D:0D:5A:FD:41:96:69:1F:C2:2E:89:AE:D2:5E:8E:70:F5:74:C5:9F. وبدون ذلك استخدم «اختيار مجلد» واختر Google Drive.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.signOutGoogle() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تسجيل الخروج")
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(StarBluePrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = StarBluePrimary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "وجهة المزامنة",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        googleDriveMode && cloudEnabled -> "Google Drive"
                                        cloudEnabled -> destinationLabel
                                        else -> "مساحة التطبيق الداخلية"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Switch(
                            checked = cloudEnabled,
                            onCheckedChange = { viewModel.setCloudEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (cloudEnabled) {
                            if (destinationReady) "الوجهة جاهزة ويمكن الكتابة فيها."
                            else "سجّل الدخول بحساب Google أو أعد اختيار مجلد الكتابة."
                        } else {
                            "المزامنة المحلية فقط. فعّل المفتاح لاستخدام Google Drive أو مجلد على الهاتف أو بطاقة SD."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "اختر الوجهة",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.useInternalStorage() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مساحة التطبيق الداخلية (محلي فقط)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("اختيار مجلد (الهاتف / SD / Google Drive)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "افتح المنتقي واختر Google Drive ← مجلد Star Cloud لرفع الملفات فعلياً إلى حسابك. هذا الخيار لا يحتاج أي إعداد إضافي.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "الجدولة التلقائية (WorkManager)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "تعمل المزامنة المجدولة في الخلفية بكفاءة عالية وفق معايير Doze Mode واستهلاك البطارية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ScheduleInterval.values().forEach { interval ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setScheduleInterval(interval) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = interval == scheduleInterval,
                                onClick = { viewModel.setScheduleInterval(interval) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = interval.titleAr,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (interval == scheduleInterval) FontWeight.Bold else FontWeight.Normal
                            )
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
                        text = "شروط الشبكة والطاقة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("المزامنة عبر Wi-Fi فقط", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("حماية باقة بيانات الجوال وتجنب التكاليف", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { viewModel.setWifiOnly(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("أثناء الشحن فقط", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("تشغيل المزامنة فقط عند توصيل الهاتف بالشاحن", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = requireCharging,
                            onCheckedChange = { viewModel.setRequireCharging(it) }
                        )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تشفير البيانات محليًا (AES-256)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "قيد التطوير — لن يتم تفعيله قبل اكتمال التشفير والتحقق منه.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = false,
                            onCheckedChange = null,
                            enabled = false
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = StarlightGold, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "حول تطبيق Star Cloud",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Star Cloud تطبيق أصلي بلغة Kotlin وJetpack Compose، يعتمد على SAF للوصول الآمن إلى مساحة التطبيق وذاكرة الهاتف وبطاقة SD وGoogle Drive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.showProposal(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proposal_modal_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Article, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("عرض ملف العرض الفني والمعماري الشامل", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:starsyria2500@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "استفسار بخصوص تطبيق Star Cloud")
                            }
                            context.startActivity(Intent.createChooser(intent, "إرسال بريد إلكتروني"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تواصل مع الدعم الفني: starsyria2500@gmail.com")
                    }
                }
            }
        }
    }
}
