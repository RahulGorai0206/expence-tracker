package com.myapp.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules file is fetched from a public repo at runtime, so the parser is the
 * only thing standing between an edit to that file and transaction detection
 * breaking on someone's phone. Anything malformed must be rejected outright.
 */
class ExtractionRulesParserTest {

    private fun parse(json: String) = ExtractionRulesParser.parse(json)

    private fun failureReason(json: String): String {
        val result = parse(json)
        assertTrue("expected rejection, got $result", result is ExtractionRulesParser.Result.Failure)
        return (result as ExtractionRulesParser.Result.Failure).reason
    }

    private val minimalValid = """
        {
          "schemaVersion": 1,
          "version": 3,
          "releasedAt": "2026-08-16",
          "spendKeywords": ["debited"],
          "receiveKeywords": ["credited"],
          "amountPatterns": ["(?:Rs\\.?|INR)\\s*(\\d+)"]
        }
    """.trimIndent()

    // ── The file that actually ships ─────────────────────────────────────────

    @Test
    fun `the shipped rules file is valid`() {
        val rules = ShippedRules.load()

        assertTrue(rules.version >= 1)
        assertTrue(rules.spendKeywords.isNotEmpty())
        assertTrue(rules.receiveKeywords.isNotEmpty())
        assertTrue(rules.amountPatterns.isNotEmpty())
        assertTrue(rules.categories.isNotEmpty())
    }

    @Test
    fun `shipped rules preserve the behaviour the extractor depends on`() {
        val rules = ShippedRules.load()

        assertTrue("debited" in rules.spendKeywords)
        assertTrue("credited" in rules.receiveKeywords)
        assertTrue("txn" in rules.spendKeywords)
        assertTrue("limit" in rules.txnDisqualifiers)
        assertEquals(15, rules.balanceLookback)
        assertTrue(rules.categories.containsKey("Dining"))
    }

    // ── Acceptance ───────────────────────────────────────────────────────────

    @Test
    fun `a minimal valid file parses`() {
        val result = parse(minimalValid)
        assertTrue(result is ExtractionRulesParser.Result.Success)

        val rules = (result as ExtractionRulesParser.Result.Success).rules
        assertEquals(3, rules.version)
        assertEquals("2026-08-16", rules.releasedAt)
        assertEquals(15, rules.balanceLookback) // default when omitted
    }

    @Test
    fun `blank and null terms are dropped rather than poisoning matching`() {
        val json = """
            {"schemaVersion":1,"version":1,
             "spendKeywords":["debited","  ","spent"],
             "receiveKeywords":["credited"],
             "amountPatterns":["(\\d+)"]}
        """.trimIndent()

        val rules = (parse(json) as ExtractionRulesParser.Result.Success).rules
        assertEquals(listOf("debited", "spent"), rules.spendKeywords)
    }

    // ── Rejection ────────────────────────────────────────────────────────────

    @Test
    fun `malformed json is rejected`() {
        assertTrue(failureReason("{ not json").contains("valid JSON"))
    }

    @Test
    fun `a newer schema is refused rather than half-understood`() {
        val json = minimalValid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        assertTrue(failureReason(json).contains("Update the app"))
    }

    @Test
    fun `a missing version is rejected`() {
        val json = minimalValid.replace("\"version\": 3,", "")
        assertTrue(failureReason(json).contains("version"))
    }

    @Test
    fun `empty keyword lists are rejected because detection would stop`() {
        assertTrue(
            failureReason(minimalValid.replace("[\"debited\"]", "[]"))
                .contains("spend keywords")
        )
        assertTrue(
            failureReason(minimalValid.replace("[\"credited\"]", "[]"))
                .contains("receive keywords")
        )
    }

    @Test
    fun `an invalid regex is rejected instead of crashing at parse time`() {
        val json = minimalValid.replace(
            "\"(?:Rs\\\\.?|INR)\\\\s*(\\\\d+)\"",
            "\"(unclosed[\""
        )
        assertTrue(failureReason(json).contains("regex"))
    }

    @Test
    fun `a pattern with no capture group is rejected`() {
        val json = minimalValid.replace(
            "\"(?:Rs\\\\.?|INR)\\\\s*(\\\\d+)\"",
            "\"Rs\\\\d+\""
        )
        assertTrue(failureReason(json).contains("capture group"))
    }

    @Test
    fun `an absurdly long pattern is rejected`() {
        val long = "(" + "a".repeat(400) + ")"
        val json = minimalValid.replace("\"(?:Rs\\\\.?|INR)\\\\s*(\\\\d+)\"", "\"$long\"")
        assertTrue(failureReason(json).contains("long"))
    }

    @Test
    fun `an out of range balance lookback is rejected`() {
        val json = minimalValid.replace("\"version\": 3,", "\"version\": 3, \"balanceLookback\": 9999,")
        assertTrue(failureReason(json).contains("balanceLookback"))
    }

    @Test
    fun `empty categories are dropped without failing the whole file`() {
        val json = minimalValid.replace(
            "\"version\": 3,",
            "\"version\": 3, \"categories\": {\"Dining\": [], \"Transport\": [\"Uber\"]},"
        )
        val rules = (parse(json) as ExtractionRulesParser.Result.Success).rules

        assertFalse(rules.categories.containsKey("Dining"))
        assertEquals(listOf("Uber"), rules.categories["Transport"])
    }
}
