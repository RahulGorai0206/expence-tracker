package com.myapp.expensetracker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.annotation.SuppressLint

class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val extractor = TransactionExtractor()

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val pendingResult = goAsync()
            
            scope.launch {
                try {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                    val location = try {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                    } catch (e: Exception) {
                        null
                    }

                    for (sms in messages) {
                        val body = sms.messageBody
                        val sender = sms.displayOriginatingAddress ?: "Unknown"
                        val timestamp = sms.timestampMillis

                        val transaction = extractor.extractTransaction(body, sender, timestamp)?.copy(
                            latitude = location?.latitude,
                            longitude = location?.longitude
                        )
                        if (transaction != null) {
                            showTransactionNotification(context, transaction)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showTransactionNotification(context: Context, transaction: Transaction) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "transaction_alerts"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Transaction Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = (transaction.date % Int.MAX_VALUE).toInt()

        val acceptIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "ACCEPT_TRANSACTION"
            putExtra("notificationId", notificationId)
            putExtra("sender", transaction.sender)
            putExtra("amount", transaction.amount)
            putExtra("date", transaction.date)
            putExtra("body", transaction.body)
            putExtra("category", transaction.category)
            putExtra("latitude", transaction.latitude ?: 0.0)
            putExtra("longitude", transaction.longitude ?: 0.0)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(context, notificationId, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val denyIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "DENY_TRANSACTION"
            putExtra("notificationId", notificationId)
        }
        val denyPendingIntent = PendingIntent.getBroadcast(context, notificationId + 1, denyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val triggerAt = System.currentTimeMillis() + 30000

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Transaction: ₹${transaction.amount}")
            .setContentText("From ${transaction.sender}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(triggerAt)
            .addAction(android.R.drawable.ic_input_add, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Deny", denyPendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timeoutIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "TIMEOUT_TRANSACTION"
            putExtra("notificationId", notificationId)
            putExtra("sender", transaction.sender)
            putExtra("amount", transaction.amount)
            putExtra("date", transaction.date)
            putExtra("body", transaction.body)
            putExtra("category", transaction.category)
            putExtra("latitude", transaction.latitude ?: 0.0)
            putExtra("longitude", transaction.longitude ?: 0.0)
        }
        val timeoutPendingIntent = PendingIntent.getBroadcast(context, notificationId + 2, timeoutIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, timeoutPendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, timeoutPendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, timeoutPendingIntent)
        }
    }
}
