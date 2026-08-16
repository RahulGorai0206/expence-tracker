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

        /**
         * Characters scanned before an amount when looking for "bal"/"balance".
         * Must be wide enough for "a/c balance is " — a 10-char window left the
         * "bal" just out of reach and logged account balances as spending.
         */
        private const val BALANCE_LOOKBACK = 15
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

    // Non-transactional phrases that indicate informational/promotional messages
    private val nonTransactionalPhrases = listOf(
        "welcome to",
        "txn. limit", "txn limit", "transaction limit",
        "limit can be enhanced", "limit will be upgraded", "limit has been",
        "activate your", "register for", "apply now",
        "reward points", "loyalty points",
        "emi available", "pre-approved", "pre approved",
        "upgrade your", "exclusive offer",
        "kyc", "pan card", "aadhaar",
        "download the app", "install the app"
    )

    // Words that, when appearing right next to "txn", indicate a non-transactional context
    private val txnDisqualifiers = listOf(
        "limit", "alert", "password", "pin"
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
        
        // 1. Skip OTPs, verification codes and promotional bank messages
        if (isNonTransactional(body)) return null

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
                                    val rawAmount =
                                        entity.integerPart.toDouble() + (entity.fractionalPart.toDouble() / 100.0)
                                    extractedAmount = Math.round(rawAmount * 100.0) / 100.0
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
                extractedAmount = extractAmountByRegex(body)
            }

            val isSpend = isSpendMessage(lowerBody)
            val isReceive = isReceiveMessage(lowerBody)

            if (extractedAmount != null && (isSpend || isReceive)) {
                val finalAmount = if (isSpend) -extractedAmount else extractedAmount

                return Transaction(
                    sender = sender,
                    amount = finalAmount,
                    date = transactionDate,
                    body = body,
                    category = categorize(lowerBody),
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

    // ── Pure helpers ─────────────────────────────────────────────────────────
    // Split out of extractTransaction so the parsing rules can be unit tested
    // without ML Kit or an Android runtime. extractTransaction orchestrates
    // these; the ML Kit call remains the only untestable part.

    /** OTPs and promotional/informational bank messages that carry no transaction. */
    internal fun isNonTransactional(body: String): Boolean {
        val lowerBody = body.lowercase()
        if (lowerBody.contains("otp") ||
            lowerBody.contains("verification code") ||
            lowerBody.contains("is your code")
        ) return true

        return nonTransactionalPhrases.any { lowerBody.contains(it) }
    }

    /**
     * Fallback when ML Kit finds no money entity. Skips amounts preceded by
     * "bal"/"balance" so an account balance isn't logged as a spend.
     */
    internal fun extractAmountByRegex(body: String): Double? {
        val lowerBody = body.lowercase()
        val patterns = listOf(
            """(?:Rs\.?|INR|₹)\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
            """debited\s+by\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            // Every match, not just the first: when a message leads with the
            // available balance, the spend amount comes later in the text.
            for (match in pattern.findAll(body)) {
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
                val matchStart = match.range.first
                val prefix = lowerBody.substring(
                    (matchStart - BALANCE_LOOKBACK).coerceAtLeast(0),
                    matchStart
                )
                if (!prefix.contains("bal")) return amount
            }
        }
        return null
    }

    internal fun isSpendMessage(lowerBody: String): Boolean =
        spendKeywords.any { keywordMatchesTransaction(lowerBody, it) }

    internal fun isReceiveMessage(lowerBody: String): Boolean =
        receiveKeywords.any { lowerBody.contains(it) }

    /** First matching category wins; "Other" when nothing matches. */
    internal fun categorize(lowerBody: String): String {
        for ((category, keywords) in categoriesMap) {
            if (keywords.any { lowerBody.contains(it.lowercase()) }) return category
        }
        return "Other"
    }

    /**
     * Checks if a spend keyword appears in a genuine transactional context.
     * For ambiguous keywords like "txn", verifies that the surrounding words
     * don't indicate a non-transactional context (e.g., "txn limit").
     */
    internal fun keywordMatchesTransaction(body: String, keyword: String): Boolean {
        if (!body.contains(keyword)) return false

        // For "txn", check that it's not followed by disqualifying words
        if (keyword == "txn") {
            // Find all occurrences and check context around each
            var searchFrom = 0
            while (true) {
                val idx = body.indexOf(keyword, searchFrom)
                if (idx == -1) return false
                // Grab up to 15 chars after the keyword to check context
                val afterEnd = (idx + keyword.length + 15).coerceAtMost(body.length)
                val after = body.substring(idx + keyword.length, afterEnd)
                    .trimStart('.', ' ', ',')
                    .lowercase()
                val isDisqualified = txnDisqualifiers.any { after.startsWith(it) }
                if (!isDisqualified) return true // found a valid transactional "txn"
                searchFrom = idx + keyword.length
            }
        }

        return true
    }
}
