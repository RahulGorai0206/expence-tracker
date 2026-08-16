package com.myapp.expensetracker.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start timings, so performance work can be verified instead of assumed.
 *
 * Run with a device connected:
 *   ./gradlew :baselineprofile:connectedBenchmarkAndroidTest
 *
 * [startupNoCompilation] is the floor and [startupBaselineProfile] the shipped
 * behaviour; the gap between them is what the profile actually buys. Run both
 * before and after enabling R8 to see its effect separately.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = measureStartup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() =
        measureStartup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measureStartup(mode: CompilationMode) = rule.measureRepeated(
        packageName = BaselineProfileGenerator.PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }
}
