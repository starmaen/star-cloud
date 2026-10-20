package com.example.core.data

import android.content.Context
import android.os.Build
import com.example.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** كل إعدادات التطبيق + قائمة الوجهات + الاختيارات، في مكان واحد. */
class Settings(context: Context) {
    private val sp = context.getSharedPreferences("star_cloud", Context.MODE_PRIVATE)

    var selection: Selection
        get() = Selection.fromJson(sp.getString("selection", null))
        set(v) { sp.edit().putString("selection", v.toJson()).apply() }

    var interval: Interval
        get() = runCatching { Interval.valueOf(sp.getString("interval", "MANUAL")!!) }.getOrDefault(Interval.MANUAL)
        set(v) { sp.edit().putString("interval", v.name).apply() }

    var wifiOnly: Boolean
        get() = sp.getBoolean("wifi_only", true)
        set(v) { sp.edit().putBoolean("wifi_only", v).apply() }

    var chargingOnly: Boolean
        get() = sp.getBoolean("charging_only", false)
        set(v) { sp.edit().putBoolean("charging_only", v).apply() }

    /** true = الملفات التي تُحذف من الهاتف تبقى في النسخة (تراكمي). */
    var keepDeleted: Boolean
        get() = sp.getBoolean("keep_deleted", true)
        set(v) { sp.edit().putBoolean("keep_deleted", v).apply() }

    var maxFileMb: Int
        get() = sp.getInt("max_file_mb", 0)
        set(v) { sp.edit().putInt("max_file_mb", v).apply() }

    var keepVersions: Int
        get() = sp.getInt("keep_versions", 5)
        set(v) { sp.edit().putInt("keep_versions", v).apply() }

    var deviceName: String
        get() = sp.getString("device_name", null) ?: defaultDeviceName()
        set(v) { sp.edit().putString("device_name", v.trim().ifBlank { defaultDeviceName() }).apply() }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) { sp.edit().putBoolean("onboarded", v).apply() }

    var destinations: List<DestConfig>
        get() = runCatching {
            val a = JSONArray(sp.getString("destinations", "[]"))
            (0 until a.length()).map { DestConfig.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        set(v) {
            val a = JSONArray(); v.forEach { a.put(it.toJson()) }
            sp.edit().putString("destinations", a.toString()).apply()
        }

    fun addDestination(c: DestConfig) { destinations = destinations.filter { it.id != c.id } + c }
    fun removeDestination(id: String) { destinations = destinations.filter { it.id != id } }
    fun setDestinationEnabled(id: String, on: Boolean) {
        destinations = destinations.map { if (it.id == id) it.copy(enabled = on) else it }
    }

    companion object {
        fun newId() = UUID.randomUUID().toString().take(8)
        fun defaultDeviceName(): String =
            (Build.MODEL ?: "Phone").replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().ifBlank { "Phone" }
    }
}
