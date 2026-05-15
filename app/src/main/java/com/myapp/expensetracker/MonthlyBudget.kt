package com.myapp.expensetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monthly_budgets")
data class MonthlyBudget(
    @PrimaryKey val monthKey: String,   // "2026-05" format (YYYY-MM)
    val amount: Double,
    val createdAt: Long = System.currentTimeMillis()
)
