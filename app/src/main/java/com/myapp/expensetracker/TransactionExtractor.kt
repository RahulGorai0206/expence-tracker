package com.myapp.expensetracker

import com.google.mlkit.nl.entityextraction.*
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.tasks.await

class TransactionExtractor {
    private val languageIdentifier = LanguageIdentification.getClient()
    private val entityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH)
            .build()
    )

    private val categoriesMap = mapOf(
        "Dining" to listOf("Starbucks", "Coffee", "Restaurant", "Zomato", "Swiggy", "McDonalds", "KFC", "Burger", "Pizza", "Cafe", "Bake"),
        "Transport" to listOf("Uber", "Ola", "Taxi", "Fuel", "Petrol", "Shell", "Metro", "IRCTC", "Railway", "Bus", "Rapido"),
        "Groceries" to listOf("Market", "Grocery", "Foods", "BigBasket", "Blinkit", "Zepto", "Reliance", "Fresh", "Vegetable", "Milk"),
        "Shopping" to listOf("Shopping", "Mall", "Store", "Amazon", "Flipkart", "Myntra", "Ajio", "Fashion", "Clothing", "Electronics"),
        "Bills" to listOf("Bill", "Utility", "Electricity", "Water", "Gas", "Recharge", "Mobile", "Internet", "Broadband", "Insurance", "Premium"),
        "Entertainment" to listOf("Netflix", "Hotstar", "Spotify", "Movie", "Cinema", "Theater", "Prime", "Gaming", "Ticket"),
        "Health" to listOf("Pharmacy", "Hospital", "Clinic", "Medical", "Apollo", "Doctor", "Gym", "Fitness")
    )

    private val spendKeywords = listOf(
        "debited", "spent", "paid", "transferred", "payment", "sent", "withdrawal",
        "purchased", "txn", "using", "done", "deducted"
    )
    private val receiveKeywords = listOf(
        "credited", "received", "deposited", "added", "refunded", "cashback"
    )

    suspend fun extractTransaction(body: String, sender: String, timestamp: Long): Transaction? {
        // Skip OTPs and simple verification codes
        if (body.contains("OTP", true) || body.contains("verification code", true) || body.contains("is your code", true)) {
            return null
        }

        return try {
            // Identify language to ensure it's English (or supported)
            val languageCode = languageIdentifier.identifyLanguage(body).await()
            if (languageCode == "und" || languageCode != "en") {
                // If it's definitely not English, we might skip it or proceed with caution
                // For now, let's just proceed as many banking SMS are English-heavy anyway
            }

            entityExtractor.downloadModelIfNeeded().await()

            val params = EntityExtractionParams.Builder(body)
                .setEntityTypesFilter(setOf(
                    Entity.TYPE_MONEY,
                    Entity.TYPE_DATE_TIME,
                    Entity.TYPE_URL,
                    Entity.TYPE_IBAN
                ))
                .build()

            val result = entityExtractor.annotate(params).await()

            var extractedAmount: Double? = null
            var transactionDate: Long = timestamp

            // More robust amount extraction: usually the first money entity in a banking SMS is the transaction amount
            for (annotation in result) {
                for (entity in annotation.entities) {
                    when (entity) {
                        is MoneyEntity -> {
                            if (extractedAmount == null) {
                                val integerPart = entity.integerPart
                                val fractionalPart = entity.fractionalPart
                                extractedAmount = integerPart.toDouble() + (fractionalPart.toDouble() / 100.0)
                            }
                        }
                        is DateTimeEntity -> {
                            // Optionally use the date from the SMS if available
                            transactionDate = entity.timestampMillis
                        }
                    }
                }
            }

            val lowerBody = body.lowercase()
            val isSpend = spendKeywords.any { lowerBody.contains(it) }
            val isReceive = receiveKeywords.any { lowerBody.contains(it) }
            
            // Avoid false positives for balance inquiries
            val isBalanceUpdate = lowerBody.contains("balance") && !isSpend && !isReceive
            if (isBalanceUpdate) return null

            // Regex fallback for various currency formats (Rs, INR, ₹)
            if (extractedAmount == null) {
                val patterns = listOf(
                    """(?:Rs\.?|INR|₹)\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
                    """amount\s+(?:of\s+)?(?:Rs\.?|INR|₹)?\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
                    """debited\s+by\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
                    """credited\s+with\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE)
                )
                for (pattern in patterns) {
                    val match = pattern.find(body)
                    if (match != null) {
                        extractedAmount = match.groupValues[1].replace(",", "").toDoubleOrNull()
                        if (extractedAmount != null) break
                    }
                }
            }

            if (extractedAmount != null && (isSpend || isReceive)) {
                val finalAmount = if (isSpend) -extractedAmount else extractedAmount

                var category = "Other"
                for ((cat, keywords) in categoriesMap) {
                    if (keywords.any { lowerBody.contains(it.lowercase()) }) {
                        category = cat
                        break
                    }
                }

                // If sender is a known UPI ID or bank, it might help (stub for more logic)
                if (category == "Other") {
                    if (sender.contains("VPA", true) || body.contains("@")) {
                        category = "UPI Transfer"
                    }
                }

                Transaction(
                    sender = sender,
                    amount = finalAmount,
                    date = transactionDate,
                    body = body,
                    category = category,
                    status = "Cleared"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
