package com.myapp.expensetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String,
    val amount: Double,
    val date: Long,
    val body: String,
    val category: String = "Other",
    val status: String = "Cleared"
)
