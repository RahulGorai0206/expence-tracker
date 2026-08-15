package com.myapp.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MonthToDatePeriodsTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun millis(
        year: Int,
        month: Int, // 1-based
        day: Int,
        hour: Int = 0,
        minute: Int = 0
    ): Long = Calendar.getInstance(utc).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

    private fun fieldsOf(value: Long): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance(utc).apply { timeInMillis = value }
        return Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `current window runs from the first of the month to now`() {
        val now = millis(2026, 8, 16, hour = 14, minute = 30)
        val periods = MonthToDatePeriods.at(now, utc)

        assertEquals(now, periods.currentEnd)
        assertEquals(Triple(2026, 8, 1), fieldsOf(periods.currentStart))

        val startCal = Calendar.getInstance(utc).apply { timeInMillis = periods.currentStart }
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, startCal.get(Calendar.MINUTE))
        assertEquals(0, startCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `previous window ends at the same day and time one month earlier`() {
        val now = millis(2026, 8, 16, hour = 14, minute = 30)
        val periods = MonthToDatePeriods.at(now, utc)

        assertEquals(Triple(2026, 7, 1), fieldsOf(periods.previousStart))
        assertEquals(Triple(2026, 7, 16), fieldsOf(periods.previousEnd))

        val endCal = Calendar.getInstance(utc).apply { timeInMillis = periods.previousEnd }
        assertEquals(14, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, endCal.get(Calendar.MINUTE))
    }

    @Test
    fun `the two windows span a comparable number of days`() {
        val now = millis(2026, 8, 16, hour = 12)
        val periods = MonthToDatePeriods.at(now, utc)

        val currentSpan = periods.currentEnd - periods.currentStart
        val previousSpan = periods.previousEnd - periods.previousStart

        // This is the whole point of the fix: 16 days vs 16 days, not 16 vs 31.
        assertEquals(currentSpan, previousSpan)
    }

    @Test
    fun `month end clamps into a shorter previous month instead of rolling over`() {
        val now = millis(2026, 3, 31, hour = 9)
        val periods = MonthToDatePeriods.at(now, utc)

        val (year, month, day) = fieldsOf(periods.previousEnd)
        assertEquals(2026, year)
        assertEquals(2, month)         // February, not March
        assertEquals(28, day)          // 2026 is not a leap year
        assertEquals(Triple(2026, 2, 1), fieldsOf(periods.previousStart))
    }

    @Test
    fun `leap year end of month resolves to the 29th`() {
        val now = millis(2028, 3, 31, hour = 9)
        val periods = MonthToDatePeriods.at(now, utc)

        assertEquals(Triple(2028, 2, 29), fieldsOf(periods.previousEnd))
    }

    @Test
    fun `january compares against december of the previous year`() {
        val now = millis(2026, 1, 10, hour = 8)
        val periods = MonthToDatePeriods.at(now, utc)

        assertEquals(Triple(2026, 1, 1), fieldsOf(periods.currentStart))
        assertEquals(Triple(2025, 12, 1), fieldsOf(periods.previousStart))
        assertEquals(Triple(2025, 12, 10), fieldsOf(periods.previousEnd))
    }

    @Test
    fun `first of the month yields a small but valid pair of windows`() {
        val now = millis(2026, 8, 1, hour = 6)
        val periods = MonthToDatePeriods.at(now, utc)

        assertTrue(periods.currentEnd > periods.currentStart)
        assertEquals(Triple(2026, 7, 1), fieldsOf(periods.previousStart))
        assertEquals(Triple(2026, 7, 1), fieldsOf(periods.previousEnd))
        assertEquals(
            periods.currentEnd - periods.currentStart,
            periods.previousEnd - periods.previousStart
        )
    }

    // ── Percentage ───────────────────────────────────────────────────────────

    @Test
    fun `percent change reports an increase and a decrease`() {
        assertEquals(50.0, MonthToDatePeriods.percentChange(150.0, 100.0)!!, 0.0001)
        assertEquals(-25.0, MonthToDatePeriods.percentChange(75.0, 100.0)!!, 0.0001)
        assertEquals(0.0, MonthToDatePeriods.percentChange(100.0, 100.0)!!, 0.0001)
    }

    @Test
    fun `no baseline means no percentage rather than a divide by zero`() {
        assertNull(MonthToDatePeriods.percentChange(120.0, 0.0))
        assertNull(MonthToDatePeriods.percentChange(0.0, 0.0))
        assertNull(MonthToDatePeriods.percentChange(120.0, -5.0))
    }
}
