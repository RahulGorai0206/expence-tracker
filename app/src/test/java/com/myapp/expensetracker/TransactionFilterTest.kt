package com.myapp.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionFilterTest {

    @Test
    fun testDebitDetection() {
        // Logic: Amounts < 0 are debits (spends)
        assertEquals("Credit should be ignored", true, shouldIgnore(trackOnlyDebits = true, amount = 100.0))
        assertEquals("Debit should NOT be ignored", false, shouldIgnore(trackOnlyDebits = true, amount = -100.0))

        // Test when trackOnlyDebits is false
        assertEquals("Credit should NOT be ignored when filtering disabled", false, shouldIgnore(trackOnlyDebits = false, amount = 100.0))
        assertEquals("Debit should NOT be ignored when filtering disabled", false, shouldIgnore(trackOnlyDebits = false, amount = -100.0))
    }

    private fun shouldIgnore(trackOnlyDebits: Boolean, amount: Double): Boolean {
        return trackOnlyDebits && amount >= 0
    }

    @Test
    fun testCloudSyncFiltering() {
        // Mock remote records
        val activeRecord = RemoteRecord(id = "1", amount = 100.0, status = "active")
        val deletedRecord = RemoteRecord(id = "2", amount = 100.0, status = "deleted")
        
        val records = listOf(activeRecord, deletedRecord)
        
        // Simulating GoogleSheetsLogger.syncFromCloud logic:
        val filtered = records.mapNotNull { remote ->
            if (remote.id.isNullOrBlank() || remote.amount == null || remote.status == "deleted") null
            else remote
        }
        
        assertEquals(1, filtered.size)
        assertEquals("1", filtered[0].id)
    }

    // Helper data class for testing
    data class RemoteRecord(val id: String?, val amount: Double?, val status: String?)
}
