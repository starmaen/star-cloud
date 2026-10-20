package com.example.core.engine

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** تثبيت APK (مع الـ splits) عبر مثبّت النظام عندما لا يتوفر Root/Shizuku. المستخدم يؤكد التثبيت. */
object ApkInstaller {
    const val ACTION = "com.example.STAR_INSTALL_RESULT"
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<String?>>()

    suspend fun install(context: Context, files: List<File>): String? = withContext(Dispatchers.IO) {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = pi.createSession(params)
        val done = CompletableDeferred<String?>()
        waiting[id] = done
        try {
            pi.openSession(id).use { s ->
                for (f in files) {
                    f.inputStream().use { input ->
                        s.openWrite(f.name, 0, f.length()).use { out -> input.copyTo(out, 256 * 1024); s.fsync(out) }
                    }
                }
                val intent = Intent(ACTION).setPackage(context.packageName).putExtra("sid", id)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                s.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
            withTimeout(5 * 60_000L) { done.await() }
        } catch (e: Exception) {
            runCatching { pi.abandonSession(id) }
            e.message ?: "فشل التثبيت"
        } finally { waiting.remove(id) }
    }

    fun onResult(context: Context, intent: Intent) {
        val sid = intent.getIntExtra("sid", -1)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> waiting[sid]?.complete(null)
            else -> waiting[sid]?.complete(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "رفض النظام التثبيت")
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { ApkInstaller.onResult(context, intent) }
}
