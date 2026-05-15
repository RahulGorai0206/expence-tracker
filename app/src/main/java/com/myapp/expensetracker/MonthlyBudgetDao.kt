package com.myapp.expensetracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyBudgetDao {

    /** Get budget record for a specific month (e.g. "2026-05"). */
    @Query("SELECT * FROM monthly_budgets WHERE monthKey = :monthKey")
    suspend fun getBudgetForMonth(monthKey: String): MonthlyBudget?

    /** Reactive version — emits whenever the row changes. */
    @Query("SELECT * FROM monthly_budgets WHERE monthKey = :monthKey")
    fun observeBudgetForMonth(monthKey: String): Flow<MonthlyBudget?>

    /**
     * Carry-forward: get the most recent budget at or before the given month.
     * This is the fallback when no explicit budget exists for a month.
     */
    @Query("SELECT * FROM monthly_budgets WHERE monthKey <= :monthKey ORDER BY monthKey DESC LIMIT 1")
    suspend fun getEffectiveBudget(monthKey: String): MonthlyBudget?

    /** Reactive carry-forward: observe the effective budget for a month. */
    @Query("SELECT * FROM monthly_budgets WHERE monthKey <= :monthKey ORDER BY monthKey DESC LIMIT 1")
    fun observeEffectiveBudget(monthKey: String): Flow<MonthlyBudget?>

    /** Get the latest budget ever set (for suggestions). */
    @Query("SELECT * FROM monthly_budgets ORDER BY monthKey DESC LIMIT 1")
    suspend fun getLatestBudget(): MonthlyBudget?

    /** Insert or update a month's budget. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: MonthlyBudget)
}
