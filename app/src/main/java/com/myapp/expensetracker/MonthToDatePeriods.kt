package com.myapp.expensetracker

import java.util.Calendar
import java.util.TimeZone

/**
 * Month-to-date comparison windows.
 *
 * "vs last month" is only meaningful when both sides cover the same stretch of
 * days. Comparing the current partial month against the whole of last month
 * makes every month look like a collapse in spending until the very last day.
 */
object MonthToDatePeriods {

    data class Periods(
        val currentStart: Long,
        val currentEnd: Long,
        val previousStart: Long,
        val previousEnd: Long
    )

    /**
     * Current month start → [nowMillis], paired with the previous month start →
     * the same point in that month.
     *
     * Day-of-month is clamped by [Calendar.add]: on 31 March the previous window
     * ends on 28/29 February rather than rolling into March.
     */
    fun at(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Periods {
        val currentStart = startOfMonth(nowMillis, timeZone)

        val previousEndCal = calendarAt(nowMillis, timeZone).apply {
            add(Calendar.MONTH, -1)
        }
        val previousEnd = previousEndCal.timeInMillis
        val previousStart = startOfMonth(previousEnd, timeZone)

        return Periods(
            currentStart = currentStart,
            currentEnd = nowMillis,
            previousStart = previousStart,
            previousEnd = previousEnd
        )
    }

    /** Percentage change between the two windows, or null when there's no baseline. */
    fun percentChange(currentTotal: Double, previousTotal: Double): Double? {
        if (previousTotal <= 0.0) return null
        return ((currentTotal - previousTotal) / previousTotal) * 100
    }

    private fun startOfMonth(millis: Long, timeZone: TimeZone): Long =
        calendarAt(millis, timeZone).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun calendarAt(millis: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = millis }
}
