package com.myapp.expensetracker

import com.google.mlkit.nl.entityextraction.*
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class TransactionExtractor {
    companion object {
        private val languageIdentifier by lazy { LanguageIdentification.getClient() }
        private val entityExtractor by lazy {
            EntityExtraction.getClient(
                EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH)
                    .build()
            )
        }
        @Volatile
        private var modelDownloaded = false
    }

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

    fun isCreditCardBill(body: String): Boolean {
        val lowerBody = body.lowercase()

        // 1. Direct Bill Indicators (Specific enough on their own)
        val billPhrases = listOf(
            "total amount due",
            "minimum amount due",
            "statement for your card",
            "bill for your card",
            "outstanding on your card",
            "statement is generated",
            "bill is generated",
            "card bill"
        )

        if (billPhrases.any { lowerBody.contains(it) }) return true

        // 2. Secondary check for "Card" + "Bill/Due/Statement" combinations
        val hasCardRef =
            lowerBody.contains("card ending") || lowerBody.contains("credit card") || lowerBody.contains(
                "your card"
            ) || lowerBody.contains(" card ")
        val isBillContext =
            lowerBody.contains("due date") || lowerBody.contains("statement") || lowerBody.contains(
                "outstanding"
            ) || lowerBody.contains("bill")

        // Explicit check for Card + Bill combination
        if (lowerBody.contains("card") && lowerBody.contains("bill")) return true

        return hasCardRef && isBillContext
    }

    suspend fun extractTransaction(body: String, sender: String, timestamp: Long): Transaction? {
        val lowerBody = body.lowercase()
        
        // 1. Skip OTPs and non-transactional alerts
        if (lowerBody.contains("otp") || lowerBody.contains("verification code") || lowerBody.contains("is your code")) {
            return null
        }

        return try {
            var extractedAmount: Double? = null
            
            try {
                if (!modelDownloaded) {
                    entityExtractor.downloadModelIfNeeded().await()
                    modelDownloaded = true
                }
    
                val params = EntityExtractionParams.Builder(body)
                    .setEntityTypesFilter(setOf(Entity.TYPE_MONEY)) // Only extract money
                    .build()
    
                val result = entityExtractor.annotate(params).await()
    
                // 2. Extract Money using ML Kit
                for (annotation in result) {
                    for (entity in annotation.entities) {
                        if (entity is MoneyEntity) {
                            if (extractedAmount == null) {
                                // Ignore amounts following "bal" or "balance" (likely account balance)
                                val startIndex = annotation.start
                                val prefix = lowerBody.substring((startIndex - 15).coerceAtLeast(0), startIndex)
                                if (!prefix.contains("bal")) {
                                    extractedAmount = entity.integerPart.toDouble() + (entity.fractionalPart.toDouble() / 100.0)
                                }
                            }
                        }
                    }
                }
            } catch (mlErr: Exception) {
                mlErr.printStackTrace()
                // Proceed to regex fallback if ML Kit fails
            }

            // ALWAYS use the SMS received timestamp as requested
            val transactionDate: Long = timestamp

            // 3. Regex Fallback for Amount (Rs. / INR / ₹)
            if (extractedAmount == null) {
                val patterns = listOf(
                    """(?:Rs\.?|INR|₹)\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
                    """debited\s+by\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE)
                )
                for (pattern in patterns) {
                    val match = pattern.find(body)
                    if (match != null) {
                        val amt = match.groupValues[1].replace(",", "").toDoubleOrNull()
                        // Ensure it's not the balance
                        val matchStart = match.range.first
                        val prefix = lowerBody.substring((matchStart - 10).coerceAtLeast(0), matchStart)
                        if (!prefix.contains("bal") && amt != null) {
                            extractedAmount = amt
                            break
                        }
                    }
                }
            }

            val isSpend = spendKeywords.any { lowerBody.contains(it) }
            val isReceive = receiveKeywords.any { lowerBody.contains(it) }

            if (extractedAmount != null && (isSpend || isReceive)) {
                val finalAmount = if (isSpend) -extractedAmount else extractedAmount
                
                var category = "Other"
                for ((cat, keywords) in categoriesMap) {
                    if (keywords.any { lowerBody.contains(it.lowercase()) }) {
                        category = cat
                        break
                    }
                }

                return Transaction(
                    sender = sender,
                    amount = finalAmount,
                    date = transactionDate,
                    body = body,
                    category = category,
                    status = "Cleared",
                    type = "automated"
                )
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
