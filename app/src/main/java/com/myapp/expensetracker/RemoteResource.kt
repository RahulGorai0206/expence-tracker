package com.myapp.expensetracker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A file that ships inside the APK but can be refreshed from the public repo at
 * runtime, so logic changes don't require an app release.
 *
 * Resolution order is always: downloaded override → bundled asset. A failed or
 * rejected download therefore never leaves the app worse off than the build it
 * shipped with.
 */
enum class RemoteResource(val assetName: String, val repoPath: String) {
    APPS_SCRIPT("apps-script.gs", "scripts/apps-script.gs"),
    EXTRACTION_RULES("extraction-rules.json", "rules/extraction-rules.json");

    /** Where a successfully downloaded copy is cached. */
    fun cacheFile(context: Context): File =
        File(context.filesDir, "remote_resources/$assetName")
}

object RemoteResourceLoader {

    private const val RAW_BASE =
        "https://raw.githubusercontent.com/RahulGorai0206/expense-tracker/main/"

    private const val MAX_BYTES = 2L * 1024 * 1024

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun rawUrl(resource: RemoteResource): String = RAW_BASE + resource.repoPath

    /** The bundled baseline that shipped with this build. */
    fun readBundled(context: Context, resource: RemoteResource): String =
        context.assets.open(resource.assetName).bufferedReader().use { it.readText() }

    /**
     * The copy currently in force: a previously downloaded update if one exists,
     * otherwise the bundled baseline.
     */
    fun readActive(context: Context, resource: RemoteResource): String {
        val cached = resource.cacheFile(context)
        if (cached.exists() && cached.length() > 0) {
            runCatching { return cached.readText() }
        }
        return readBundled(context, resource)
    }

    fun hasUpdate(context: Context, resource: RemoteResource): Boolean =
        resource.cacheFile(context).let { it.exists() && it.length() > 0 }

    /** Discards a downloaded update and reverts to the bundled baseline. */
    fun revertToBundled(context: Context, resource: RemoteResource) {
        runCatching { resource.cacheFile(context).delete() }
    }

    /**
     * Downloads [resource] and hands the text to [validate] before it is
     * persisted. Returning false from [validate] discards the download, so a
     * malformed file published upstream can never take effect.
     */
    suspend fun fetch(
        resource: RemoteResource,
        validate: (String) -> Boolean = { it.isNotBlank() }
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(rawUrl(resource)).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Server returned ${response.code}")
                }
                if (response.body.contentLength() > MAX_BYTES) {
                    error("File is unexpectedly large")
                }
                val text = response.body.string()
                if (text.isBlank()) error("Downloaded file was empty")
                if (!validate(text)) error("Downloaded file failed validation")
                text
            }
        }
    }

    fun persist(context: Context, resource: RemoteResource, content: String) {
        val file = resource.cacheFile(context)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
