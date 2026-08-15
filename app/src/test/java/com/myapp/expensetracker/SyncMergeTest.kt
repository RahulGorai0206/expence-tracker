package com.myapp.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cloud restore used to wipe the table and re-insert. It now merges, which
 * makes "which local row is this remote record?" the load-bearing decision —
 * get it wrong and rows are either duplicated or silently overwritten.
 */
class SyncMergeTest {

    private fun local(
        id: Int,
        amount: Double = -250.0,
        date: Long = 1_700_000_000_000L,
        body: String = "Rs.250 debited",
        remoteId: String? = null,
        syncStatus: String = "synced"
    ) = Transaction(
        id = id,
        remoteId = remoteId,
        syncStatus = syncStatus,
        sender = "HDFCBK",
        amount = amount,
        date = date,
        body = body
    )

    @Test
    fun `matches on remote id`() {
        val existing = local(id = 7, remoteId = "REC-1")
        val matcher = LocalTransactionMatcher(listOf(existing))

        val matched = matcher.match("REC-1", local(id = 0, amount = -999.0, body = "different"))

        assertEquals(7, matched?.id)
    }

    @Test
    fun `falls back to content for a row that was never uploaded`() {
        val neverUploaded = local(id = 3, remoteId = null, syncStatus = "failed")
        val matcher = LocalTransactionMatcher(listOf(neverUploaded))

        val incoming = local(id = 0, remoteId = "REC-9")
        val matched = matcher.match("REC-9", incoming)

        // Same date/amount/body — this is the same transaction, now linked.
        assertEquals(3, matched?.id)
    }

    @Test
    fun `unrelated record matches nothing and will be inserted`() {
        val matcher = LocalTransactionMatcher(listOf(local(id = 1, remoteId = "REC-1")))

        val matched = matcher.match("REC-2", local(id = 0, amount = -77.0, body = "other"))

        assertNull(matched)
    }

    @Test
    fun `a local row is claimed only once so two remote records never collapse`() {
        val existing = local(id = 5)
        val matcher = LocalTransactionMatcher(listOf(existing))

        val first = matcher.match("REC-1", local(id = 0))
        val second = matcher.match("REC-2", local(id = 0))

        assertEquals(5, first?.id)
        // Second must NOT reuse row 5 — it becomes a fresh insert instead.
        assertNull(second)
    }

    @Test
    fun `remote id match wins over a content match on a different row`() {
        val contentTwin = local(id = 1, remoteId = null)
        val idMatch = local(id = 2, remoteId = "REC-1", amount = -500.0, body = "other body")
        val matcher = LocalTransactionMatcher(listOf(contentTwin, idMatch))

        // Incoming carries REC-1 but its content equals row 1.
        val incoming = local(id = 0)
        val matched = matcher.match("REC-1", incoming)

        assertEquals(2, matched?.id)
    }

    @Test
    fun `content fallback still applies when the id match was already claimed`() {
        val idRow = local(id = 1, remoteId = "REC-1", amount = -500.0, body = "other body")
        val contentRow = local(id = 2, remoteId = null)
        val matcher = LocalTransactionMatcher(listOf(idRow, contentRow))

        val firstClaim = matcher.match("REC-1", local(id = 0, amount = -500.0, body = "other body"))
        assertEquals(1, firstClaim?.id)

        // A second record also carrying REC-1, but whose content matches row 2.
        val second = matcher.match("REC-1", local(id = 0))
        assertEquals(2, second?.id)
    }

    @Test
    fun `null remote id relies purely on content`() {
        val matcher = LocalTransactionMatcher(listOf(local(id = 4)))

        val matched = matcher.match(null, local(id = 0))

        assertNotNull(matched)
        assertEquals(4, matched?.id)
    }

    @Test
    fun `unsynced local rows are visible to the matcher and therefore never orphaned`() {
        // The old wipe-and-reinsert destroyed these outright.
        val pending = local(id = 8, remoteId = null, syncStatus = "pending")
        val failed = local(id = 9, remoteId = null, syncStatus = "failed", amount = -12.0, body = "b")
        val matcher = LocalTransactionMatcher(listOf(pending, failed))

        assertEquals(8, matcher.match(null, local(id = 0))?.id)
        assertEquals(9, matcher.match(null, local(id = 0, amount = -12.0, body = "b"))?.id)
    }
}
