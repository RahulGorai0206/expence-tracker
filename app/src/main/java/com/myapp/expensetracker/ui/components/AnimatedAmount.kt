package com.myapp.expensetracker.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Counts a headline figure up to its new value instead of snapping.
 *
 * Only worth it on the few numbers the user actually watches — the balance and
 * the analytics totals. Applying it to every number on screen turns a nice
 * detail into visual noise, and makes small lists feel unsettled.
 *
 * The first value is shown immediately rather than animating from zero: on a
 * cold start that would read as the app still loading.
 */
@Composable
fun animatedAmount(target: Double, durationMillis: Int = 650): Double {
    val hasSettled = remember { SettledOnce() }
    val isFirst = !hasSettled.value
    if (isFirst) hasSettled.value = true

    val animated by animateFloatAsState(
        targetValue = target.toFloat(),
        animationSpec = tween(
            durationMillis = if (isFirst) 0 else durationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "animatedAmount"
    )
    return animated.toDouble()
}

/** Integer counterpart, for counts like "spending days". */
@Composable
fun animatedCount(target: Int, durationMillis: Int = 650): Int {
    val value = animatedAmount(target.toDouble(), durationMillis)
    // rememberUpdatedState keeps rounding stable across recompositions.
    val rounded by rememberUpdatedState(Math.round(value).toInt())
    return rounded
}

/** Tiny holder so the "first frame" flag survives recomposition. */
private class SettledOnce {
    var value: Boolean = false
}
