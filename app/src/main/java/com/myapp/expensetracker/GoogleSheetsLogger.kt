package com.myapp.expensetracker

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object GoogleSheetsLogger {
    private var api: GoogleSheetsApi? = null
    private var currentUrl: String? = null
    private var apiKey: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val DUMMY_BASE_URL = "https://script.google.com/"

    fun init(context: Context) {
        val sharedPrefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val url = sharedPrefs.getString("script_url", "") ?: ""
        apiKey = sharedPrefs.getString("api_key", "") ?: ""
        updateUrl(url)
    }

    fun updateUrl(url: String) {
        currentUrl = url
        if (api == null) {
            api = Retrofit.Builder()
                .baseUrl(DUMMY_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
                .create(GoogleSheetsApi::class.java)
        }
    }

    fun updateApiKey(key: String) {
        apiKey = key
    }

    fun isConfigured(): Boolean {
        return !currentUrl.isNullOrBlank() && !apiKey.isNullOrBlank()
    }

    suspend fun testConnection(url: String, key: String): String? {
        if (api == null) {
            api = Retrofit.Builder()
                .baseUrl(DUMMY_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
                .create(GoogleSheetsApi::class.java)
        }
        return try {
            val response = api?.getRecords(url, apiKey = key)
            if (response?.success == true) {
                null // Success
            } else {
                response?.error ?: "Invalid API Key or Script URL"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Connection failed: ${e.localizedMessage}"
        }
    }

    suspend fun log(transaction: Transaction): String? {
        val loggerApi = api ?: return null
        val url = currentUrl ?: return null
        val key = apiKey
        if (url.isBlank() || key.isNullOrBlank()) return null
        
        // Check if transaction already has a remoteId to prevent duplicates
        if (!transaction.remoteId.isNullOrBlank()) {
            return transaction.remoteId
        }

        return try {
            val response = loggerApi.postAction(
                url = url,
                action = "create",
                amount = transaction.amount,
                sender = transaction.sender,
                date = transaction.date,
                body = transaction.body,
                category = transaction.category,
                tag = transaction.tag,
                status = transaction.status,
                type = transaction.type,
                latitude = transaction.latitude,
                longitude = transaction.longitude,
                apiKey = apiKey
            )
            if (response.success) response.records?.firstOrNull()?.id else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun update(transaction: Transaction) {
        val loggerApi = api ?: return
        val url = currentUrl ?: return
        val remoteId = transaction.remoteId ?: return
        val key = apiKey
        if (url.isBlank() || key.isNullOrBlank()) return

        try {
            loggerApi.postAction(
                url = url,
                action = "update",
                id = remoteId,
                amount = transaction.amount,
                sender = transaction.sender,
                date = transaction.date,
                body = transaction.body,
                category = transaction.category,
                tag = transaction.tag,
                status = transaction.status,
                type = transaction.type,
                latitude = transaction.latitude,
                longitude = transaction.longitude,
                apiKey = apiKey
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun delete(transaction: Transaction): Boolean {
        val loggerApi = api ?: return false
        val url = currentUrl ?: return false
        val remoteId =
            transaction.remoteId ?: return true // Already "deleted" locally, no remote to sync
        val key = apiKey
        if (url.isBlank() || key.isNullOrBlank()) return false

        return try {
            val response = loggerApi.postAction(
                url = url,
                action = "delete",
                id = remoteId,
                status = "deleted", // Soft delete by default in script
                apiKey = apiKey
            )
            response.success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun backupSettings(context: Context): String? {
        val loggerApi = api ?: return "Sync not configured"
        val url = currentUrl ?: return "Script URL not set"
        val key = apiKey
        if (url.isBlank()) return "Script URL is empty"
        if (key.isNullOrBlank()) return "API Key is empty"

        return try {
            val backup = CloudSettingsBackupManager.capture(context)
            val response = loggerApi.syncSettings(
                url = url,
                mode = "write",
                settingsJson = Gson().toJson(backup),
                apiKey = key
            )
            if (response.success) null else response.error ?: "Failed to back up settings"
        } catch (e: Exception) {
            e.printStackTrace()
            "Settings backup error: ${e.localizedMessage}"
        }
    }

    suspend fun restoreSettingsFromCloud(context: Context): String? {
        val loggerApi = api ?: return "Sync not configured"
        val url = currentUrl ?: return "Script URL not set"
        val key = apiKey
        if (url.isBlank()) return "Script URL is empty"
        if (key.isNullOrBlank()) return "API Key is empty"

        return try {
            val response = loggerApi.syncSettings(url = url, mode = "read", apiKey = key)
            android.util.Log.d(
                "CloudBackup",
                "Restore response: success=${response.success}, settings=${response.settings != null}"
            )
            if (!response.success) {
                response.error ?: "Failed to restore settings"
            } else {
                response.settings?.let { CloudSettingsBackupManager.apply(context, it) }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Settings restore error: ${e.localizedMessage}"
        }
    }

    suspend fun syncFromCloud(
        context: Context,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): String? {
        val loggerApi = api ?: return "Sync not configured"
        val url = currentUrl ?: return "Script URL not set"
        val key = apiKey
        if (url.isBlank()) return "Script URL is empty"
        if (key.isNullOrBlank()) return "API Key is empty"

        return try {
            val settingsError = restoreSettingsFromCloud(context)
            if (settingsError != null) {
                android.util.Log.e("CloudSync", "Settings restore failed: $settingsError")
            }

            val response = loggerApi.getRecords(url, apiKey = key)
            if (response.success && response.records != null) {
                val db = AppDatabase.getDatabase(context)
                val dao = db.transactionDao()

                val rawRecords = response.records.filter {
                    !it.id.isNullOrBlank() && it.amount != null && it.amount != 0.0
                }

                val total = rawRecords.size
                if (total > 0) {
                    onProgress(0, total)

                    // Merge, never replace. The old implementation wiped the
                    // table first and re-inserted row by row outside any
                    // transaction: a crash mid-loop truncated the ledger for
                    // good, and rows that had never been uploaded (syncStatus
                    // 'failed'/'pending') were destroyed rather than kept.
                    db.withTransaction {
                        val matcher = LocalTransactionMatcher(dao.getAllTransactionsList())

                        rawRecords.forEachIndexed { index, remote ->
                            val incoming = remote.toTransaction()
                            val existing = matcher.match(remote.id, incoming)

                            when {
                                // Remote tombstone: mirror it onto the local row.
                                remote.status == "deleted" -> {
                                    if (existing != null) {
                                        dao.insert(
                                            existing.copy(
                                                remoteId = remote.id,
                                                status = "deleted",
                                                syncStatus = "synced"
                                            )
                                        )
                                    }
                                }

                                // Known row — update in place, keeping its local id.
                                existing != null -> dao.insert(incoming.copy(id = existing.id))

                                // New to this device.
                                else -> dao.insert(incoming)
                            }
                            onProgress(index + 1, total)
                        }
                    }

                    enqueueWidgetUpdate(context)
                    null // Success
                } else {
                    settingsError ?: "No valid records found to restore"
                }
            } else {
                response.error ?: "Failed to fetch records"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Connection error: ${e.localizedMessage}"
        }
    }

    /**
     * The sheet may store debits as positive numbers; the app's internal
     * convention is that spending is negative.
     */
    private fun RemoteTransaction.toTransaction(): Transaction {
        val remoteAmount = amount ?: 0.0
        val remoteType = type ?: "automated"
        val isDebit = remoteType.lowercase() != "credit" &&
                !(body?.lowercase()?.contains("credited") ?: false)
        val normalizedAmount = if (isDebit && remoteAmount > 0) -remoteAmount else remoteAmount
        val safeBody = body.orEmpty()

        return Transaction(
            remoteId = id,
            sender = sender.orEmpty(),
            amount = normalizedAmount,
            date = date ?: System.currentTimeMillis(),
            body = safeBody,
            bodyHash = safeBody.hashCode(),
            category = category ?: "Other",
            tag = tag.orEmpty(),
            status = status ?: "Cleared",
            type = remoteType,
            latitude = latitude,
            longitude = longitude,
            syncStatus = "synced"
        )
    }

    fun logAsync(context: Context, transaction: Transaction, localId: Long) {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val dao = db.transactionDao()
            
            // Mark as pending immediately to show loading UI
            dao.updateSyncStatus(localId.toInt(), transaction.remoteId, "pending")

            val pendingAndFailed = dao.getPendingOrFailedTransactions(localId.toInt())
            val workManager = androidx.work.WorkManager.getInstance(context)

            pendingAndFailed.forEach { pendingTx ->
                val pendingWorkRequest =
                    androidx.work.OneTimeWorkRequestBuilder<com.myapp.expensetracker.worker.SheetsSyncWorker>()
                        .setConstraints(constraints)
                        .setInputData(androidx.work.workDataOf("TRANSACTION_ID" to pendingTx.id.toLong()))
                        .build()
                workManager.enqueueUniqueWork(
                    "sync_${pendingTx.id}",
                    androidx.work.ExistingWorkPolicy.KEEP,
                    pendingWorkRequest
                )
            }
        }

        val workRequest =
            androidx.work.OneTimeWorkRequestBuilder<com.myapp.expensetracker.worker.SheetsSyncWorker>()
                .setConstraints(constraints)
                .setInputData(androidx.work.workDataOf("TRANSACTION_ID" to localId))
                .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "sync_$localId",
            androidx.work.ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
}
