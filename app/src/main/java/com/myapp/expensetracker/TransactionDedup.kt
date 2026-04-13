package com.myapp.expensetracker

import android.util.Log

/**
 * Centralized, cross-layer deduplication for transaction messages.
 *
 * The app has 3 independent detection layers that can all fire for the SAME message:
 *   1. SmsReceiver (SMS_RECEIVED broadcast)
 *   2. SmsMonitorService (ContentObserver on SMS database)
 *   3. TransactionNotificationListener (notification interception)
 *
 * This singleton ensures only the FIRST layer to detect a message actually processes it.
 * Dedup is keyed on the message body hash — different messages are never blocked.
 */
object TransactionDedup {

    private const val TAG = "TransactionDedup"
    private const val DEDUP_WINDOW_MS = 120_000L // 2 minutes

    // Maps message body hash → timestamp when it was first processed
    private val recentlyProcessed = LinkedHashMap<Int, Long>(64, 0.75f, true)

    /**
     * Checks if this message body was already processed by another detection layer.
     * If NOT a duplicate, automatically marks it as processed (atomic check-and-set).
     *
     * @param messageBody The full SMS/RCS message text
     * @return true if this is a duplicate (already processed), false if it's new
     */
    @Synchronized
    fun isDuplicate(messageBody: String): Boolean {
        val now = System.currentTimeMillis()

        // Evict expired entries
        recentlyProcessed.entries.removeAll { now - it.value > DEDUP_WINDOW_MS }

        val bodyHash = messageBody.hashCode()

        if (recentlyProcessed.containsKey(bodyHash)) {
            Log.d(
                TAG,
                "DUPLICATE detected — message already processed by another layer (hash=$bodyHash)"
            )
            return true
        }

        // Mark as processed
        recentlyProcessed[bodyHash] = now
        Log.d(
            TAG,
            "NEW message registered (hash=$bodyHash, active entries=${recentlyProcessed.size})"
        )
        return false
    }
}
