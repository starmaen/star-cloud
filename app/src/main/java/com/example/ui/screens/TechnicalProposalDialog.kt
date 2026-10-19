package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.StarlightGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicalProposalDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "العرض الفني والمعماري — Star Cloud",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_proposal_button")) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Stars, contentDescription = null, tint = StarlightGold, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "وثيقة العرض الفني المتكامل لمشروع Star Cloud",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "مُعد خصيصًا لتلبية كافة المتطلبات الهندسية والتصميمية الواردة في كراسة الشروط.\nجهة التواصل المعتمدة: starsyria2500@gmail.com",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                item {
                    ProposalSectionCard(
                        number = "1",
                        title = "البنية المعمارية المقترحة والتكامل السحابي",
                        content = "• النمط المعماري: Clean Architecture + MVVM مع مبادئ SOLID وفصل تام بين طبقة واجهة المستخدم (UI)، طبقة النطاق (Domain)، وطبقة البيانات (Data Layer).\n\n" +
                                "• نمط المزود السحابي القابل للتوسع (Pluggable Cloud Provider Pattern):\n" +
                                "تم تصميم واجهة برمجية موحدة (CloudStorageProvider) تسمح بتبديل أو إضافة مزودي خدمة سحابية متعددين مثل Google Drive، Dropbox، وOneDrive دون تعديل محرك المزامنة الأساسي.\n\n" +
                                "• تكامل Google Drive API:\n" +
                                "1. الاعتماد على نطاقات الصلاحيات الآمنة: https://www.googleapis.com/auth/drive.file للوصول إلى الملفات التي ينشئها التطبيق فقط، أو drive.appdata للمجلد السحابي المخفي للتطبيق.\n" +
                                "2. الرفع المجزأ والاستئناف التلقائي (Resumable Uploads) للملفات الكبيرة والوسائط لمنع فشل الرفع عند تذبذب الشبكة."
                    )
                }

                item {
                    ProposalSectionCard(
                        number = "2",
                        title = "آلية اختيار المجلدات عبر SAF والاحتفاظ الدائم بالصلاحيات",
                        content = "• Scoped Storage في أندرويد 10+ (API 29+):\n" +
                                "الالتزام التام بإرشادات Google Play بعدم طلب أذونات التخزين الشاملة (READ_EXTERNAL_STORAGE)؛ وبدلاً من ذلك استخدام Storage Access Framework (SAF) عبر ActivityResultContracts.OpenDocumentTree.\n\n" +
                                "• الاحتفاظ بالأذونات بشكل دائم (Persistable URI Permission):\n" +
                                "فور اختيار المستخدم لأي مجلد، ينفذ التطبيق الكود التالي فورًا:\n" +
                                "contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)\n" +
                                "مما يضمن استمرار إمكانية قراءة وكتابة المجلد حتى بعد إعادة تشغيل الهاتف."
                    )
                }

                item {
                    ProposalSectionCard(
                        number = "3",
                        title = "استراتيجية المزامنة الانتقائية وقيود بيانات التطبيقات",
                        content = "• جهات الاتصال: قراءة وتحديث جهات الاتصال عبر ContactsContract وتصديرها بصيغة vCard القياسية مع التحديد الفردي لكل جهة اتصال.\n\n" +
                                "• الرسائل والمكالمات: نسخ رسائل SMS وسجلات المكالمات بتنسيق JSON مهيكل وقابل للاستيراد.\n\n" +
                                "• تطبيقات المراسلة (واتساب): قراءة مسار الوسائط الرسمي (/Android/media/com.whatsapp/WhatsApp/Media/) وتصنيفها انتقائيًا (صور، فيديو، مستندات، صوتيات، وقواعد بيانات النسخ الاحتياطي المشفرة).\n\n" +
                                "• نسخ تطبيقات الجهاز (APK): استخراج ملف حزمة التثبيت الأصلية (ApplicationInfo.sourceDir) وحفظها سحابيًا مع توفير آلية التثبيت المباشر عبر FileProvider.\n\n" +
                                "⚠️ القيود التقنية لبيانات التطبيقات (App Data Sandbox):\n" +
                                "وفق معمارية نظام أندرويد الصارمة، فإن مجلد البيانات الداخلية (/data/data/<package_name>/) محمي بعزل تام (Sandbox) بموجب سياسات أمان Linux وSELinux، ولا يمكن لتطبيق طرف ثالث الوصول إليه إلا بصلاحيات Root أو عبر أنظمة Enterprise MDM. يوضح التطبيق هذا القيد بوضوح مع توفير نسخ ملفات التطبيقات والوسائط المسموحة بالكامل."
                    )
                }

                item {
                    ProposalSectionCard(
                        number = "4",
                        title = "خطة الجدولة الدورية باستخدام WorkManager",
                        content = "• استخدام PeriodicWorkRequestBuilder لضمان تنفيذ المزامنة بدقة حتى عند إغلاق التطبيق أو دخول الجهاز في وضع Doze Mode.\n\n" +
                                "• القيود الذكية (WorkManager Constraints):\n" +
                                "1. شرط الشبكة (NetworkType.UNMETERED): لتشغيل المزامنة عند الاتصال بشبكة Wi-Fi فقط لحماية باقة الجوال.\n" +
                                "2. شرط البطارية (RequiresBatteryNotLow): لمنع استنزاف بطارية الهاتف.\n" +
                                "3. شرط الشحن (RequiresCharging): كخيار إضافي للمزامنة الليلية أثناء الشحن.\n" +
                                "4. استئناف تلقائي بعد انقطاع الاتصال (BackoffCriteria.EXPONENTIAL)."
                    )
                }

                item {
                    ProposalSectionCard(
                        number = "5",
                        title = "نموذج الواجهة والتصميم العصري (Material Design 3)",
                        content = "• واجهة برمجية تفاعلية تم تطويرها بالكامل عبر Jetpack Compose.\n" +
                                "• دعم الوضع الليلي الداكن والوضع النهاري والألوان المتجاوبة.\n" +
                                "• لوحة تحكم ومقاييس سعة الجهاز وسحابة Google Drive.\n" +
                                "• شريط متابعة آني لتقدم المزامنة وسرعة النقل بالبايت والميغابايت.\n" +
                                "• دعم كامل للغة العربية ومطابقة معايير إمكانية الوصول (Accessibility & 48dp touch targets)."
                    )
                }

                item {
                    ProposalSectionCard(
                        number = "6",
                        title = "الخطة الزمنية لتسليم وتطوير المراحل",
                        content = "• المرحلة 1 (الأسبوع 1): معمارية المشروع، إعداد Google Drive API، ونظام أذونات SAF الدائم.\n" +
                                "• المرحلة 2 (الأسبوع 2): محرك المزامنة الانتقائية لجهات الاتصال، الرسائل، ووسائط واتساب.\n" +
                                "• المرحلة 3 (الأسبوع 3): استخراج حزم APK، المرشحات المتقدمة، وحل النزاعات.\n" +
                                "• المرحلة 4 (الأسبوع 4): جدولة WorkManager، تحسين الأداء واستهلاك البطارية، واختبارات Robolectric.\n" +
                                "• المرحلة 5 (الأسبوع 5): التسليم النهائي، التوثيق الهندسي، وإطلاق نسخة الإنتاج."
                    )
                }

                item {
                    ProposalSectionCard(
                        number = "7",
                        title = "جاهزية الفريق والدعم الفني",
                        content = "يمتلك فريقنا خبرة متقدمة تزيد عن 7 سنوات في تطوير تطبيقات الأندرويد الأصلية بالاعتماد على Kotlin وJetpack Compose وتطبيقات التخزين السحابي والمزامنة الكبيرة وفق أعلى معايير أمان البيانات.\n\n" +
                                "لأي استفسارات أو تفاصيل إضافية:\n" +
                                "📧 البريد الإلكتروني: starsyria2500@gmail.com"
                    )
                }

                item {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("العودة إلى لوحة تحكم Star Cloud", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposalSectionCard(
    number: String,
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.35
            )
        }
    }
}
