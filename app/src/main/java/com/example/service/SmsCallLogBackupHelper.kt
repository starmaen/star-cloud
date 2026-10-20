package com.example.service

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.data.model.CallLogItem
import com.example.data.model.SmsLogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SmsCallLogBackupHelper(private val context: Context) {

    fun hasSmsPermission(): Boolean = runCatching {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun hasCallLogPermission(): Boolean = runCatching {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    suspend fun getSmsMessages(): List<SmsLogItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SmsLogItem>()
        if (hasSmsPermission()) {
            runCatching {
                val uri = Uri.parse("content://sms")
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf("_id", "address", "body", "date", "type"),
                    null,
                    null,
                    "date DESC LIMIT 100"
                )
                cursor?.use {
                    val idIdx = it.getColumnIndex("_id")
                    val addrIdx = it.getColumnIndex("address")
                    val bodyIdx = it.getColumnIndex("body")
                    val dateIdx = it.getColumnIndex("date")
                    val typeIdx = it.getColumnIndex("type")

                    while (it.moveToNext()) {
                        list.add(
                            SmsLogItem(
                                id = if (idIdx >= 0) it.getString(idIdx) ?: "" else "",
                                address = if (addrIdx >= 0) it.getString(addrIdx) ?: "مجهول" else "مجهول",
                                body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else "",
                                date = if (dateIdx >= 0) it.getLong(dateIdx) else 0L,
                                type = if (typeIdx >= 0) it.getInt(typeIdx) else 1,
                                isSelected = true
                            )
                        )
                    }
                }
            }
        }

        list
    }

    suspend fun getCallLogs(): List<CallLogItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CallLogItem>()
        if (hasCallLogPermission()) {
            runCatching {
                val cursor = context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls._ID,
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.DATE,
                        CallLog.Calls.DURATION,
                        CallLog.Calls.TYPE
                    ),
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC LIMIT 100"
                )
                cursor?.use {
                    val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                    val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                    val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                    val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)

                    while (it.moveToNext()) {
                        list.add(
                            CallLogItem(
                                id = if (idIdx >= 0) it.getString(idIdx) ?: "" else "",
                                number = if (numIdx >= 0) it.getString(numIdx) ?: "" else "",
                                name = if (nameIdx >= 0) it.getString(nameIdx) else null,
                                date = if (dateIdx >= 0) it.getLong(dateIdx) else 0L,
                                durationSeconds = if (durIdx >= 0) it.getLong(durIdx) else 0L,
                                type = if (typeIdx >= 0) it.getInt(typeIdx) else 1,
                                isSelected = true
                            )
                        )
                    }
                }
            }
        }

        list
    }

    fun exportSmsToJson(items: List<SmsLogItem>): String {
        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("address", item.address)
                put("body", item.body)
                put("date", item.date)
                put("type", item.type)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    fun exportCallLogsToJson(items: List<CallLogItem>): String {
        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("number", item.number)
                put("name", item.name ?: "")
                put("date", item.date)
                put("durationSeconds", item.durationSeconds)
                put("type", item.type)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }
}
