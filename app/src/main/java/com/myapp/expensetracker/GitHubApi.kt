package com.myapp.expensetracker

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface GitHubApi {
    @GET("repos/RahulGorai0206/expense-tracker/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease

    @GET("repos/RahulGorai0206/expense-tracker/git/ref/tags/{tag}")
    suspend fun getTagRef(@Path("tag") tag: String): GitHubTagRef

    // Resolves an annotated tag object (type=tag) to its underlying commit SHA
    @GET("repos/RahulGorai0206/expense-tracker/git/tags/{sha}")
    suspend fun getAnnotatedTagObject(@Path("sha") sha: String): GitHubAnnotatedTag

    @GET("repos/RahulGorai0206/expense-tracker/commits/{ref}")
    suspend fun getCommit(@Path("ref") ref: String): GitHubCommit
}

data class GitHubRelease(
    val tag_name: String,
    val html_url: String,
    val name: String,
    val body: String,
    val target_commitish: String = ""
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

// Used when dereferencing an annotated tag object to its commit
data class GitHubAnnotatedTag(
    val sha: String,
    val `object`: GitHubObject
)

data class GitHubCommit(
    val sha: String
)
