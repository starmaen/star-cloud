package com.example

import android.app.Application
import android.util.Log
import com.example.data.cloud.CloudProviderRegistry
import com.example.data.local.StarCloudDatabase
import com.example.data.local.SyncPreferences
import com.example.service.AppBackupHelper
import com.example.service.ContactsBackupHelper
import com.example.service.SmsCallLogBackupHelper
import com.example.service.SyncEngine
import com.example.service.SyncScheduler
import com.example.service.WhatsAppBackupHelper

class StarCloudApp : Application() {

    lateinit var database: StarCloudDatabase private set
    lateinit var preferences: SyncPreferences private set
    lateinit var cloudRegistry: CloudProviderRegistry private set
    lateinit var contactsHelper: ContactsBackupHelper private set
    lateinit var smsHelper: SmsCallLogBackupHelper private set
    lateinit var waHelper: WhatsAppBackupHelper private set
    lateinit var appHelper: AppBackupHelper private set
    lateinit var syncEngine: SyncEngine private set
    val syncScheduler: SyncScheduler by lazy { SyncScheduler(this, preferences) }

    private fun writeCrashReport(thread: Thread, throwable: Throwable) {
        val report = buildString {
            appendLine("Star Cloud crash report")
            appendLine("Time: ${java.util.Date()}")
            appendLine("Thread: ${thread.name}")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("App: $packageName ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            append(Log.getStackTraceString(throwable))
        }
        java.io.File(filesDir, "last_crash.txt").writeText(report)
        getExternalFilesDir(null)?.let { dir ->
            runCatching { java.io.File(dir, "last_crash.txt").writeText(report) }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("StarCloudApp", "Uncaught exception: ${throwable.message}", throwable)
            runCatching { writeCrashReport(thread, throwable) }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        database = StarCloudDatabase.getDatabase(this)
        preferences = SyncPreferences(this)
        cloudRegistry = CloudProviderRegistry(this)
        contactsHelper = ContactsBackupHelper(this)
        smsHelper = SmsCallLogBackupHelper(this)
        waHelper = WhatsAppBackupHelper(this)
        appHelper = AppBackupHelper(this)

        syncEngine = SyncEngine(
            context = this,
            database = database,
            preferences = preferences,
            contactsHelper = contactsHelper,
            smsHelper = smsHelper,
            waHelper = waHelper,
            appHelper = appHelper
        )
    }
}
