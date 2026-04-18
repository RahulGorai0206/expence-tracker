package com.myapp.expensetracker

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.*

class LazySyncManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val extractor = TransactionExtractor()

    companion object {
        private const val MODEL_URL = "https://huggingface.co/rperuman/gemma-2b-it-cpu-int4.bin/resolve/main/gemma-2b-it-cpu-int4.bin"
        private const val MODEL_FILE_NAME = "gemma.bin"
    }

    suspend fun syncMessages(startDate: Long, endDate: Long, onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress("Checking AI model...")
            val modelFile = File(context.filesDir, MODEL_FILE_NAME)
            if (!modelFile.exists()) {
                onProgress("Downloading AI model (this may take a while)...")
                downloadModel(modelFile)
            }

            onProgress("Initializing AI...")
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .build()
            
            LlmInference.createFromOptions(context, options).use { llmInference ->
                onProgress("Fetching SMS messages...")
                val messages = fetchSmsMessages(startDate, endDate)
                // Stage 1: Block promotional/spam/OTP messages first
                val promoKeywords = listOf(
                    "recharge", "offer", "click", "subscribe", "download", "unlimited",
                    "plan ", "plans ", "validity", "activate", "www.", "http://", "https://",
                    "get ", "win ", "earn ", "reward", "cashback offer", "discount",
                    "congratulations", "dear customer", "promo", "deal", "free", "renew your",
                    "otp", "verification code", "valid till", "secret code", "do not share"
                )
                // Stage 2: Must have a strong bank transaction signal
                val transactionSignals = listOf(
                    "debited", "credited", "spent", "withdrawn", "deposited",
                    "a/c", "acct", "account", "upi", "neft", "imps", "txn", "transfer"
                )
                // Stage 3: Must contain a currency mention
                val currencySignals = listOf("rs.", "rs ", "inr", "₹")

                val ignoreCcBills =
                    context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
                        .getBoolean("ignore_cc_bills", false)

                val candidateMessages = messages.filter { sms ->
                    val lower = sms.body.lowercase()
                    val isPromoOrOtp = promoKeywords.any { lower.contains(it) }
                    val hasTransaction = transactionSignals.any { lower.contains(it) }
                    val hasCurrency = currencySignals.any { lower.contains(it) }

                    if (isPromoOrOtp || !hasTransaction || !hasCurrency) return@filter false

                    if (ignoreCcBills && extractor.isCreditCardBill(sms.body)) {
                        Log.d("LazySync", "Ignoring CC Bill candidate: ${sms.body}")
                        return@filter false
                    }

                    true
                }
                
                onProgress("Found ${candidateMessages.size} potential transactions. AI Analyzing...")
                
                val trackOnlyDebits = context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
                    .getBoolean("track_only_debits", false)

                var count = 0
                candidateMessages.forEachIndexed { index, sms ->
                    onProgress("AI Analyzing message ${index + 1}/${candidateMessages.size}...")
                    val transaction = extractWithAI(llmInference, sms.body, sms.sender, sms.timestamp)
                    if (transaction != null) {
                        // Respect "Track Only Debits" setting
                        if (trackOnlyDebits && transaction.amount >= 0) return@forEachIndexed

                        // Prevent duplicate syncing — all detection layers now use the
                        // system SMS timestamp, so exact date+amount matching works reliably
                        val duplicateCount = database.transactionDao().checkDuplicate(
                            transaction.date,
                            transaction.amount,
                            transaction.bodyHash
                        )
                        if (duplicateCount > 0) {
                            Log.d(
                                "LazySync",
                                "Duplicate skipped — already exists in DB (date+amount match)"
                            )
                            return@forEachIndexed
                        }
                        
                        val localId = database.transactionDao().insertAndReturnId(transaction)
                        // Trigger cloud upload so it actually reaches the sheet
                        GoogleSheetsLogger.logAsync(context, transaction, localId)
                        count++
                    }
                }

                onProgress("Sync complete! Added $count transactions.")
                if (count > 0) {
                    updateExpenseWidget(context)
                }
                true
            }
        } catch (e: Throwable) {
            Log.e("LazySync", "Error during lazy sync", e)
            onProgress("Error: ${e.localizedMessage}")
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Lazy Sync Error: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            }
            false
        }
    }

    private fun downloadModel(targetFile: File) {
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
            }.getInputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
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

    private fun extractWithAI(llmInference: LlmInference, body: String, sender: String, timestamp: Long): Transaction? {
        val prompt = """
            <start_of_turn>user
            Extract transaction details from this SMS. If it is NOT a bank transaction or if it's an OTP, answer INVALID.
            Otherwise, extract the details precisely in this format:
            AMOUNT: (number only, use dot for decimals)
            TYPE: (DEBIT or CREDIT)
            CATEGORY: (Dining, Transport, Groceries, Shopping, Bills, Entertainment, Health, or Other)

            Examples:
            SMS: "Your A/c XX123 debited by Rs 500.00 for txn at Amazon"
            Response: AMOUNT: 500.00, TYPE: DEBIT, CATEGORY: Shopping

            SMS: "OTP is 123456 for txn of Rs 100.00"
            Response: INVALID

            SMS: "$body"<end_of_turn>
            <start_of_turn>model
        """.trimIndent()
        
        val response = llmInference.generateResponse(prompt).trim()
        Log.d("LazySync", "LLM Extraction for SMS: $response")
        
        if (response.contains("INVALID", ignoreCase = true) && !response.contains("AMOUNT", ignoreCase = true)) {
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

            // Handle commas as thousand separators by removing them, but keep the dot
            val rawAmountStr = amountMatch?.groupValues?.get(1)
            val amountStr = rawAmountStr?.replace(",", "")
            val amount = amountStr?.toDoubleOrNull() ?: return null

            val rawCategory = categoryMatch?.groupValues?.get(1)?.lowercase() ?: "other"
            val categoryStr =
                rawCategory.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            Log.d("LazySync", "Parsed amount: $amount from raw string: $rawAmountStr")
            
            // Keyword-based debit/credit verification — more reliable than AI for Indian SMS
            val lower = body.lowercase()
            val isDebitByKeyword = lower.contains("debited") || lower.contains("spent") ||
                lower.contains("payment of") || lower.contains("paid") || lower.contains("withdrawn") ||
                lower.contains("deducted") || lower.contains("transferred to")
            val isCreditByKeyword = lower.contains("credited") || lower.contains("received") ||
                lower.contains("deposited") || lower.contains("refund") || lower.contains("cashback")
            
            val isDebit = when {
                isDebitByKeyword && !isCreditByKeyword -> true   // Clear debit keyword
                isCreditByKeyword && !isDebitByKeyword -> false  // Clear credit keyword
                else -> typeMatch?.groupValues?.get(1)?.equals("DEBIT", ignoreCase = true) ?: true // Fallback to AI
            }
            
            val finalAmount = if (isDebit) -amount else amount
            
            return Transaction(
                sender = sender,
                amount = finalAmount,
                date = timestamp,
                body = body,
                category = categoryStr,
                status = "Cleared",
                type = "AI",
                syncStatus = "pending"  // will be uploaded to Google Sheets
            )
        } catch (e: Exception) {
            return null
        }
    }

    data class SmsMessage(val body: String, val sender: String, val timestamp: Long)
}
