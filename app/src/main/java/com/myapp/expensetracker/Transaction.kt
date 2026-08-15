package com.myapp.expensetracker

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// bodyHash is probed on every incoming SMS by the dedup check — without this
// index that is a full table scan per message.
@Entity(tableName = "transactions", indices = [Index(value = ["bodyHash"])])
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val remoteId: String? = null,
    val syncStatus: String = "synced", // "pending", "synced", "failed"
    val sender: String,
    val amount: Double,
    val date: Long,
    val body: String,
    val bodyHash: Int = body.hashCode(),
    val category: String = "Other",
    val tag: String = "",
    val status: String = "Cleared",
    val type: String = "automated",
    val latitude: Double? = null,
    val longitude: Double? = null
)
