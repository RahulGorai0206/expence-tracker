package com.myapp.expensetracker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class SmsMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val extractor = TransactionExtractor()
    private var smsObserver: SmsContentObserver? = null
    private var mmsSmsObserver: SmsContentObserver? = null
    private val processLock = Mutex() // Prevents concurrent processNewMessages() calls

    companion object {
        private const val TAG = "SmsMonitorService"
        private const val CHANNEL_ID = "sms_monitor_channel"
        private const val NOTIFICATION_ID = 9999
        private const val PREF_LAST_SMS_ID = "last_processed_sms_id"

        fun start(context: Context) {
            val intent = Intent(context, SmsMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SmsMonitorService::class.java))
        }

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .getBoolean("background_monitoring", true)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("background_monitoring", enabled).apply()
            if (enabled) start(context) else stop(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        startForegroundWithType()
        registerSmsObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY // Restart if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        unregisterSmsObserver()

        // Auto-restart if still enabled (handles edge case where system kills us)
        if (isEnabled(this)) {
            val restartIntent = Intent(this, SmsMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            }
        }
    }

    // ── ContentObserver for RCS + SMS ──────────────────────────────────

    private fun registerSmsObserver() {
        val handler = Handler(Looper.getMainLooper())
        smsObserver = SmsContentObserver(handler)
        mmsSmsObserver = SmsContentObserver(handler)

        // Watch content://sms — triggers on standard SMS database writes
        contentResolver.registerContentObserver(
            Uri.parse("content://sms/"),
            true,
            smsObserver!!
        )
        // Watch content://mms-sms — some devices/Google Messages write RCS here
        contentResolver.registerContentObserver(
            Uri.parse("content://mms-sms/"),
            true,
            mmsSmsObserver!!
        )
        Log.d(TAG, "SMS + MMS-SMS ContentObservers registered")

        // Initialize last-seen ID so we don't reprocess old messages on first run
        initLastProcessedId()
    }

    private fun unregisterSmsObserver() {
        smsObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        mmsSmsObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        smsObserver = null
        mmsSmsObserver = null
        Log.d(TAG, "ContentObservers unregistered")
    }

    private fun initLastProcessedId() {
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        if (!prefs.contains(PREF_LAST_SMS_ID)) {
            // Seed with the current highest SMS ID so we only process new messages going forward
            val highestId = getHighestSmsId()
            prefs.edit().putLong(PREF_LAST_SMS_ID, highestId).apply()
            Log.d(TAG, "Seeded last processed SMS ID: $highestId")
        }
    }

    private fun getHighestSmsId(): Long {
        var maxId = 0L
        try {
            val cursor = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                null, null,
                "${Telephony.Sms._ID} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    maxId = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting highest SMS ID", e)
        }
        return maxId
    }

    /**
     * ContentObserver that fires whenever the SMS/MMS database changes.
     * RCS messages from Google Messages are written into this same database,
     * so this observer catches them even though no SMS_RECEIVED broadcast fires.
     */
    inner class SmsContentObserver(handler: Handler) : ContentObserver(handler) {

        // Debounce: Android may fire onChange multiple times for a single message
        @Volatile
        private var lastChangeTime = 0L
        private val debounceMs = 3000L // 3s debounce to avoid rapid re-fires

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)

            val now = System.currentTimeMillis()
            if (now - lastChangeTime < debounceMs) return
            lastChangeTime = now

            Log.d(TAG, "SMS database changed — URI: $uri")
            scope.launch { processNewMessages() }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun processNewMessages() = processLock.withLock {
        // Mutex ensures only one processNewMessages() runs at a time,
        // preventing race conditions when onChange fires rapidly.
        try {
            val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val lastId = prefs.getLong(PREF_LAST_SMS_ID, 0L)

            // Query inbox for messages newer than our last processed ID
            val cursor = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.BODY,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.DATE
                ),
                "${Telephony.Sms._ID} > ?",
                arrayOf(lastId.toString()),
                "${Telephony.Sms._ID} ASC"
            )

            var newHighestId = lastId

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val addrIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val smsId = it.getLong(idIdx)
                    val body = it.getString(bodyIdx) ?: continue
                    val sender = it.getString(addrIdx) ?: "Unknown"
                    val timestamp = it.getLong(dateIdx)

                    // Always advance watermark for every row, even non-transactions
                    if (smsId > newHighestId) newHighestId = smsId

                    Log.d(TAG, "ContentObserver processing new message ID=$smsId from $sender")

                    val transaction = extractor.extractTransaction(body, sender, timestamp)

                    if (transaction != null) {
                        // Cross-layer dedup: skip if SmsReceiver or NotificationListener already got it
                        if (TransactionDedup.isDuplicate(body)) {
                            Log.d(TAG, "Skipping ID=$smsId — already processed by another layer")
                            continue
                        }

                        // Check track-only-debits preference
                        val trackOnlyDebits = prefs.getBoolean("track_only_debits", false)
                        if (trackOnlyDebits && transaction.amount >= 0) {
                            Log.d(TAG, "Ignoring non-debit transaction (ContentObserver)")
                        } else {
                            // Capture location
                            val fusedLocationClient =
                                LocationServices.getFusedLocationProviderClient(this@SmsMonitorService)
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
                                Log.e(TAG, "Location capture failed (ContentObserver): ${e.message}")
                                null
                            }

                            val withLocation = transaction.copy(
                                latitude = location?.latitude,
                                longitude = location?.longitude
                            )

                            // Use the same notification flow as SmsReceiver
                            showTransactionNotification(withLocation)
                        }
                    }
                }
            }

            // Persist the new watermark — always, even if no transactions were found.
            // This prevents re-processing non-transaction messages on subsequent onChange fires.
            if (newHighestId > lastId) {
                prefs.edit().putLong(PREF_LAST_SMS_ID, newHighestId).apply()
                Log.d(TAG, "Updated last processed SMS ID to $newHighestId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing new messages via ContentObserver", e)
        }
    }

    // ── Notification (replicates SmsReceiver logic) ───────────────────

    private fun showTransactionNotification(transaction: Transaction) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "transaction_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Transaction Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

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

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, timeoutPendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, timeoutPendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, timeoutPendingIntent)
        }
    }

    // ── Foreground notification ───────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transaction Monitoring",
                NotificationManager.IMPORTANCE_LOW // Low = no sound, shows in shade
            ).apply {
                description = "Keeps the app running to capture transaction SMS"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithType() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Expense Tracker Active")
            .setContentText("Monitoring SMS & RCS transactions")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
