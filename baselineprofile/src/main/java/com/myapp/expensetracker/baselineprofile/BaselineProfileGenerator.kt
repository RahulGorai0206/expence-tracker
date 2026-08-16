package com.myapp.expensetracker.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods used on the critical startup path so they can
 * be AOT-compiled rather than interpreted on first run.
 *
 * Generate with a device or emulator connected:
 *   ./gradlew :app:generateReleaseBaselineProfile
 *
 * The result lands in app/src/release/generated/baselineProfiles/ and should be
 * committed — it ships in the APK and is applied at install time.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndBrowse() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()

        // The branded splash holds for ~450ms before content is interactive.
        device.waitForIdle()
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)

        // Exercising a scroll captures the list and row composables too, not
        // just the first frame — those dominate perceived jank after launch.
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            repeat(2) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    companion object {
        const val PACKAGE_NAME = "com.myapp.expensetracker"
    }
}
