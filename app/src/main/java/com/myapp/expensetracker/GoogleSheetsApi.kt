package com.myapp.expensetracker

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Url

interface GoogleSheetsApi {
    @FormUrlEncoded
    @POST
    suspend fun logTransaction(
        @Url url: String,
        @Field("sender") sender: String,
        @Field("amount") amount: String,
        @Field("date") date: String,
        @Field("body") body: String,
        @Field("type") type: String
    )
}
