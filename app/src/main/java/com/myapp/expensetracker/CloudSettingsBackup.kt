package com.myapp.expensetracker

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CloudSettingsBackup(
    val version: Int = 1,
    val updated_at: Long = System.currentTimeMillis(),
    val budget_month_key: String? = null,
    val budget_amount: Double? = null,
    val budget_monthly: Boolean? = null,
    val track_only_debits: Boolean? = null,
    val ignore_cc_bills: Boolean? = null,
    val background_monitoring: Boolean? = null,
    val follow_system_theme: Boolean? = null,
    val dark_theme: Boolean? = null,
    val cloud_sync: Boolean? = null
)

object CloudSettingsBackupManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val backupPreferenceKeys = setOf(
        "budget_monthly",
        "track_only_debits",
        "ignore_cc_bills",
        "background_monitoring",
        "follow_system_theme",
        "dark_theme",
        "cloud_sync"
    )

    fun backupAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            GoogleSheetsLogger.init(appContext)
            GoogleSheetsLogger.backupSettings(appContext)
        }
    }

    suspend fun capture(context: Context): CloudSettingsBackup {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val db = AppDatabase.getDatabase(context)
        val monthKey = currentMonthKey()
        val budget = db.monthlyBudgetDao().getEffectiveBudget(monthKey)

        return CloudSettingsBackup(
            budget_month_key = budget?.monthKey,
            budget_amount = budget?.amount,
            budget_monthly = prefs.getBoolean("budget_monthly", true),
            track_only_debits = prefs.getBoolean("track_only_debits", false),
            ignore_cc_bills = prefs.getBoolean("ignore_cc_bills", false),
            background_monitoring = prefs.getBoolean("background_monitoring", true),
            follow_system_theme = prefs.getBoolean("follow_system_theme", true),
            dark_theme = prefs.getBoolean("dark_theme", true),
            cloud_sync = GoogleSheetsLogger.isConfigured()
        )
    }

    suspend fun apply(context: Context, backup: CloudSettingsBackup) {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

        prefs.edit {
            backup.budget_monthly?.let { putBoolean("budget_monthly", it) }
            backup.track_only_debits?.let { putBoolean("track_only_debits", it) }
            backup.ignore_cc_bills?.let { putBoolean("ignore_cc_bills", it) }
            backup.follow_system_theme?.let { putBoolean("follow_system_theme", it) }
            backup.dark_theme?.let { putBoolean("dark_theme", it) }
            backup.cloud_sync?.let { putBoolean("cloud_sync", it) }
        }

        backup.background_monitoring?.let { enabled ->
            SmsMonitorService.setEnabled(context, enabled)
        }

        val amount = backup.budget_amount
        if (amount != null && amount > 0.0) {
            val monthKey = backup.budget_month_key?.takeIf { it.isNotBlank() } ?: currentMonthKey()
            AppDatabase.getDatabase(context).monthlyBudgetDao().upsert(
                MonthlyBudget(
                    monthKey = monthKey,
                    amount = amount
                )
            )
            prefs.edit { putFloat("budget", amount.toFloat()) }
        }
    }

    private fun currentMonthKey(): String {
        return SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    }
}
