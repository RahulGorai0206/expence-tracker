package com.myapp.expensetracker

import android.util.Log

/**
 * Logging for messages that would otherwise put user content — bank SMS text,
 * locations, credentials — into logcat.
 *
 * Logcat is readable by anyone with ADB access and is bundled verbatim into
 * `adb bugreport`, which users are routinely asked to share. Anything sensitive
 * must therefore go through here rather than [Log] directly.
 *
 * The message is a lambda and the functions are `inline`, so in a release build
 * the string is never assembled at all — not built and discarded. Note this is
 * a runtime check, not build-time removal: R8 is disabled for release, so
 * nothing is stripped from the APK.
 */
object AppLog {

    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, message())
    }

    inline fun w(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.w(tag, message())
    }

    /**
     * Errors are kept in release — a throwable's stack trace is diagnostic, not
     * user content. Pass only non-sensitive text as [message].
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
