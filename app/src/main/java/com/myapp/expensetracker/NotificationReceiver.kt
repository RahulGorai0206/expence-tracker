package com.myapp.expensetracker

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
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
        val notificationId = intent.getIntExtra("notificationId", 0)
        
        val body = intent.getStringExtra("body") ?: ""
        val category = intent.getStringExtra("category") ?: "Other"
        val latitude = intent.getDoubleExtra("latitude", 0.0).takeIf { it != 0.0 }
        val longitude = intent.getDoubleExtra("longitude", 0.0).takeIf { it != 0.0 }
        
        Log.d("NotificationReceiver", "Action: $action, ID: $notificationId, Body: $body")

        // Dismiss notification immediately
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        scope.launch {
            try {
                // Cancel the timeout alarm if the user took action (Accept or Deny)
                if (action == "ACCEPT_TRANSACTION" || action == "DENY_TRANSACTION") {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val timeoutIntent = Intent(context, NotificationReceiver::class.java).apply {
                        this.action = "TIMEOUT_TRANSACTION"
                    }
                    val timeoutPendingIntent = PendingIntent.getBroadcast(
                        context, 
                        notificationId + 2, 
                        timeoutIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.cancel(timeoutPendingIntent)
                }

                if (action == "ACCEPT_TRANSACTION" || action == "TIMEOUT_TRANSACTION") {
                    val sender = intent.getStringExtra("sender") ?: "Unknown"
                    val amount = intent.getDoubleExtra("amount", 0.0)
                    val date = intent.getLongExtra("date", System.currentTimeMillis())
                    val bodyFromIntent = intent.getStringExtra("body") ?: ""
                    val categoryFromIntent = intent.getStringExtra("category") ?: "Other"
                    val latitudeFromIntent = intent.getDoubleExtra("latitude", 0.0).takeIf { it != 0.0 }
                    val longitudeFromIntent = intent.getDoubleExtra("longitude", 0.0).takeIf { it != 0.0 }

                    if (amount != 0.0) {
                        val db = AppDatabase.getDatabase(context)

                        // DB-level dedup: last line of defense against duplicate entries
                        val dupeCount =
                            db.transactionDao().checkDuplicateByBody(amount, bodyFromIntent)
                        if (dupeCount > 0) {
                            Log.d(
                                "NotificationReceiver",
                                "Duplicate detected in DB — skipping insert for $amount from $sender"
                            )
                        } else {
                            val transaction = Transaction(
                                sender = sender,
                                amount = amount,
                                date = date,
                                body = bodyFromIntent,
                                category = categoryFromIntent,
                                status = if (action == "TIMEOUT_TRANSACTION") "Auto-Cleared" else "Cleared",
                                type = "automated",
                                latitude = latitudeFromIntent,
                                longitude = longitudeFromIntent,
                                syncStatus = "pending"
                            )

                            val localId = db.transactionDao().insertAndReturnId(transaction)
                            Log.d(
                                "NotificationReceiver",
                                "Saved transaction locally: $amount from $sender"
                            )

                            // Update widget
                            updateExpenseWidget(context)

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val remoteId = GoogleSheetsLogger.log(transaction)
                                    if (remoteId != null) {
                                        db.transactionDao()
                                            .updateSyncStatus(localId.toInt(), remoteId, "synced")
                                    } else {
                                        db.transactionDao()
                                            .updateSyncStatus(localId.toInt(), null, "failed")
                                    }
                                } catch (e: Exception) {
                                    db.transactionDao()
                                        .updateSyncStatus(localId.toInt(), null, "failed")
                                    e.printStackTrace()
                                }
                            }
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
}
