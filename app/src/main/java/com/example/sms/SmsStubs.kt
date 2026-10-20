package com.example.sms

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

/**
 * مكوّنات فارغة مطلوبة من النظام كي يقبل جعل التطبيق «تطبيق الرسائل الافتراضي» مؤقتاً،
 * وهو الشرط الوحيد في أندرويد لكتابة الرسائل المستعادة. لا تستقبل ولا ترسل شيئاً.
 */
class SmsReceiver : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) {} }
class MmsReceiver : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) {} }
class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); finish() }
}
class RespondViaMessageService : Service() { override fun onBind(intent: Intent?): IBinder? = null }
