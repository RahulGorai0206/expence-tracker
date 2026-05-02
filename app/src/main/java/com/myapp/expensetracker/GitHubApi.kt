package com.myapp.expensetracker

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface GitHubApi {
    @GET("repos/RahulGorai0206/expense-tracker/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease

    @GET("repos/RahulGorai0206/expense-tracker/git/ref/tags/{tag}")
    suspend fun getTagRef(@Path("tag") tag: String): GitHubTagRef

    @GET("repos/RahulGorai0206/expense-tracker/commits/{ref}")
    suspend fun getCommit(@Path("ref") ref: String): GitHubCommit
}

data class GitHubRelease(
    val tag_name: String,
    val html_url: String,
    val name: String,
    val body: String
)

data class GitHubTagRef(
    val ref: String,
    val `object`: GitHubObject
)

data class GitHubObject(
    val sha: String,
    val type: String,
    val url: String
)

data class GitHubCommit(
    val sha: String
)
