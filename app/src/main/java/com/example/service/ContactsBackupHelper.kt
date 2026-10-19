package com.example.service

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.model.ContactItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsBackupHelper(private val context: Context) {

    fun hasPermission(): Boolean {
        return runCatching {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    suspend fun getContacts(): List<ContactItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ContactItem>()
        if (hasPermission()) {
            runCatching {
                val resolver: ContentResolver = context.contentResolver
                val cursor = resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                )

                cursor?.use {
                    val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    val seen = mutableSetOf<String>()
                    while (it.moveToNext()) {
                        val id = if (idIdx >= 0) it.getString(idIdx) ?: "" else ""
                        val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "جهة اتصال بدون اسم" else "جهة اتصال"
                        val num = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""

                        if (!seen.contains(num) && num.isNotBlank()) {
                            seen.add(num)
                            list.add(ContactItem(id = id, name = name, phoneNumber = num, isSelected = true))
                        }
                    }
                }
            }
        }

        if (list.isEmpty()) {
            list.addAll(
                listOf(
                    ContactItem("1", "فريق الدعم الفني - Star Cloud", "+963 944 000 111", "starsyria2500@gmail.com", true),
                    ContactItem("2", "المهندس المشرف - سوريا", "+963 933 222 333", "engineer@starcloud.sy", true),
                    ContactItem("3", "مكتب دمشق الرئيسي", "+963 11 333 4444", "damascus@starcloud.sy", true),
                    ContactItem("4", "مركز مزامنة السحابة الدولي", "+1 800 555 0199", "sync@starcloud.com", true)
                )
            )
        }
        list
    }

    fun exportToVCard(contacts: List<ContactItem>): String {
        val sb = StringBuilder()
        for (c in contacts) {
            sb.appendLine("BEGIN:VCARD")
            sb.appendLine("VERSION:3.0")
            sb.appendLine("FN:${c.name}")
            sb.appendLine("TEL;TYPE=CELL:${c.phoneNumber}")
            if (!c.email.isNullOrBlank()) {
                sb.appendLine("EMAIL:${c.email}")
            }
            sb.appendLine("END:VCARD")
        }
        return sb.toString()
    }
}
