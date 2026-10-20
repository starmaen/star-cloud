package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.example.core.data.Settings
import com.example.core.data.StarDb
import com.example.core.dest.GoogleAccount
import com.example.core.engine.BackupEngine
import com.example.core.engine.RestoreEngine
import com.example.core.engine.Tracker
import java.io.File

class StarApp : Application() {
    lateinit var settings: Settings private set
    lateinit var db: StarDb private set
    lateinit var google: GoogleAccount private set
    val tracker = Tracker()

    val backupEngine by lazy { BackupEngine(this, settings, db, google, tracker) }
    val restoreEngine by lazy { RestoreEngine(this, settings, db, google, tracker) }

    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { File(filesDir, "last_crash.txt").writeText("${java.util.Date()}\n${Log.getStackTraceString(e)}") }
            prev?.uncaughtException(t, e)
        }
        settings = Settings(this)
        db = StarDb.get(this)
        google = GoogleAccount(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RUN, "تقدّم النسخ والاستعادة", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_RESULT, "نتيجة النسخ والاستعادة", NotificationManager.IMPORTANCE_DEFAULT))
    }

    companion object {
        const val CH_RUN = "run"
        const val CH_RESULT = "result"
    }
}
