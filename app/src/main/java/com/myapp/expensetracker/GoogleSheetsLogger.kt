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

    suspend fun log(transaction: Transaction): String? {
        val loggerApi = api ?: return null
        val url = currentUrl ?: return null
        if (url.isBlank()) return null
        
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
                longitude = transaction.longitude
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
        if (url.isBlank()) return

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
                longitude = transaction.longitude
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun delete(transaction: Transaction) {
        val loggerApi = api ?: return
        val url = currentUrl ?: return
        val remoteId = transaction.remoteId ?: return
        if (url.isBlank()) return

        try {
            loggerApi.postAction(
                url = url,
                action = "delete",
                id = remoteId,
                status = "deleted" // Soft delete by default in script
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncFromCloud(context: Context) {
        val loggerApi = api ?: return
        val url = currentUrl ?: return
        if (url.isBlank()) return

        try {
            val response = loggerApi.getRecords(url)
            if (response.success && response.records != null) {
                val db = AppDatabase.getDatabase(context)
                val dao = db.transactionDao()
                
                val transactions = response.records.mapNotNull { remote ->
                    // Skip invalid records (e.g. if script returns garbage or missing fields)
                    if (remote.id.isNullOrBlank() || remote.amount == null || remote.amount == 0.0) return@mapNotNull null
                    
                    Transaction(
                        remoteId = remote.id,
                        sender = remote.sender ?: "",
                        amount = remote.amount,
                        date = remote.date ?: System.currentTimeMillis(),
                        body = remote.body ?: "",
                        category = remote.category ?: "Other",
                        status = remote.status ?: "Cleared",
                        type = remote.type ?: "automated",
                        latitude = remote.latitude,
                        longitude = remote.longitude,
                        syncStatus = "synced"
                    )
                }
                
                if (transactions.isNotEmpty()) {
                    dao.deleteAllTransactions()
                    transactions.forEach { dao.insert(it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logAsync(context: Context, transaction: Transaction, localId: Long) {
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val dao = db.transactionDao()
            try {
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
