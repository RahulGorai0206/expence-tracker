package com.myapp.expensetracker

import java.io.File

/**
 * Loads the rule set that actually ships, straight from the repo file, so tests
 * exercise the real thing rather than a fixture that can drift from it.
 */
object ShippedRules {

    private val candidatePaths = listOf(
        "../rules/extraction-rules.json",   // unit tests run with cwd = app/
        "rules/extraction-rules.json",
        "../../rules/extraction-rules.json"
    )

    fun rawJson(): String {
        val file = candidatePaths.map(::File).firstOrNull { it.exists() }
            ?: error(
                "Could not locate rules/extraction-rules.json from ${File(".").absolutePath}"
            )
        return file.readText()
    }

    fun load(): ExtractionRules =
        when (val result = ExtractionRulesParser.parse(rawJson())) {
            is ExtractionRulesParser.Result.Success -> result.rules
            is ExtractionRulesParser.Result.Failure ->
                error("Shipped extraction rules are invalid: ${result.reason}")
        }
}
