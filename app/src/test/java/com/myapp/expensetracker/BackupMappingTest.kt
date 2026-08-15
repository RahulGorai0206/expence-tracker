package com.myapp.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup file format is hand-editable and survives app upgrades, so the
 * importer must never trust it. These cover the mapping layer that stands
 * between a parsed file and the database.
 */
class BackupMappingTest {

    private fun transactionDto(
        amount: Double? = -250.0,
        date: Long? = 1_700_000_000_000L,
        body: String? = "Rs.250 debited",
        bodyHash: Int? = null,
        remoteId: String? = null,
        syncStatus: String? = null
    ) = TransactionBackup(
        remoteId = remoteId,
        syncStatus = syncStatus,
        sender = "HDFCBK",
        amount = amount,
        date = date,
        body = body,
        bodyHash = bodyHash,
        category = null,
        tag = null,
        status = null,
        type = null,
        latitude = null,
        longitude = null
    )

    @Test
    fun `transaction without an amount is rejected`() {
        assertNull(transactionDto(amount = null).toEntity())
    }

    @Test
    fun `transaction without a date is rejected`() {
        assertNull(transactionDto(date = null).toEntity())
    }

    @Test
    fun `non-finite amounts are rejected`() {
        assertNull(transactionDto(amount = Double.NaN).toEntity())
        assertNull(transactionDto(amount = Double.POSITIVE_INFINITY).toEntity())
    }

    @Test
    fun `missing optional fields fall back to entity defaults`() {
        val entity = transactionDto().toEntity()!!

        assertEquals("Other", entity.category)
        assertEquals("", entity.tag)
        assertEquals("Cleared", entity.status)
        assertEquals("automated", entity.type)
        assertEquals(0, entity.id)
    }

    @Test
    fun `missing bodyHash is recomputed from the body`() {
        val entity = transactionDto(body = "Rs.250 debited", bodyHash = null).toEntity()!!
        assertEquals("Rs.250 debited".hashCode(), entity.bodyHash)
    }

    @Test
    fun `stored bodyHash is preserved so dedup survives a round trip`() {
        // A hash that does NOT match the body — e.g. written by an older build.
        val entity = transactionDto(bodyHash = 12345).toEntity()!!
        assertEquals(12345, entity.bodyHash)
    }

    @Test
    fun `rows with no remote counterpart are re-queued for sync`() {
        val entity = transactionDto(remoteId = null, syncStatus = "synced").toEntity()!!
        assertEquals("failed", entity.syncStatus)
    }

    @Test
    fun `rows with a remote id keep their recorded sync status`() {
        val entity = transactionDto(remoteId = "REC-1", syncStatus = "synced").toEntity()!!
        assertEquals("synced", entity.syncStatus)
    }

    @Test
    fun `budget needs both a month key and an amount`() {
        assertNull(MonthlyBudgetBackup(monthKey = null, amount = 100.0).toEntity())
        assertNull(MonthlyBudgetBackup(monthKey = "2026-08", amount = null).toEntity())
        assertNull(MonthlyBudgetBackup(monthKey = "   ", amount = 100.0).toEntity())

        val entity = MonthlyBudgetBackup(monthKey = " 2026-08 ", amount = 100.0).toEntity()!!
        assertEquals("2026-08", entity.monthKey)
        assertEquals(100.0, entity.amount, 0.0001)
    }

    // ── Merge identity ───────────────────────────────────────────────────────

    private fun transaction(amount: Double, date: Long, body: String) = Transaction(
        sender = "HDFCBK",
        amount = amount,
        date = date,
        body = body
    )

    @Test
    fun `identical rows share a dedup key so re-import is a no-op`() {
        val a = transaction(-250.0, 1_700_000_000_000L, "Rs.250 debited")
        val b = transaction(-250.0, 1_700_000_000_000L, "Rs.250 debited")
        assertEquals(a.dedupKey(), b.dedupKey())
    }

    @Test
    fun `same amount at a different time is a distinct row`() {
        val a = transaction(-50.0, 1_700_000_000_000L, "Rs.50 debited at METRO")
        val b = transaction(-50.0, 1_700_086_400_000L, "Rs.50 debited at METRO")
        assertNotEquals(a.dedupKey(), b.dedupKey())
    }

    @Test
    fun `same time and body but different amount is a distinct row`() {
        val a = transaction(-50.0, 1_700_000_000_000L, "debited")
        val b = transaction(-70.0, 1_700_000_000_000L, "debited")
        assertNotEquals(a.dedupKey(), b.dedupKey())
    }

    @Test
    fun `a full round trip through the DTO preserves the dedup key`() {
        val original = transaction(-1234.56, 1_700_000_000_000L, "Rs.1,234.56 spent")
        val restored = TransactionBackup(
            remoteId = original.remoteId,
            syncStatus = original.syncStatus,
            sender = original.sender,
            amount = original.amount,
            date = original.date,
            body = original.body,
            bodyHash = original.bodyHash,
            category = original.category,
            tag = original.tag,
            status = original.status,
            type = original.type,
            latitude = original.latitude,
            longitude = original.longitude
        ).toEntity()!!

        assertEquals(original.dedupKey(), restored.dedupKey())
    }

    @Test
    fun `split events are distinguished by name and creation time`() {
        assertEquals(splitEventDedupKey("Goa Trip", 100L), splitEventDedupKey("Goa Trip", 100L))
        assertNotEquals(splitEventDedupKey("Goa Trip", 100L), splitEventDedupKey("Goa Trip", 200L))
        assertNotEquals(splitEventDedupKey("Goa Trip", 100L), splitEventDedupKey("Manali", 100L))
    }

    @Test
    fun `unknown split mode falls back to even rather than corrupting the row`() {
        assertEquals(SplitMode.EVEN, SplitMode.fromDb("nonsense-from-a-hand-edited-file"))
        assertEquals(SplitMode.EVEN, SplitMode.fromDb(""))
        assertEquals(SplitMode.PERCENTAGE, SplitMode.fromDb("percentage"))
    }

    @Test
    fun `export file names are scoped and safe`() {
        val data = BackupManager.suggestedFileName(BackupScope.DATA)
        val full = BackupManager.suggestedFileName(BackupScope.FULL)

        assertTrue(data.startsWith("expense-tracker-data-"))
        assertTrue(full.startsWith("expense-tracker-full-"))
        assertTrue(data.endsWith(".json"))
        assertTrue(full.endsWith(".json"))
        assertTrue(data.none { it == '/' || it == ':' })
    }

    @Test
    fun `unknown scope ids degrade to data rather than throwing`() {
        assertEquals(BackupScope.DATA, BackupScope.fromId(null))
        assertEquals(BackupScope.DATA, BackupScope.fromId("bogus"))
        assertEquals(BackupScope.FULL, BackupScope.fromId("full"))
    }
}
