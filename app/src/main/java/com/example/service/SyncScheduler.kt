package com.example.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.SyncPreferences
import com.example.data.model.ScheduleInterval
import java.util.concurrent.TimeUnit

class SyncScheduler(private val context: Context, private val preferences: SyncPreferences) {

    private val workManager by lazy {
        runCatching { WorkManager.getInstance(context) }.getOrNull()
    }

    fun applySchedule(interval: ScheduleInterval) {
        val wm = workManager ?: return
        if (interval == ScheduleInterval.MANUAL_ONLY) {
            cancelScheduledSync()
            return
        }

        val constraintsBuilder = Constraints.Builder()
            .setRequiresBatteryNotLow(true)

        if (preferences.isWifiOnly()) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
        } else {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }

        if (preferences.isRequireCharging()) {
            constraintsBuilder.setRequiresCharging(true)
        }

        val repeatIntervalHours = interval.hours.coerceAtLeast(1)
        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatIntervalHours, TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraintsBuilder.build())
            .addTag(WORK_TAG_STAR_CLOUD_SYNC)
            .build()

        wm.enqueueUniquePeriodicWork(
            WORK_NAME_PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancelScheduledSync() {
        workManager?.cancelUniqueWork(WORK_NAME_PERIODIC_SYNC)
    }

    companion object {
        const val WORK_TAG_STAR_CLOUD_SYNC = "star_cloud_sync_tag"
        const val WORK_NAME_PERIODIC_SYNC = "star_cloud_periodic_sync"
    }
}
