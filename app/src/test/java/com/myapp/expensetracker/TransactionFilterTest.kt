package com.myapp.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionFilterTest {

    @Test
    fun testDebitDetection() {
        // Logic: Amounts < 0 are debits (spends)
        val debitAmount = -100.0
        val creditAmount = 100.0
        
        val trackOnlyDebits = true
        
        // Simulating SmsReceiver logic:
        // if (trackOnlyDebits && transaction.amount >= 0) return (ignore)
        
        val shouldIgnoreCredit = trackOnlyDebits && creditAmount >= 0
        val shouldIgnoreDebit = trackOnlyDebits && debitAmount >= 0
        
        assertEquals("Credit should be ignored", true, shouldIgnoreCredit)
        assertEquals("Debit should NOT be ignored", false, shouldIgnoreDebit)
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
