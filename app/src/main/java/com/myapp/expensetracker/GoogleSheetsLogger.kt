package com.myapp.expensetracker

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

object GoogleSheetsLogger {
    private var api: GoogleSheetsApi? = null
    private var currentUrl: String? = null

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

    suspend fun log(transaction: Transaction) {
        val loggerApi = api ?: return
        val url = currentUrl ?: return
        if (url.isBlank()) return
        
        try {
            // Ensure we use the absolute value as requested by the user's script logic
            val amountValue = if (transaction.amount < 0) -transaction.amount else transaction.amount
            
            loggerApi.logTransaction(
                url = url,
                sender = "", // Not used in user's new script
                amount = "%.2f".format(amountValue),
                date = "", // Not used in user's new script
                body = "", // Not used in user's new script
                type = ""  // Not used in user's new script
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
