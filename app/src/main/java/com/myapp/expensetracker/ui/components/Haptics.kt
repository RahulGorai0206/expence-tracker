package com.myapp.expensetracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Centralised haptics.
 *
 * Routed through one type so feedback stays consistent across screens — the
 * common failure mode is a different vibration for the same kind of action in
 * two places, which reads as sloppy rather than responsive. Keeping it in one
 * place also means a "reduce haptics" preference only has to be added here.
 *
 * Used sparingly and deliberately: state changes the user *caused* and would
 * otherwise have to verify visually.
 */
@Immutable
class Haptics(private val feedback: HapticFeedback) {

    /** Light tick — moving between tabs, stepping through a selection. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    fun toggleOn() = feedback.performHapticFeedback(HapticFeedbackType.ToggleOn)

    fun toggleOff() = feedback.performHapticFeedback(HapticFeedbackType.ToggleOff)

    fun toggle(enabled: Boolean) = if (enabled) toggleOn() else toggleOff()

    /** An action completed — saved, synced, marked paid. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** An action failed or was refused. */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)

    /** Entering a mode via long press, e.g. multi-select. */
    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
