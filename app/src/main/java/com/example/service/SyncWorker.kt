package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.StarCloudApp
import kotlinx.coroutines.flow.first

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as StarCloudApp
        val syncEngine = app.syncEngine
        val registry = app.cloudRegistry
        val preferences = app.preferences
        val database = app.database

        val provider = registry.getActiveProvider()
        val folders = database.syncDao().getAllFolderConfigs().first()

        val contacts = app.contactsHelper.getContacts().filter { it.isSelected }
        val wa = app.waHelper.getWhatsAppMediaCategories().filter { it.isSelected }
        val apps = app.appHelper.getInstalledApps().filter { it.isSelected }

        val success = syncEngine.executeSync(
            provider = provider,
            direction = preferences.getSyncDirection(),
            folderConfigs = folders,
            selectedContacts = contacts,
            selectedWhatsAppCategories = wa,
            selectedApps = apps
        )

        return if (success) Result.success() else Result.retry()
    }
}
