package com.myapp.expensetracker

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

    suspend fun delete(transaction: Transaction) {
        val loggerApi = api ?: return
        val url = currentUrl ?: return
        val remoteId = transaction.remoteId ?: return
        val key = apiKey
        if (url.isBlank() || key.isNullOrBlank()) return

        try {
            loggerApi.postAction(
                url = url,
                action = "delete",
                id = remoteId,
                status = "deleted", // Soft delete by default in script
                apiKey = apiKey
            )
        } catch (e: Exception) {
            e.printStackTrace()
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
            val response = loggerApi.getRecords(url, apiKey = key)
            if (response.success && response.records != null) {
                val db = AppDatabase.getDatabase(context)
                val dao = db.transactionDao()
                
                val rawRecords = response.records.filter { 
                    !it.id.isNullOrBlank() && it.amount != null && it.amount != 0.0 && it.status != "deleted"
                }
                
                val total = rawRecords.size
                if (total > 0) {
                    onProgress(0, total)
                    dao.deleteAllTransactions()
                    rawRecords.forEachIndexed { index, remote ->
                        val transaction = Transaction(
                            remoteId = remote.id,
                            sender = remote.sender ?: "",
                            amount = remote.amount ?: 0.0,
                            date = remote.date ?: System.currentTimeMillis(),
                            body = remote.body ?: "",
                            category = remote.category ?: "Other",
                            status = remote.status ?: "Cleared",
                            type = remote.type ?: "automated",
                            latitude = remote.latitude,
                            longitude = remote.longitude,
                            syncStatus = "synced"
                        )
                        dao.insert(transaction)
                        onProgress(index + 1, total)
                        kotlinx.coroutines.delay(20) // Give UI time to breathe
                    }
                    null // Success
                } else {
                    "No valid records found to restore"
                }
            } else {
                response.error ?: "Failed to fetch records"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Connection error: ${e.localizedMessage}"
        }
    }

    fun logAsync(context: Context, transaction: Transaction, localId: Long) {
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val dao = db.transactionDao()
            
            // Mark as pending immediately to show loading UI
            dao.updateSyncStatus(localId.toInt(), transaction.remoteId, "pending")

            try {
                // Fetch the latest transaction from DB to get the most current remoteId
                val currentTransaction = dao.getTransactionSync(localId.toInt())
                
                // Check if already synced or has remoteId
                if (currentTransaction != null && !currentTransaction.remoteId.isNullOrBlank()) {
                    dao.updateSyncStatus(localId.toInt(), currentTransaction.remoteId, "synced")
                    return@launch
                }

                val remoteId = log(transaction)
                if (remoteId != null) {
                    dao.updateSyncStatus(localId.toInt(), remoteId, "synced")
                } else {
                    dao.updateSyncStatus(localId.toInt(), null, "failed")
                }
            } catch (e: Exception) {
                dao.updateSyncStatus(localId.toInt(), null, "failed")
                e.printStackTrace()
            }
        }
    }
}
