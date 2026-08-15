package com.myapp.expensetracker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val action = intent.action
        val notificationId = intent.getIntExtra(TransactionApproval.EXTRA_NOTIFICATION_ID, 0)
        val pendingId = intent.getLongExtra(TransactionApproval.EXTRA_PENDING_ID, -1L)

        Log.d("NotificationReceiver", "Action: $action, notificationId: $notificationId, pendingId: $pendingId")

        // Dismiss immediately so the UI feels responsive; the pipeline below
        // cancels the timeout alarm and clears the durable row.
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        scope.launch {
            try {
                when (action) {
                    TransactionApproval.ACTION_DENY -> {
                        if (pendingId > 0) {
                            TransactionApproval.discard(context, pendingId, notificationId)
                        }
                    }

                    TransactionApproval.ACTION_ACCEPT,
                    TransactionApproval.ACTION_TIMEOUT -> {
                        val autoCleared = action == TransactionApproval.ACTION_TIMEOUT
                        if (pendingId > 0) {
                            TransactionApproval.commit(context, pendingId, autoCleared)
                        } else {
                            commitFromLegacyExtras(context, intent, autoCleared)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Error processing transaction", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Notifications posted by a build before pending_transactions existed carry
     * the whole payload in extras. Honour them so an in-flight upgrade doesn't
     * drop a transaction the user is looking at.
     */
    private suspend fun commitFromLegacyExtras(
        context: Context,
        intent: Intent,
        autoCleared: Boolean
    ) {
        val amount = intent.getDoubleExtra("amount", 0.0)
        if (amount == 0.0) return

        val body = intent.getStringExtra("body").orEmpty()
        val transaction = Transaction(
            sender = intent.getStringExtra("sender") ?: "Unknown",
            amount = amount,
            date = intent.getLongExtra("date", System.currentTimeMillis()),
            body = body,
            bodyHash = body.hashCode(),
            category = intent.getStringExtra("category") ?: "Other",
            status = if (autoCleared) "Auto-Cleared" else "Cleared",
            type = "automated",
            latitude = intent.getDoubleExtra("latitude", 0.0).takeIf { it != 0.0 },
            longitude = intent.getDoubleExtra("longitude", 0.0).takeIf { it != 0.0 },
            syncStatus = "pending"
        )

        Log.d("NotificationReceiver", "Committing via legacy extras path")
        TransactionApproval.commitLegacy(context, transaction)
    }
}
