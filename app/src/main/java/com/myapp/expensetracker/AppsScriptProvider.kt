package com.myapp.expensetracker

import android.content.Context

/**
 * Supplies the Google Apps Script the user pastes into their spreadsheet.
 *
 * The script itself lives at `scripts/apps-script.gs` in the repo rather than
 * inside a Kotlin string, so it can be read and reviewed on GitHub and refreshed
 * at runtime without an app release. A copy is bundled in assets as the offline
 * fallback.
 */
object AppsScriptProvider {

    private const val SHEET_ID_PLACEHOLDER = "__SPREADSHEET_ID__"

    /** The script currently in force, with the user's sheet id substituted in. */
    fun build(context: Context, sheetId: String): String =
        RemoteResourceLoader.readActive(context, RemoteResource.APPS_SCRIPT)
            .replace(SHEET_ID_PLACEHOLDER, sheetId)

    fun sourceUrl(): String = RemoteResourceLoader.rawUrl(RemoteResource.APPS_SCRIPT)

    fun isUpdated(context: Context): Boolean =
        RemoteResourceLoader.hasUpdate(context, RemoteResource.APPS_SCRIPT)

    sealed interface UpdateOutcome {
        data object Updated : UpdateOutcome
        data object AlreadyCurrent : UpdateOutcome
        data class Failed(val reason: String) : UpdateOutcome
    }

    /** Refreshes the script from the repo, rejecting anything that isn't it. */
    suspend fun update(context: Context): UpdateOutcome {
        val appContext = context.applicationContext
        val active = RemoteResourceLoader.readActive(appContext, RemoteResource.APPS_SCRIPT)

        val fetched = RemoteResourceLoader.fetch(RemoteResource.APPS_SCRIPT) { text ->
            // Cheap sanity check: it must still be the sync script, with the
            // placeholder intact and the entry point present.
            text.contains(SHEET_ID_PLACEHOLDER) && text.contains("function doPost")
        }

        val text = fetched.getOrElse { error ->
            return UpdateOutcome.Failed(error.message ?: "Could not download the script.")
        }

        if (text == active) return UpdateOutcome.AlreadyCurrent

        RemoteResourceLoader.persist(appContext, RemoteResource.APPS_SCRIPT, text)
        return UpdateOutcome.Updated
    }

    fun revert(context: Context) =
        RemoteResourceLoader.revertToBundled(context.applicationContext, RemoteResource.APPS_SCRIPT)
}
