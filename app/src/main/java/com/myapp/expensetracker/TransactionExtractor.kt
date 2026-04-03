package com.myapp.expensetracker

import com.google.mlkit.nl.entityextraction.*
import kotlinx.coroutines.tasks.await

class TransactionExtractor {
    private val entityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH)
            .build()
    )

    suspend fun extractTransaction(body: String, sender: String, timestamp: Long): Transaction? {
        return try {
            entityExtractor.downloadModelIfNeeded().await()
            
            val params = EntityExtractionParams.Builder(body)
                .setEntityTypesFilter(setOf(Entity.TYPE_MONEY))
                .build()
            
            val result = entityExtractor.annotate(params).await()

            var extractedAmount: Double? = null
            
            for (annotation in result) {
                for (entity in annotation.entities) {
                    if (entity is MoneyEntity) {
                        val integerPart = entity.integerPart
                        val fractionalPart = entity.fractionalPart
                        extractedAmount = integerPart.toDouble() + (fractionalPart.toDouble() / 100.0)
                        break
                    }
                }
                if (extractedAmount != null) break
            }

            val keywords = listOf("debited", "spent", "paid", "credited", "transaction", "transferred", "payment", "received", "sent")
            val isTransaction = keywords.any { body.contains(it, ignoreCase = true) }

            val category = when {
                body.contains("Starbucks", true) || body.contains("Coffee", true) || body.contains("Restaurant", true) -> "Dining"
                body.contains("Uber", true) || body.contains("Taxi", true) || body.contains("Fuel", true) -> "Transport"
                body.contains("Market", true) || body.contains("Grocery", true) || body.contains("Foods", true) -> "Groceries"
                body.contains("Shopping", true) || body.contains("Mall", true) || body.contains("Store", true) -> "Shopping"
                body.contains("Bill", true) || body.contains("Utility", true) || body.contains("Electricity", true) -> "Bills"
                else -> "Other"
            }

            if (extractedAmount != null && isTransaction) {
                Transaction(
                    sender = sender,
                    amount = extractedAmount,
                    date = timestamp,
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
