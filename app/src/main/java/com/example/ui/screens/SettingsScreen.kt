package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CloudProviderType
import com.example.data.model.ScheduleInterval
import com.example.ui.StarCloudViewModel
import com.example.ui.theme.CosmicCyan
import com.example.ui.theme.StarBluePrimary
import com.example.ui.theme.StarlightGold

@Composable
fun SettingsScreen(
    viewModel: StarCloudViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectedAccount by viewModel.connectedAccount.collectAsState()
    val scheduleInterval by viewModel.scheduleInterval.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val requireCharging by viewModel.requireCharging.collectAsState()
    val clientEncryption by viewModel.clientEncryption.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()

    var showAccountDialog by remember { mutableStateOf(false) }
    var accountInput by remember { mutableStateOf(connectedAccount) }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(StarBluePrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Cloud, contentDescription = null, tint = StarBluePrimary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "حساب Google Drive",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = connectedAccount,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                accountInput = connectedAccount
                                showAccountDialog = true
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("تغيير")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "نطاقات الصلاحيات النشطة: drive.file و drive.appdata لضمان خصوصية تامة.",
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
                                text = "تشفير النسخ قبل رفعها إلى Google Drive بمفتاح سري خاص بك",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = clientEncryption,
                            onCheckedChange = { viewModel.setClientEncryption(it) }
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
                    Text(
                        text = "مزود التخزين السحابي (بنية معمارية قابلة للتوسع)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CloudProviderType.values().forEach { provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setCloudProvider(provider) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = provider == selectedProvider,
                                onClick = { viewModel.setCloudProvider(provider) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (provider == selectedProvider) FontWeight.Bold else FontWeight.Normal
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
                        text = "Star Cloud هو تطبيق أصلي مبني بلغة Kotlin وJetpack Compose وفق أعلى معايير الجودة والأمان (Clean Architecture + MVVM + WorkManager + SAF).",
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

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("تحديث حساب Google Drive") },
            text = {
                Column {
                    Text("أدخل البريد الإلكتروني المعتمد لمزامنة Google Drive:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = accountInput,
                        onValueChange = { accountInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (accountInput.isNotBlank()) {
                            viewModel.setConnectedAccount(accountInput.trim())
                        }
                        showAccountDialog = false
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
