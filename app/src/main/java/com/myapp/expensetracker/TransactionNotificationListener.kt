package com.myapp.expensetracker

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Intercepts notifications from messaging apps (Google Messages, Samsung Messages, etc.)
 * to detect bank transaction messages delivered via RCS or other non-SMS protocols.
 *
 * This is the ONLY reliable way to catch RCS transactions because Google Messages
 * stores RCS data in its own private database — not in the system Telephony provider.
 *
 * Detection chain (layered fallbacks):
 *   1. TransactionNotificationListener (this) — catches RCS, WhatsApp, any notification
 *   2. SmsReceiver (broadcast) — catches standard SMS instantly
 *   3. ContentObserver in SmsMonitorService — catches SMS/RCS written to system DB
 *   4. LazySyncManager — historical scan of system SMS database
 */
class TransactionNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val extractor = TransactionExtractor()

    companion object {
        private const val TAG = "TxnNotifListener"

        // Messaging apps whose notifications we inspect for transactions
        private val MESSAGING_PACKAGES = setOf(
            "com.google.android.apps.messaging",   // Google Messages (RCS + SMS)
            "com.samsung.android.messaging",        // Samsung Messages
            "com.android.mms",                      // AOSP Messaging
            "com.jio.myjio",                        // Jio messaging
            "org.thoughtcrime.securesms",            // Signal
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName !in MESSAGING_PACKAGES) return

        val extras = sbn.notification?.extras ?: return

        // Try to extract from MessagingStyle first (modern RCS/Messaging apps)
        var messageBody: String? = null
        val messages =
            extras.getParcelableArray(NotificationCompat.EXTRA_MESSAGES, Parcelable::class.java)
        if (messages != null && messages.isNotEmpty()) {
            val lastMessage = messages.last() as? android.os.Bundle
            if (lastMessage != null) {
                messageBody = lastMessage.getCharSequence("text")?.toString()
            }
        }

        // Fallback for older/standard Notification styles
        if (messageBody == null) {
            val bigText = extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()
            val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()
            messageBody = bigText ?: text
        }
        
        if (messageBody == null) return

        // Extract sender from notification title
        val title = extras.getCharSequence("android.title")?.toString() ?: "Unknown"

        Log.d(TAG, "Notification from $packageName | title: $title | body: ${messageBody.take(80)}...")

        // Quick pre-filter: skip if it doesn't look like a bank transaction at all
        val lower = messageBody.lowercase()
        val hasCurrency = lower.contains("rs.") || lower.contains("rs ") || lower.contains("inr") || lower.contains("₹")
        val hasTransactionWord = lower.contains("debited") || lower.contains("credited") ||
            lower.contains("spent") || lower.contains("withdrawn") || lower.contains("transferred") ||
            lower.contains("payment") || lower.contains("a/c") || lower.contains("acct") ||
            lower.contains("upi") || lower.contains("txn") || lower.contains("deposited")
        if (!hasCurrency || !hasTransactionWord) return

        // Cross-layer dedup: skip if already processed by SmsReceiver or ContentObserver
        if (TransactionDedup.isDuplicate(messageBody)) {
            Log.d(TAG, "Skipping — already processed by another detection layer")
            return
        }

        Log.d(TAG, "Potential transaction detected via notification — processing")

        scope.launch {
            processTransactionMessage(messageBody, title, System.currentTimeMillis())
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun processTransactionMessage(body: String, sender: String, timestamp: Long) {
        try {
            val transaction = extractor.extractTransaction(body, sender, timestamp) ?: return

            // Check track-only-debits preference
            val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val trackOnlyDebits = prefs.getBoolean("track_only_debits", false)
            if (trackOnlyDebits && transaction.amount >= 0) {
                Log.d(TAG, "Ignoring non-debit transaction as per settings")
                return
            }

            // DB-level dedup: skip if this transaction was already saved
            // (covers cases where the in-memory dedup window has expired)
            val db = AppDatabase.getDatabase(this)
            val existsInDb = db.transactionDao().checkDuplicateByBody(transaction.amount, body)
            if (existsInDb > 0) {
                Log.d(TAG, "Skipping — transaction already exists in DB")
                return
            }

            // Capture location
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@TransactionNotificationListener)
            val location = try {
                withTimeoutOrNull(5000) {
                    val lastLoc = fusedLocationClient.lastLocation.await()
                    if (lastLoc == null || (System.currentTimeMillis() - lastLoc.time) > 5 * 60 * 1000) {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                    } else {
                        lastLoc
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Location capture failed: ${e.message}")
                null
            }

            val withLocation = transaction.copy(
                latitude = location?.latitude,
                longitude = location?.longitude
            )

            showTransactionNotification(withLocation)
            Log.d(TAG, "Transaction notification shown for ₹${transaction.amount} from $sender")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification message", e)
        }
    }

    private fun showTransactionNotification(transaction: Transaction) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channelId = "transaction_alerts"

        val channel = NotificationChannel(
            channelId,
            "Transaction Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notificationId = (transaction.date % Int.MAX_VALUE).toInt()

        fun createTransactionIntent(action: String): Intent {
            return Intent(this, NotificationReceiver::class.java).apply {
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

        val acceptPendingIntent = PendingIntent.getBroadcast(this, notificationId, createTransactionIntent("ACCEPT_TRANSACTION"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val denyPendingIntent = PendingIntent.getBroadcast(this, notificationId + 1, Intent(this, NotificationReceiver::class.java).apply {
            action = "DENY_TRANSACTION"
            putExtra("notificationId", notificationId)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val triggerAt = System.currentTimeMillis() + 30000
        val timeoutPendingIntent = PendingIntent.getBroadcast(this, notificationId + 2, createTransactionIntent("TIMEOUT_TRANSACTION"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
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

        val alarmManager = getSystemService(AlarmManager::class.java)
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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed
    }
}
