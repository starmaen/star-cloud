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

    override fun onCreate() {
        super.onCreate()

        // Global crash prevention handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("StarCloudApp", "Caught unhandled exception safely: ${throwable.message}", throwable)
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
