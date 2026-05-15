package com.myapp.expensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.expensetracker.MonthlyBudget
import com.myapp.expensetracker.MonthlyBudgetDao
import com.myapp.expensetracker.Transaction
import com.myapp.expensetracker.TransactionDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeViewModel(
    private val dao: TransactionDao,
    private val budgetDao: MonthlyBudgetDao
) : ViewModel() {

    val transactions: StateFlow<List<Transaction>> = dao.getAllTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentBudget = MutableStateFlow(0.0)
    val currentBudget: StateFlow<Double> = _currentBudget.asStateFlow()

    private val _smartSuggestions = MutableStateFlow<List<Double>>(emptyList())
    val smartSuggestions: StateFlow<List<Double>> = _smartSuggestions.asStateFlow()

    init {
        loadBudget()
        loadSmartSuggestions()
    }

    /** Current month key in "yyyy-MM" format. */
    private fun currentMonthKey(): String =
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    /** Load the effective budget for the current month (with carry-forward). */
    private fun loadBudget() {
        viewModelScope.launch {
            budgetDao.observeEffectiveBudget(currentMonthKey()).collect { budget ->
                _currentBudget.value = budget?.amount ?: 0.0
            }
        }
    }

    /** Save a new budget for the current month. */
    fun saveBudget(amount: Double) {
        viewModelScope.launch {
            budgetDao.upsert(
                MonthlyBudget(
                    monthKey = currentMonthKey(),
                    amount = amount
                )
            )
            // _currentBudget will auto-update via the Flow in loadBudget()
        }
    }

    /**
     * Generate smart preset suggestions based on spending history.
     * Returns 3 amounts: conservative (avg × 0.8), moderate (avg), generous (avg × 1.2).
     * Falls back to fixed presets if no history exists.
     */
    private fun loadSmartSuggestions() {
        viewModelScope.launch {
            // Get spending for the last 3 months to calculate average
            val cal = Calendar.getInstance()
            val endDate = cal.timeInMillis

            cal.add(Calendar.MONTH, -3)
            val startDate = cal.timeInMillis

            val avgSpend = dao.getTotalSpentBetween(startDate, endDate)?.let { it / 3.0 }

            val suggestions = if (avgSpend != null && avgSpend > 0) {
                // Round to nearest 1000
                val base = (avgSpend / 1000).toLong() * 1000
                listOf(
                    (base * 0.8).coerceAtLeast(1000.0),
                    base.toDouble().coerceAtLeast(2000.0),
                    (base * 1.2).coerceAtLeast(3000.0)
                ).distinct()
            } else {
                // Default presets when no history
                listOf(10000.0, 20000.0, 50000.0)
            }

            _smartSuggestions.value = suggestions
        }
    }
}
