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

    suspend fun syncMessages(startDate: Long, endDate: Long, onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
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
                onProgress("Found ${messages.size} messages. Analyzing...")

                var count = 0
                messages.forEachIndexed { index, sms ->
                    onProgress("Analyzing message ${index + 1}/${messages.size}...")
                    if (isTransactionMessage(llmInference, sms.body)) {
                        val transaction = extractor.extractTransaction(sms.body, sms.sender, sms.timestamp)
                        if (transaction != null) {
                            database.transactionDao().insert(transaction)
                            count++
                        }
                    }
                }

                onProgress("Sync complete! Added $count transactions.")
            }
        } catch (e: Exception) {
            Log.e("LazySync", "Error during lazy sync", e)
            onProgress("Error: ${e.localizedMessage}")
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
            Telephony.Sms.CONTENT_URI,
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

    private fun isTransactionMessage(llmInference: LlmInference, body: String): Boolean {
        val prompt = """
            <start_of_turn>user
            Determine if the following SMS message describes a financial transaction (money spent, received, or transferred).
            Rules:
            1. Look for keywords like 'debited', 'credited', 'paid', 'spent', 'received', 'withdrawal', 'INR', 'Rs'.
            2. Ignore OTPs, marketing alerts, or general bank notifications.
            3. Answer ONLY with 'YES' or 'NO'.
            
            Message: "$body"<end_of_turn>
            <start_of_turn>model
            
        """.trimIndent()
        
        val response = llmInference.generateResponse(prompt).trim().uppercase()
        Log.d("LazySync", "LLM Response for message: $response")
        return response.contains("YES")
    }

    data class SmsMessage(val body: String, val sender: String, val timestamp: Long)
}
