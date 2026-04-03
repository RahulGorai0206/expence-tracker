package com.myapp.expensetracker

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

object GoogleSheetsLogger {
    // URL updated with your deployment ID
    private const val BASE_URL = "https://script.google.com/macros/s/AKfycbxyiomOjiR4YwFhlVKv_HRv5FekZjPdQ58QN-NEgWRtvOhUmKdU8m9rFHULj4qThDx60A/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()
        .create(GoogleSheetsApi::class.java)

    suspend fun log(transaction: Transaction) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val amountValue = if (transaction.amount < 0) -transaction.amount else transaction.amount
            val type = if (transaction.amount < 0) "Expense" else "Income"
            
            api.logTransaction(
                sender = transaction.sender,
                amount = "%.2f".format(amountValue),
                date = dateFormat.format(Date(transaction.date)),
                body = transaction.body,
                type = type
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
