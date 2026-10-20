package com.example.core

import org.json.JSONArray
import org.json.JSONObject

/** تطبيق مختار للنسخ، مع خيار نسخ بياناته (يحتاج Root أو Shizuku). */
data class AppChoice(val pkg: String, val withData: Boolean = false)

/** كل ما اختاره المستخدم ليُنسخ احتياطياً. */
data class Selection(
    val apps: List<AppChoice> = emptyList(),
    /** مسارات مطلقة لملفات أو مجلدات على الهاتف. */
    val paths: List<String> = emptyList(),
    val contacts: Boolean = false,
    val sms: Boolean = false,
    val calls: Boolean = false
) {
    val isEmpty get() = apps.isEmpty() && paths.isEmpty() && !contacts && !sms && !calls

    fun toJson(): String = JSONObject().apply {
        put("apps", JSONArray().apply {
            apps.forEach { put(JSONObject().put("pkg", it.pkg).put("data", it.withData)) }
        })
        put("paths", JSONArray(paths))
        put("contacts", contacts); put("sms", sms); put("calls", calls)
    }.toString()

    companion object {
        fun fromJson(s: String?): Selection {
            if (s.isNullOrBlank()) return Selection()
            return runCatching {
                val o = JSONObject(s)
                val a = o.optJSONArray("apps") ?: JSONArray()
                val p = o.optJSONArray("paths") ?: JSONArray()
                Selection(
                    apps = (0 until a.length()).map {
                        val x = a.getJSONObject(it); AppChoice(x.getString("pkg"), x.optBoolean("data"))
                    },
                    paths = (0 until p.length()).map { p.getString(it) },
                    contacts = o.optBoolean("contacts"),
                    sms = o.optBoolean("sms"),
                    calls = o.optBoolean("calls")
                )
            }.getOrDefault(Selection())
        }
    }
}

enum class DestKind(val title: String) {
    FOLDER("مجلد (هاتف / بطاقة / تطبيق سحابي)"),
    WEBDAV("WebDAV / Nextcloud"),
    DRIVE_API("Google Drive مباشر")
}

data class DestConfig(
    val id: String,
    val kind: DestKind,
    val label: String,
    val enabled: Boolean = true,
    /** FOLDER: tree uri — WEBDAV: base url */
    val target: String = "",
    val user: String = "",
    val secret: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind.name).put("label", label).put("enabled", enabled)
        .put("target", target).put("user", user).put("secret", secret)

    companion object {
        fun fromJson(o: JSONObject) = DestConfig(
            id = o.getString("id"),
            kind = runCatching { DestKind.valueOf(o.getString("kind")) }.getOrDefault(DestKind.FOLDER),
            label = o.optString("label"),
            enabled = o.optBoolean("enabled", true),
            target = o.optString("target"),
            user = o.optString("user"),
            secret = o.optString("secret")
        )
    }
}

data class RemoteEntry(val name: String, val isDir: Boolean, val size: Long = 0, val modified: Long = 0)

data class QuotaInfo(val total: Long?, val used: Long?, val free: Long?)

enum class Interval(val title: String, val hours: Long) {
    MANUAL("يدوي فقط", 0),
    EVERY_6H("كل 6 ساعات", 6),
    DAILY("يومياً", 24),
    WEEKLY("أسبوعياً", 168)
}

data class RunState(
    val running: Boolean = false,
    val isRestore: Boolean = false,
    val phase: String = "",
    val current: String = "",
    val doneItems: Int = 0,
    val totalItems: Int = 0,
    val doneBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBps: Long = 0,
    val skipped: Int = 0,
    val errors: List<String> = emptyList(),
    val summary: String? = null,
    val finishedAt: Long = 0
) {
    val fraction: Float
        get() = when {
            totalBytes > 0 -> (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            totalItems > 0 -> (doneItems.toFloat() / totalItems).coerceIn(0f, 1f)
            else -> 0f
        }
}

fun formatBytes(b: Long?): String {
    if (b == null) return "غير معروف"
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble(); var i = -1
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(java.util.Locale.US, "%.1f %s", v, units[i])
}
