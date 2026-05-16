package com.myapp.expensetracker

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.*
import kotlin.math.abs

class LazySyncManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val extractor = TransactionExtractor()

    data class SyncProgress(
        val message: String,
        val current: Int = 0,
        val total: Int = 0,
        val added: Int = 0
    )

    companion object {
        private const val MODEL_URL = "https://huggingface.co/rperuman/gemma-2b-it-cpu-int4.bin/resolve/main/gemma-2b-it-cpu-int4.bin"
        private const val MODEL_FILE_NAME = "gemma.bin"
    }

    suspend fun syncMessagesInBackground(
        startDate: Long,
        endDate: Long,
        onProgress: suspend (SyncProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress(SyncProgress("Checking AI model..."))
            val modelFile = File(context.filesDir, MODEL_FILE_NAME)
            if (!modelFile.exists()) {
                downloadModel(modelFile) { status ->
                    runBlocking { onProgress(SyncProgress(status)) }
                }
            }

            onProgress(SyncProgress("Initializing AI..."))
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .build()

            LlmInference.createFromOptions(context, options).use { llmInference ->
                onProgress(SyncProgress("Fetching SMS messages..."))
                val messages = fetchSmsMessages(startDate, endDate)
                val prefs =
                    context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
                val ignoreCcBills = prefs.getBoolean("ignore_cc_bills", false)
                val trackOnlyDebits = prefs.getBoolean("track_only_debits", false)

                var count = 0
                messages.forEachIndexed { index, sms ->
                    val current = index + 1
                    onProgress(
                        SyncProgress(
                            message = "AI checking message $current/${messages.size}...",
                            current = current,
                            total = messages.size,
                            added = count
                        )
                    )

                    val transaction =
                        extractWithAI(llmInference, sms.body, sms.sender, sms.timestamp)
                            ?: return@forEachIndexed

                    if (ignoreCcBills && extractor.isCreditCardBill(sms.body)) {
                        Log.d("LazySync", "Ignoring CC bill after AI check: ${sms.body}")
                        return@forEachIndexed
                    }
                    if (trackOnlyDebits && transaction.amount >= 0) {
                        Log.d("LazySync", "Skipping credit due to Track Only Debits: ${sms.body}")
                        return@forEachIndexed
                    }

                    val duplicateCount = database.transactionDao().checkDuplicate(
                        transaction.date,
                        transaction.amount,
                        transaction.bodyHash
                    )
                    if (duplicateCount > 0) {
                        Log.d("LazySync", "Duplicate skipped after AI scan")
                        return@forEachIndexed
                    }

                    val localId = database.transactionDao().insertAndReturnId(transaction)
                    GoogleSheetsLogger.logAsync(context, transaction, localId)
                    count++
                }

                onProgress(
                    SyncProgress(
                        message = "Sync complete! Added $count transactions.",
                        current = messages.size,
                        total = messages.size,
                        added = count
                    )
                )
                if (count > 0) {
                    enqueueWidgetUpdate(context)
                }
                true
            }
        } catch (e: Throwable) {
            Log.e("LazySync", "Error during background lazy sync", e)
            onProgress(SyncProgress("Error: ${e.localizedMessage}"))
            false
        }
    }


    fun isModelDownloaded(): Boolean {
        return File(context.filesDir, MODEL_FILE_NAME).exists()
    }

    fun deleteModel(): Boolean {
        val file = File(context.filesDir, MODEL_FILE_NAME)
        return if (file.exists()) file.delete() else false
    }

    suspend fun downloadModelOnly(onProgress: (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val modelFile = File(context.filesDir, MODEL_FILE_NAME)
                if (modelFile.exists()) {
                    onProgress("Model already downloaded")
                    return@withContext true
                }
                downloadModel(modelFile, onProgress)
                onProgress("Download complete!")
                true
            } catch (e: Exception) {
                onProgress("Error: ${e.localizedMessage}")
                false
            }
        }

    suspend fun repairModel(onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress("Deleting old model...")
            deleteModel()
            downloadModelOnly(onProgress)
        } catch (e: Exception) {
            onProgress("Error: ${e.localizedMessage}")
            false
        }
    }

    private fun downloadModel(targetFile: File, onProgress: (String) -> Unit) {
        val statFs = android.os.StatFs(context.filesDir.absolutePath)
        val availableSpace = statFs.availableBlocksLong * statFs.blockSizeLong
        if (availableSpace < 2L * 1024 * 1024 * 1024) {
            throw Exception("Insufficient storage space. At least 2GB of free space is required to download the AI model.")
        }

        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
        try {
            URL(MODEL_URL).openConnection().apply {
                connectTimeout = 15000
                readTimeout = 0 // 0 prevents timeout on large model downloads
            }.let { connection ->
                val totalBytes = connection.contentLengthLong
                connection.getInputStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastProgressUpdate = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastProgressUpdate > 500) { // Update every 500ms
                                val percent =
                                    if (totalBytes > 0) (totalRead * 100 / totalBytes) else -1
                                val progressMsg =
                                    if (percent >= 0) "Downloading: $percent%" else "Downloading..."
                                onProgress(progressMsg)
                                lastProgressUpdate = currentTime
                            }
                        }
                    }
                }
            }
            if (!tempFile.renameTo(targetFile)) {
                throw Exception("Failed to save model file")
            }
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun fetchSmsMessages(startDate: Long, endDate: Long): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val seen = mutableSetOf<String>() // Dedup key: "$timestamp|$body"

        // Query 1: Telephony.Sms.CONTENT_URI (standard SMS + some RCS)
        // Use broader URI with type=1 filter to include RCS messages synced by Google Messages.
        try {
            val smsCursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
                "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), startDate.toString(), endDate.toString()),
                "${Telephony.Sms.DATE} ASC"
            )
            smsCursor?.use {
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val addrIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                while (it.moveToNext()) {
                    val body = it.getString(bodyIdx) ?: continue
                    val sender = it.getString(addrIdx) ?: "Unknown"
                    val ts = it.getLong(dateIdx)
                    val key = "$ts|$body"
                    if (seen.add(key)) {
                        messages.add(SmsMessage(body = body, sender = sender, timestamp = ts))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LazySync", "Error querying SMS content URI", e)
        }

        // Query 2: content://mms — Hierarchical MMS table (RCS fallbacks are synced here)
        try {
            val mmsUri = android.provider.Telephony.Mms.CONTENT_URI
            val mmsCursor = context.contentResolver.query(
                mmsUri,
                arrayOf(android.provider.Telephony.Mms._ID, android.provider.Telephony.Mms.DATE),
                "${android.provider.Telephony.Mms.MESSAGE_BOX} = ?",
                arrayOf(android.provider.Telephony.Mms.MESSAGE_BOX_INBOX.toString()),
                "${android.provider.Telephony.Mms.DATE} ASC"
            )
            mmsCursor?.use {
                val idIdx = it.getColumnIndex(android.provider.Telephony.Mms._ID)
                val dateIdx = it.getColumnIndex(android.provider.Telephony.Mms.DATE)

                while (it.moveToNext()) {
                    val mmsId = it.getString(idIdx) ?: continue
                    var ts = it.getLong(dateIdx)
                    
                    // Normalize MMS date to milliseconds (some devices store as seconds)
                    if (ts < 10000000000L) {
                        ts *= 1000
                    }
                    if (ts < startDate || ts > endDate) continue

                    // 1. Fetch plain text payload from part table
                    var body = ""
                    val partUri = android.net.Uri.parse("content://mms/part")
                    val partCursor = context.contentResolver.query(
                        partUri, null, "mid = ?", arrayOf(mmsId), null
                    )
                    partCursor?.use { pCursor ->
                        val ctIdx = pCursor.getColumnIndex("ct")
                        val textIdx = pCursor.getColumnIndex("text")
                        if (ctIdx >= 0 && textIdx >= 0) {
                            while (pCursor.moveToNext()) {
                                val ct = pCursor.getString(ctIdx)
                                if ("text/plain" == ct) {
                                    val text = pCursor.getString(textIdx)
                                    if (text != null) {
                                        body += text
                                    }
                                }
                            }
                        }
                    }
                    
                    if (body.isBlank()) continue

                    // 2. Fetch sender address from addr table (Type 137 is FROM)
                    var sender = "Unknown"
                    val addrUri = android.net.Uri.parse("content://mms/$mmsId/addr")
                    val addrCursor = context.contentResolver.query(
                        addrUri, arrayOf("address", "type"), "type = ?", arrayOf("137"), null
                    )
                    addrCursor?.use { aCursor ->
                        val addrIdx = aCursor.getColumnIndex("address")
                        if (addrIdx >= 0 && aCursor.moveToFirst()) {
                            sender = aCursor.getString(addrIdx) ?: "Unknown"
                        }
                    }

                    val key = "$ts|$body"
                    if (seen.add(key)) {
                        messages.add(SmsMessage(body = body, sender = sender, timestamp = ts))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("LazySync", "content://mms/ query failed (expected on some devices): ${e.message}")
        }

        // Sort merged results by timestamp
        messages.sortBy { it.timestamp }
        return messages
    }

    private suspend fun extractWithAI(
        llmInference: LlmInference,
        body: String,
        sender: String,
        timestamp: Long
    ): Transaction? {
        // 1. Try reliable extraction first (ML Kit / Regex)
        val reliableTxn = extractor.extractTransaction(body, sender, timestamp)

        // 2. Prepare prompt with reliable info if available to ground the AI
        val groundingPrompt = if (reliableTxn != null) {
            "GIVEN: This is a ${if (reliableTxn.amount < 0) "DEBIT" else "CREDIT"} of ${
                abs(
                    reliableTxn.amount
                )
            }."
        } else ""

        val prompt = """
            <start_of_turn>user
            You are a bank SMS classifier. Your job is to decide if an SMS is a REAL bank transaction or not.

            Answer INVALID for ALL of the following:
            - OTP or verification code messages (even if they mention an amount)
            - Login or sign-in alerts
            - Fee/charges updates or policy notifications
            - Credit card bill/statement/payment due reminders
            - Promotional or marketing messages
            - Any SMS that does NOT confirm money was actually debited or credited

            A REAL transaction SMS must confirm that money has ALREADY moved (debited/credited/spent/received/transferred/paid/withdrawn).

            If it IS a real transaction, extract ONLY the amount explicitly stated in the SMS — never invent or guess an amount. Respond in this exact format:
            AMOUNT: (number only, use dot for decimals)
            TYPE: (DEBIT or CREDIT)
            CATEGORY: (Dining, Transport, Groceries, Shopping, Bills, Entertainment, Health, or Other)

            $groundingPrompt

            Examples:
            SMS: "Your A/c XX123 debited by Rs 1200.50 for txn at Swiggy"
            Response: AMOUNT: 1200.50, TYPE: DEBIT, CATEGORY: Dining

            SMS: "Rs 350.00 credited to your A/c XX456. UPI Ref 12345"
            Response: AMOUNT: 350.00, TYPE: CREDIT, CATEGORY: Other

            SMS: "OTP is 123456 for txn of Rs 100.00"
            Response: INVALID

            SMS: "LOGIN to your Flipkart account using OTP 352547. DO NOT SHARE this code."
            Response: INVALID

            SMS: "Important Update on Fees and Charges for your Credit Card. View details: http://example.com"
            Response: INVALID

            SMS: "823132 is the OTP for transaction of INR 160.45 on Card 8303. Do not share OTP."
            Response: INVALID

            SMS: "$body"<end_of_turn>
            <start_of_turn>model
        """.trimIndent()
        
        val response = llmInference.generateResponse(prompt).trim()
        Log.d("LazySync", "LLM Response: $response")

        // Check for INVALID — look at the first line of response for robustness
        val firstLine = response.lines().firstOrNull()?.trim() ?: ""
        if (firstLine.contains("INVALID", ignoreCase = true) ||
            (response.contains("INVALID", ignoreCase = true) && !response.contains(
                "AMOUNT",
                ignoreCase = true
            ))
        ) {
            return null
        }
        
        try {
            // Updated regex to be more robust with whitespace and decimals
            val amountMatch =
                Regex("""AMOUNT:\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE).find(response)
            val typeMatch =
                Regex("""TYPE:\s*(DEBIT|CREDIT)""", RegexOption.IGNORE_CASE).find(response)
            val categoryMatch =
                Regex("""CATEGORY:\s*([a-zA-Z]+)""", RegexOption.IGNORE_CASE).find(response)

            // Parse AI's amount fallback
            val rawAmountStr = amountMatch?.groupValues?.get(1)
            val amountStr = rawAmountStr?.replace(",", "")
            val aiAmount = amountStr?.toDoubleOrNull() ?: 0.0

            val rawCategory = categoryMatch?.groupValues?.get(1)?.lowercase() ?: "other"
            val categoryStr =
                rawCategory.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            // Priority Logic: Use reliable extraction for Amount and Type, AI for Category
            return if (reliableTxn != null) {
                Log.d("LazySync", "Using reliable extraction grounded by AI category: $categoryStr")
                reliableTxn.copy(
                    category = categoryStr,
                    type = "AI",
                    syncStatus = "pending"
                )
            } else if (aiAmount > 0) {
                // Fallback to full AI if reliable extraction failed but AI found something
                // Safety: reject if the AI hallucinated an amount not present in the SMS
                if (amountStr != null && !body.contains(amountStr)) {
                    Log.d(
                        "LazySync",
                        "AI hallucinated amount $aiAmount not found in SMS body, skipping"
                    )
                    return null
                }
                Log.d(
                    "LazySync",
                    "Reliable extraction failed, falling back to AI parsed amount: $aiAmount"
                )

                val lower = body.lowercase()
                val isDebitByKeyword = lower.contains("debited") || lower.contains("spent") ||
                        lower.contains("payment of") || lower.contains("paid") || lower.contains("withdrawn") ||
                        lower.contains("deducted") || lower.contains("transferred to")
                val isCreditByKeyword = lower.contains("credited") || lower.contains("received") ||
                        lower.contains("deposited") || lower.contains("refund") || lower.contains("cashback")

                val isDebit = when {
                    isDebitByKeyword && !isCreditByKeyword -> true
                    isCreditByKeyword && !isDebitByKeyword -> false
                    else -> typeMatch?.groupValues?.get(1)?.equals("DEBIT", ignoreCase = true)
                        ?: true
                }

                Transaction(
                    sender = sender,
                    amount = if (isDebit) -aiAmount else aiAmount,
                    date = timestamp,
                    body = body,
                    category = categoryStr,
                    status = "Cleared",
                    type = "AI",
                    syncStatus = "pending"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("LazySync", "Error parsing AI response", e)
            return null
        }
    }

    data class SmsMessage(val body: String, val sender: String, val timestamp: Long)
}
