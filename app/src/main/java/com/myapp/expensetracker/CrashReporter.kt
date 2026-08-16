package com.myapp.expensetracker

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device crash log.
 *
 * The app advertises a zero-server policy, so shipping stack traces to
 * Crashlytics or Sentry would contradict its own privacy promise. Instead
 * crashes are appended to a local file the user can read and choose to share.
 */
object CrashReporter {

    private const val LOG_FILE_NAME = "crash_log.txt"
    private const val MAX_LOG_BYTES = 256 * 1024
    private const val SEPARATOR = "\n────────────────────────────────────────\n"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                record(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Never let the reporter mask the original crash.
            }
            // Always hand back so the process still dies the way Android expects.
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun record(context: Context, thread: Thread, throwable: Throwable) {
        val stackTrace = StringWriter().also { writer ->
            throwable.printStackTrace(PrintWriter(writer))
        }.toString()

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = buildString {
            append(SEPARATOR)
            append("Time:    $timestamp\n")
            append("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            append("Commit:  ${BuildConfig.GIT_COMMIT_HASH.take(8)}\n")
            append("Device:  ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("Thread:  ${thread.name}\n\n")
            append(stackTrace)
        }

        val file = logFile(context)
        file.appendText(entry)
        trimIfOversized(file)
    }

    fun logFile(context: Context): File = File(context.filesDir, LOG_FILE_NAME)

    fun hasCrashes(context: Context): Boolean =
        logFile(context).let { it.exists() && it.length() > 0 }

    /** Most recent entry first, so the newest crash is what the user sees. */
    fun readReport(context: Context): String {
        val file = logFile(context)
        if (!file.exists()) return ""
        return runCatching {
            file.readText()
                .split(SEPARATOR)
                .filter { it.isNotBlank() }
                .reversed()
                .joinToString(SEPARATOR)
        }.getOrElse { "Could not read the crash log." }
    }

    fun crashCount(context: Context): Int {
        val file = logFile(context)
        if (!file.exists()) return 0
        return runCatching {
            file.readText().split(SEPARATOR).count { it.isNotBlank() }
        }.getOrDefault(0)
    }

    fun clear(context: Context) {
        runCatching { logFile(context).delete() }
    }

    /** Keeps the newest entries when the file outgrows [MAX_LOG_BYTES]. */
    private fun trimIfOversized(file: File) {
        if (file.length() <= MAX_LOG_BYTES) return
        runCatching {
            val entries = file.readText().split(SEPARATOR).filter { it.isNotBlank() }
            val kept = entries.takeLast(10)
            file.writeText(kept.joinToString(SEPARATOR, prefix = SEPARATOR))
        }
    }
}
