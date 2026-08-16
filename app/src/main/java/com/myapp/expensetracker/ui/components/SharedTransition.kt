package com.myapp.expensetracker.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Plumbing for the list → detail shared element.
 *
 * Passed as composition locals rather than parameters because the scopes are
 * ambient UI context that would otherwise have to be threaded through
 * HomeScreen, TransactionScreen, their LazyColumns and finally the row — four
 * signatures that have nothing else to do with the transition.
 *
 * Null means "no shared transition available", and every call site degrades to
 * a plain modifier, so screens still work when rendered outside the layout
 * (previews, tests).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The detail overlay's visibility scope, present only while it is on screen. */
val LocalDetailAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Id of the transaction currently open in the detail overlay, if any. */
val LocalOpenTransactionId = compositionLocalOf<Int?> { null }

private fun transactionIconKey(id: Int): String = "transaction-icon-$id"

/**
 * Marks the category badge as a shared element.
 *
 * The list side uses caller-managed visibility because the list is *not* inside
 * an AnimatedVisibility — it stays composed underneath the detail overlay, so
 * there is no transition scope to attach to. It reports itself hidden while its
 * own transaction is open, which is what hands the badge over to the detail.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCategoryBadge(transactionId: Int, isDetail: Boolean): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this

    with(sharedScope) {
        val state = rememberSharedContentState(key = transactionIconKey(transactionId))

        return if (isDetail) {
            val detailScope = LocalDetailAnimatedScope.current ?: return this@sharedCategoryBadge
            this@sharedCategoryBadge.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = detailScope
            )
        } else {
            val openId = LocalOpenTransactionId.current
            this@sharedCategoryBadge.sharedElementWithCallerManagedVisibility(
                sharedContentState = state,
                visible = openId != transactionId
            )
        }
    }
}
