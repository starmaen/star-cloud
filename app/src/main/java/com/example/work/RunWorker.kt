package com.example.work

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.StarApp
import com.example.core.Interval
import com.example.core.engine.RestoreHolder
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** ينفّذ النسخ أو الاستعادة كخدمة أمامية كي لا يقتله النظام أثناء العمل الطويل. */
class RunWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val restore get() = inputData.getBoolean("restore", false)

    private fun notif(text: String, progress: Int = -1): Notification {
        val b = NotificationCompat.Builder(applicationContext, StarApp.CH_RUN)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(if (restore) "Star Cloud — استعادة" else "Star Cloud — نسخ احتياطي")
            .setContentText(text).setOngoing(true).setOnlyAlertOnce(true)
        if (progress in 0..100) b.setProgress(100, progress, false) else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun info(text: String, progress: Int = -1): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(NOTIF_ID, notif(text, progress), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, notif(text, progress))

    override suspend fun getForegroundInfo(): ForegroundInfo = info("جارٍ التحضير…")

    override suspend fun doWork(): Result {
        val app = applicationContext as StarApp
        if (app.tracker.state.value.running) return Result.success()
        runCatching { setForeground(info("جارٍ التحضير…")) }
        val nm = applicationContext.getSystemService(NotificationManager::class.java)

        val ok = coroutineScope {
            val watcher = launch {
                app.tracker.state.collect { s ->
                    if (s.running) {
                        val text = if (s.current.isNotBlank()) "${s.phase} — ${s.current}" else s.phase
                        runCatching { nm.notify(NOTIF_ID, notif(text.ifBlank { "جارٍ العمل…" }, (s.fraction * 100).toInt())) }
                    }
                }
            }
            val r = try {
                if (restore) {
                    val plan = RestoreHolder.plan
                    if (plan == null) false else app.restoreEngine.run(plan)
                } else app.backupEngine.run()
            } finally { watcher.cancel() }
            r
        }

        val s = app.tracker.state.value
        runCatching {
            nm.cancel(NOTIF_ID)
            nm.notify(NOTIF_ID + 1, NotificationCompat.Builder(applicationContext, StarApp.CH_RESULT)
                .setSmallIcon(if (ok) android.R.drawable.stat_sys_upload_done else android.R.drawable.stat_notify_error)
                .setContentTitle(if (ok) "اكتملت العملية" else "انتهت العملية مع أخطاء")
                .setContentText(s.summary ?: "").setAutoCancel(true).build())
        }
        return Result.success()
    }

    companion object {
        const val NOTIF_ID = 4101

        fun runNow(context: Context, restore: Boolean) {
            val req = OneTimeWorkRequestBuilder<RunWorker>()
                .setInputData(workDataOf("restore" to restore))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("star_run", ExistingWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork("star_run") }

        fun applySchedule(context: Context) {
            val app = context.applicationContext as StarApp
            val wm = WorkManager.getInstance(context)
            val s = app.settings
            if (s.interval == Interval.MANUAL) { wm.cancelUniqueWork("star_periodic"); return }
            val c = Constraints.Builder()
                .setRequiredNetworkType(if (s.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(s.chargingOnly)
                .build()
            val req = PeriodicWorkRequestBuilder<RunWorker>(s.interval.hours, TimeUnit.HOURS)
                .setConstraints(c).build()
            wm.enqueueUniquePeriodicWork("star_periodic", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
