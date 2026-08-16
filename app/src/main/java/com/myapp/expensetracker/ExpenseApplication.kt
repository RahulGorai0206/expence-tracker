package com.myapp.expensetracker

import android.app.Application
import com.myapp.expensetracker.di.appModule
import com.myapp.expensetracker.worker.FeatureNudgeWorker
import com.myapp.expensetracker.worker.UpdateCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ExpenseApplication : Application() {
    private var settingsBackupListener:
            android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // Installed first so it captures failures in the rest of startup.
        CrashReporter.install(this)

        startKoin {
            androidContext(this@ExpenseApplication)
            modules(appModule)
        }

        // Schedule daily update check at 6 PM IST
        UpdateCheckWorker.scheduleNextCheck(this)
        FeatureNudgeWorker.ensureScheduled(this)

        // Recover transactions whose approval window was cut short by a reboot,
        // force-stop or a dropped alarm.
        applicationScope.launch {
            try {
                TransactionApproval.reconcileAbandoned(this@ExpenseApplication)
            } catch (e: Exception) {
                android.util.Log.e("ExpenseApplication", "Pending reconciliation failed", e)
            }
        }

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
