package com.myapp.expensetracker

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val notificationId = intent.getIntExtra("notificationId", 0)
        
        // Dismiss notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        // Cancel the timeout alarm if the user took action (Accept or Deny)
        if (action == "ACCEPT_TRANSACTION" || action == "DENY_TRANSACTION") {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val timeoutIntent = Intent(context, NotificationReceiver::class.java).apply {
                this.action = "TIMEOUT_TRANSACTION"
            }
            // Use the same request code (notificationId + 2) used in SmsReceiver
            val timeoutPendingIntent = PendingIntent.getBroadcast(
                context, 
                notificationId + 2, 
                timeoutIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(timeoutPendingIntent)
        }

        if (action == "ACCEPT_TRANSACTION" || action == "TIMEOUT_TRANSACTION") {
            val sender = intent.getStringExtra("sender") ?: ""
            val amount = intent.getDoubleExtra("amount", 0.0)
            val date = intent.getLongExtra("date", 0L)
            val body = intent.getStringExtra("body") ?: ""
            val category = intent.getStringExtra("category") ?: "Other"
            val latitude = intent.getDoubleExtra("latitude", 0.0).takeIf { it != 0.0 }
            val longitude = intent.getDoubleExtra("longitude", 0.0).takeIf { it != 0.0 }

            val transaction = Transaction(
                sender = sender,
                amount = amount,
                date = date,
                body = body,
                category = category,
                status = "Cleared",
                latitude = latitude,
                longitude = longitude
            )

            scope.launch {
                val db = AppDatabase.getDatabase(context)
                db.transactionDao().insert(transaction)
                GoogleSheetsLogger.log(transaction)
            }
        }
        // If "DENY_TRANSACTION", we just do nothing (notification is already dismissed)
    }
}
