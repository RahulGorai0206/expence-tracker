package com.myapp.expensetracker

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface GoogleSheetsApi {
    @FormUrlEncoded
    @POST
    suspend fun postAction(
        @Url url: String,
        @Field("action") action: String,
        @Field("id") id: String? = null,
        @Field("amount") amount: Double? = null,
        @Field("sender") sender: String? = null,
        @Field("date") date: Long? = null,
        @Field("body") body: String? = null,
        @Field("category") category: String? = null,
        @Field("status") status: String? = null,
        @Field("type") type: String? = null,
        @Field("latitude") latitude: Double? = null,
        @Field("longitude") longitude: Double? = null,
        @Field("api_key") apiKey: String? = null
    ): GoogleSheetResponse

    @FormUrlEncoded
    @POST
    suspend fun getRecords(
        @Url url: String,
        @Field("action") action: String = "read",
        @Field("api_key") apiKey: String? = null
    ): GoogleSheetResponse
}

data class GoogleSheetResponse(
    val success: Boolean,
    val action: String? = null,
    val records: List<RemoteTransaction>? = null,
    val error: String? = null
)

data class RemoteTransaction(
    val id: String?,
    val created_at: String?,
    val updated_at: String?,
    val status: String?,
    val amount: Double?,
    val sender: String?,
    val date: Long?,
    val body: String?,
    val category: String?,
    val type: String?,
    val latitude: Double?,
    val longitude: Double?
)
