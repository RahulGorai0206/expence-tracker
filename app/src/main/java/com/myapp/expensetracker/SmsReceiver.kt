package com.myapp.expensetracker

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val extractor = TransactionExtractor()

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return
            
            val pendingResult = goAsync()
            
            scope.launch {
                try {
                    // 1. Combine multi-part SMS messages to ensure full body is captured
                    val fullBody = messages.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
                    val firstSms = messages[0]
                    val sender = firstSms.displayOriginatingAddress ?: "Unknown"
                    val timestamp = firstSms.timestampMillis

                    Log.d("SmsReceiver", "Processing SMS from $sender: $fullBody")

                    val transaction = extractor.extractTransaction(fullBody, sender, timestamp)
                    
                    if (transaction != null) {
                        // Cross-layer dedup: skip if already processed by ContentObserver or NotificationListener
                        if (TransactionDedup.isDuplicate(fullBody)) {
                            Log.d(
                                "SmsReceiver",
                                "Skipping — already processed by another detection layer"
                            )
                            return@launch
                        }

                        // DB-level dedup: check by system timestamp + amount
                        val db = AppDatabase.getDatabase(context)
                        val existsInDb =
                            db.transactionDao().checkDuplicate(transaction.date, transaction.amount)
                        if (existsInDb > 0) {
                            Log.d(
                                "SmsReceiver",
                                "Skipping — transaction already exists in DB (date+amount match)"
                            )
                            return@launch
                        }

                        // Check if we should only track debits
                        val sharedPrefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                        val trackOnlyDebits = sharedPrefs.getBoolean("track_only_debits", false)
                        if (trackOnlyDebits && transaction.amount >= 0) {
                            Log.d("SmsReceiver", "Ignoring non-debit transaction as per settings")
                            return@launch
                        }

                        // 2. Enhanced Location Fetching with Timeout
                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                        val location = try {
                            withTimeoutOrNull(5000) { // 5 second max wait
                                val lastLoc = fusedLocationClient.lastLocation.await()
                                // If last location is old (5 mins) or null, get fresh balanced location
                                if (lastLoc == null || (System.currentTimeMillis() - lastLoc.time) > 5 * 60 * 1000) {
                                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                                } else {
                                    lastLoc
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Location capture failed: ${e.message}")
                            null
                        }

                        if (location == null) {
                            Log.w("SmsReceiver", "Could not retrieve location for transaction")
                        }

                        val withLocation = transaction.copy(
                            latitude = location?.latitude,
                            longitude = location?.longitude
                        )
                        showTransactionNotification(context, withLocation)
                    }
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Critical error in SMS processing", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showTransactionNotification(context: Context, transaction: Transaction) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "transaction_alerts"

        val channel = NotificationChannel(
            channelId,
            "Transaction Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notificationId = (transaction.date % Int.MAX_VALUE).toInt()

        fun createTransactionIntent(action: String): Intent {
            return Intent(context, NotificationReceiver::class.java).apply {
                this.action = action
                putExtra("notificationId", notificationId)
                putExtra("sender", transaction.sender)
                putExtra("amount", transaction.amount)
                putExtra("date", transaction.date)
                putExtra("body", transaction.body)
                putExtra("category", transaction.category)
                putExtra("latitude", transaction.latitude ?: 0.0)
                putExtra("longitude", transaction.longitude ?: 0.0)
            }
        }

        val acceptPendingIntent = PendingIntent.getBroadcast(context, notificationId, createTransactionIntent("ACCEPT_TRANSACTION"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val denyPendingIntent = PendingIntent.getBroadcast(context, notificationId + 1, Intent(context, NotificationReceiver::class.java).apply { 
            action = "DENY_TRANSACTION"
            putExtra("notificationId", notificationId)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val triggerAt = System.currentTimeMillis() + 30000
        val timeoutPendingIntent = PendingIntent.getBroadcast(context, notificationId + 2, createTransactionIntent("TIMEOUT_TRANSACTION"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                timeoutPendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                timeoutPendingIntent
            )
        }
    }
}
