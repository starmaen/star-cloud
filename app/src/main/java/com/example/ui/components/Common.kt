package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.rounded.Android
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.QuotaInfo
import com.example.core.formatBytes

@Composable
fun Card1(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = c) {
        Column(Modifier.padding(16.dp), content = content)
    } else Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = c) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp))
}

@Composable
fun IconRow(
    icon: ImageVector, title: String, subtitle: String? = null,
    onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ScreenScaffold(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
                actions = actions
            )
        },
        content = content
    )
}

@Composable
fun StorageBar(label: String, q: QuotaInfo?, extra: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(
                if (q?.total != null && q.used != null) "${formatBytes(q.used)} من ${formatBytes(q.total)}"
                else if (q?.free != null) "متاح ${formatBytes(q.free)}" else "المساحة غير معروفة",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (q?.total != null && q.used != null && q.total > 0) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { (q.used.toFloat() / q.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun BigButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
        if (icon != null) { Icon(icon, null); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun Notice(text: String, error: Boolean = false, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (error) Color(0x33EF4444) else Color(0x33F59E0B))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (actionText != null && onAction != null) TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** أيقونة التطبيق: من النسخة المحفوظة، وإلا من الهاتف إن كان مثبتاً، وإلا أيقونة أندرويد. */
@Composable
fun AppIconView(pkg: String, iconB64: String = "", size: androidx.compose.ui.unit.Dp = 44.dp) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pkg, iconB64) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if (iconB64.isNotEmpty()) {
                    val b = android.util.Base64.decode(iconB64, android.util.Base64.NO_WRAP)
                    android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size).asImageBitmap()
                } else null
            }.getOrNull() ?: runCatching {
                ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) androidx.compose.foundation.Image(b, null, Modifier.fillMaxSize())
        else Icon(androidx.compose.material.icons.Icons.Rounded.Android, null, modifier = Modifier.size(size * 0.7f))
    }
}
