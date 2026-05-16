package com.myapp.expensetracker

import android.app.Application
import com.myapp.expensetracker.di.appModule
import com.myapp.expensetracker.worker.FeatureNudgeWorker
import com.myapp.expensetracker.worker.UpdateCheckWorker
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ExpenseApplication : Application() {
    private var settingsBackupListener:
            android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ExpenseApplication)
            modules(appModule)
        }

        // Schedule daily update check at 6 PM IST
        UpdateCheckWorker.scheduleNextCheck(this)
        FeatureNudgeWorker.ensureScheduled(this)

        // Clear update status if current version matches stored version
        val sharedPrefs = getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
        val latestSha = sharedPrefs.getString("latest_version_sha", "")
        if (latestSha == BuildConfig.GIT_COMMIT_HASH) {
            sharedPrefs.edit().putBoolean("update_available", false).apply()
        }

        settingsBackupListener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key in CloudSettingsBackupManager.backupPreferenceKeys) {
                    CloudSettingsBackupManager.backupAsync(this)
                }
            }
        sharedPrefs.registerOnSharedPreferenceChangeListener(settingsBackupListener)
    }
}
