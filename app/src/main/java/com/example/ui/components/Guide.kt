package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

const val DEV_TEAM = "Star Syria"
const val DEV_EMAIL = "starsyria2500@gmail.com"

/** مؤشر صغير في الصفحة الرئيسية يفتح دليل الاستخدام. */
@Composable
fun GuideBanner(onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Lightbulb, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("إرشادات الاستخدام", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("طريقة التشغيل في خطوات بسيطة", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("دليل البدء السريع", fontWeight = FontWeight.Bold)
                Text("كل ما تحتاجه لنسخ هاتفك واستعادته",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GuideStep(Icons.Rounded.LockOpen, "1) امنح الأذونات أولًا",
                    "من الصفحة الرئيسية اضغط «منح الإذن» للوصول إلى كل الملفات، واسمح بالصور وجهات الاتصال والرسائل عند طلبها.")
                GuideStep(Icons.Rounded.FactCheck, "2) اختر ما تريد نسخه",
                    "من تبويب «النسخ»: تطبيقات (ملف APK كامل)، صور وملفات ومجلدات، جهات اتصال، رسائل، وسجل مكالمات.")
                GuideStep(Icons.Rounded.CloudQueue, "3) حدّد مكان الحفظ",
                    "اضغط «أين تُحفظ النسخ؟» ثم أضف مكانًا: Google Drive بتسجيل الدخول مباشرة (الأسهل والأسرع)، أو مجلد في الهاتف/بطاقة SD، أو تطبيق سحابي آخر (OneDrive/Dropbox/Mega عبر منتقي النظام)، أو خادم WebDAV/Nextcloud. يمكنك إضافة أكثر من مكان وتُنسخ جميعها معًا.")
                GuideStep(Icons.Rounded.CloudUpload, "4) ابدأ النسخ",
                    "اضغط «ابدأ النسخ الآن». النسخ تزايدي: لا يُرفع إلا الجديد أو المتغيّر، وتستطيع متابعة التقدّم والسرعة وإيقافه في أي وقت.")
                GuideStep(Icons.Rounded.Restore, "5) الاستعادة عند الحاجة",
                    "من تبويب «الاستعادة» اختر مكان النسخة، ثم استعد التطبيقات (مع البيانات عند توفر Root/Shizuku) أو ملفات محددة أو جهات الاتصال والرسائل.")
                GuideStep(Icons.Rounded.Schedule, "6) اجعله تلقائيًا",
                    "من «الإعدادات» ← «النسخ التلقائي»: كل 6 ساعات أو يوميًا أو أسبوعيًا، مع خيارَي Wi-Fi فقط وأثناء الشحن فقط.")

                HorizontalDivider()

                Text("نصائح سريعة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Tip("نسخ بيانات التطبيقات واستعادتها يحتاج Root أو Shizuku بوضع Root. بدونهما يُنسخ APK ويُثبَّت التطبيق فقط.")
                Tip("مجلدا Android/data و Android/obb لا يمكن قراءتهما بدون Root على أندرويد 11 وأحدث.")
                Tip("استعادة الرسائل تتطلب جعل Star Cloud تطبيق الرسائل الافتراضي مؤقتًا (قيد من أندرويد).")
                Tip("«الاحتفاظ بما حذفته» يجعل النسخة تراكمية؛ أطفئه إن أردت أن تطابق النسخة هاتفك تمامًا.")

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("للاستفسارات الأخرى", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("فريق تطوير $DEV_TEAM", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        DEV_EMAIL,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEV_EMAIL")))
                            }
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("فهمت، لنبدأ") } }
    )
}

@Composable
private fun GuideStep(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(34.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tip(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
