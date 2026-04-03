package com.myapp.expensetracker

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface GoogleSheetsApi {
    @FormUrlEncoded
    @POST("exec") // This is the standard endpoint for Google Apps Script Web Apps
    suspend fun logTransaction(
        @Field("sender") sender: String,
        @Field("amount") amount: Double,
        @Field("date") date: String,
        @Field("body") body: String
    )
}
