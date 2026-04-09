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
                // Stage 1: Block promotional/spam messages first
                val promoKeywords = listOf(
                    "recharge", "offer", "click", "subscribe", "download", "unlimited",
                    "plan ", "plans ", "validity", "activate", "www.", "http://", "https://",
                    "get ", "win ", "earn ", "reward", "cashback offer", "discount",
                    "congratulations", "dear customer", "promo", "deal", "free", "renew your"
                )
                // Stage 2: Must have a strong bank transaction signal
                val transactionSignals = listOf(
                    "debited", "credited", "spent", "withdrawn", "deposited",
                    "a/c", "acct", "account", "upi", "neft", "imps", "txn", "transfer"
                )
                // Stage 3: Must contain a currency mention
                val currencySignals = listOf("rs.", "rs ", "inr", "₹")

                val candidateMessages = messages.filter { sms ->
                    val lower = sms.body.lowercase()
                    val isPromo = promoKeywords.any { lower.contains(it) }
                    val hasTransaction = transactionSignals.any { lower.contains(it) }
                    val hasCurrency = currencySignals.any { lower.contains(it) }
                    !isPromo && hasTransaction && hasCurrency
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
                        val localId = database.transactionDao().insertAndReturnId(transaction)
                        // Trigger cloud upload so it actually reaches the sheet
                        GoogleSheetsLogger.logAsync(context, transaction, localId)
                        count++
                    }
                }

                onProgress("Sync complete! Added $count transactions.")
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
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,  // Inbox only — excludes sent messages
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
            arrayOf(startDate.toString(), endDate.toString()),
            "${Telephony.Sms.DATE} ASC"
        )

        cursor?.use {
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                messages.add(
                    SmsMessage(
                        body = it.getString(bodyIndex),
                        sender = it.getString(addressIndex),
                        timestamp = it.getLong(dateIndex)
                    )
                )
            }
        }
        return messages
    }

    private fun extractWithAI(llmInference: LlmInference, body: String, sender: String, timestamp: Long): Transaction? {
        val prompt = """
            <start_of_turn>user
            Extract transaction details from this SMS. If it is NOT a bank transaction or if it's an OTP, answer INVALID.
            Otherwise, extract the details precisely in this format:
            AMOUNT: (number only)
            TYPE: (DEBIT or CREDIT)
            CATEGORY: (Dining, Transport, Groceries, Shopping, Bills, Entertainment, Health, or Other)

            SMS: "$body"<end_of_turn>
            <start_of_turn>model
            
        """.trimIndent()
        
        val response = llmInference.generateResponse(prompt).trim()
        Log.d("LazySync", "LLM Extraction for SMS: $response")
        
        if (response.contains("INVALID", ignoreCase = true) && !response.contains("AMOUNT", ignoreCase = true)) {
            return null
        }
        
        try {
            val amountMatch = Regex("AMOUNT:.*?([\\d,]+(?:\\.\\d{1,2})?)").find(response)
            val typeMatch = Regex("TYPE:.*?(DEBIT|CREDIT)", RegexOption.IGNORE_CASE).find(response)
            val categoryMatch = Regex("CATEGORY:.*?([a-zA-Z]+)").find(response)
            
            val amountStr = amountMatch?.groupValues?.get(1)?.replace(",", "")
            val categoryStr = categoryMatch?.groupValues?.get(1) ?: "Other"
            val amount = amountStr?.toDoubleOrNull() ?: return null
            
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
                type = "automated",
                syncStatus = "pending"  // will be uploaded to Google Sheets
            )
        } catch (e: Exception) {
            return null
        }
    }

    data class SmsMessage(val body: String, val sender: String, val timestamp: Long)
}
