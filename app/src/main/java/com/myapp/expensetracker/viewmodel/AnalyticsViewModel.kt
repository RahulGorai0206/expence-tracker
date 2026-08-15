package com.myapp.expensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.expensetracker.CategorySpending
import com.myapp.expensetracker.DailySpending
import com.myapp.expensetracker.MonthToDatePeriods
import com.myapp.expensetracker.MonthlySpending
import com.myapp.expensetracker.TagSpending
import com.myapp.expensetracker.TransactionDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

enum class DateRangePreset(val label: String) {
    THIS_MONTH("This Month"),
    THREE_MONTHS("3 Months"),
    SIX_MONTHS("6 Months"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time"),
    CUSTOM("Custom")
}

data class AnalyticsState(
    val selectedPreset: DateRangePreset = DateRangePreset.SIX_MONTHS,
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val totalSpent: Double = 0.0,
    val transactionCount: Int = 0,
    val spendingDays: Int = 0,
    val dailyAverage: Double = 0.0,
    val monthlySpending: List<MonthlySpending> = emptyList(),
    val categorySpending: List<CategorySpending> = emptyList(),
    val tagSpending: List<TagSpending> = emptyList(),
    val dailySpending: List<DailySpending> = emptyList(),
    val topSpendingDay: DailySpending? = null,
    val topCategory: CategorySpending? = null,
    /** This month so far vs the same stretch of days last month, as a %. */
    val monthOverMonthChange: Double? = null,
    val isLoading: Boolean = true
)

/** Range-dependent metrics, kept separate from the range-independent comparison. */
private data class RangeMetrics(
    val totalSpent: Double,
    val transactionCount: Int,
    val spendingDays: Int,
    val dailyAverage: Double,
    val monthlySpending: List<MonthlySpending>,
    val categorySpending: List<CategorySpending>,
    val tagSpending: List<TagSpending>,
    val dailySpending: List<DailySpending>,
    val topSpendingDay: DailySpending?,
    val topCategory: CategorySpending?
)

class AnalyticsViewModel(private val dao: TransactionDao) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()

    private var dataCollectionJob: Job? = null
    private var monthComparisonJob: Job? = null

    init {
        setPreset(DateRangePreset.SIX_MONTHS)
        loadMonthComparison()
    }

    /**
     * "vs last month" is deliberately independent of the selected range: it
     * always compares this month so far against the same stretch of days last
     * month, so the figure is like-for-like rather than a partial month held up
     * against a complete one.
     */
    private fun loadMonthComparison() {
        monthComparisonJob?.cancel()
        monthComparisonJob = viewModelScope.launch {
            val periods = MonthToDatePeriods.at(System.currentTimeMillis())

            combine(
                dao.getTotalSpentInRange(periods.currentStart, periods.currentEnd),
                dao.getTotalSpentInRange(periods.previousStart, periods.previousEnd)
            ) { current, previous ->
                MonthToDatePeriods.percentChange(current ?: 0.0, previous ?: 0.0)
            }.collect { change ->
                _state.update { it.copy(monthOverMonthChange = change) }
            }
        }
    }

    fun setPreset(preset: DateRangePreset) {
        val cal = Calendar.getInstance()
        val endDate = cal.timeInMillis

        val startDate = when (preset) {
            DateRangePreset.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }

            DateRangePreset.THREE_MONTHS -> {
                cal.add(Calendar.MONTH, -3)
                cal.timeInMillis
            }

            DateRangePreset.SIX_MONTHS -> {
                cal.add(Calendar.MONTH, -6)
                cal.timeInMillis
            }

            DateRangePreset.THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }

            DateRangePreset.ALL_TIME -> 0L
            DateRangePreset.CUSTOM -> return // handled by setCustomRange
        }

        _state.value =
            _state.value.copy(selectedPreset = preset, startDate = startDate, endDate = endDate)
        loadData(startDate, endDate)
    }

    fun setCustomRange(start: Long, end: Long) {
        _state.value = _state.value.copy(
            selectedPreset = DateRangePreset.CUSTOM,
            startDate = start,
            endDate = end
        )
        loadData(start, end)
    }

    private fun loadData(startDate: Long, endDate: Long) {
        dataCollectionJob?.cancel()
        dataCollectionJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            // combine supports max 5 flows, so nest two combines
            val baseFlow = combine(
                dao.getTotalSpentInRange(startDate, endDate),
                dao.getTransactionCountInRange(startDate, endDate),
                dao.getMonthlySpending(startDate, endDate),
                dao.getCategorySpending(startDate, endDate),
                dao.getDailySpending(startDate, endDate)
            ) { totalSpent, txCount, monthly, categories, daily ->
                Triple(Triple(totalSpent, txCount, monthly), categories, daily)
            }

            combine(
                baseFlow,
                dao.getTagSpending(startDate, endDate)
            ) { base, tags ->
                val (first, categories, daily) = base
                val (totalSpent, txCount, monthly) = first

                val spent = totalSpent ?: 0.0
                val daysInRange =
                    ((endDate - startDate) / (1000L * 60 * 60 * 24)).coerceAtLeast(1)
                val dailyAverage = if (daysInRange > 0) spent / daysInRange else 0.0

                val topDay = daily.maxByOrNull { it.total }
                val topCat = categories.maxByOrNull { it.total }

                // Unique days that had spending
                val uniqueSpendingDays = daily.size

                RangeMetrics(
                    totalSpent = spent,
                    transactionCount = txCount,
                    spendingDays = uniqueSpendingDays,
                    dailyAverage = dailyAverage,
                    monthlySpending = monthly,
                    categorySpending = categories,
                    tagSpending = tags,
                    dailySpending = daily,
                    topSpendingDay = topDay,
                    topCategory = topCat
                )
            }.collect { metrics ->
                // update{} rather than a plain assignment: the month comparison
                // stream writes to the same state concurrently.
                _state.update { current ->
                    current.copy(
                        totalSpent = metrics.totalSpent,
                        transactionCount = metrics.transactionCount,
                        spendingDays = metrics.spendingDays,
                        dailyAverage = metrics.dailyAverage,
                        monthlySpending = metrics.monthlySpending,
                        categorySpending = metrics.categorySpending,
                        tagSpending = metrics.tagSpending,
                        dailySpending = metrics.dailySpending,
                        topSpendingDay = metrics.topSpendingDay,
                        topCategory = metrics.topCategory,
                        isLoading = false
                    )
                }
            }
        }
    }
}
