package com.myapp.expensetracker.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Placeholders shaped like the content that is coming.
 *
 * These exist less for polish than for correctness: the screens below render
 * from a Room Flow whose first emission is an empty list, so without a loading
 * state they briefly showed "No spending data" / "No transactions" before the
 * real data arrived. A skeleton says "loading", which an empty state actively
 * contradicts.
 */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmerOffset"
    )

    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh

    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(offset - 400f, 0f),
        end = Offset(offset, 0f)
    )
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    width: Dp? = null,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(shape)
            .background(shimmerBrush())
    )
}

/** Stand-in for the transaction rows on Home and History. */
@Composable
fun TransactionListSkeleton(
    rows: Int = 6,
    modifier: Modifier = Modifier
) {
    Column(
        // Announced as one unit so TalkBack says "Loading" rather than reading
        // out a dozen meaningless placeholder shapes.
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Loading transactions" },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(
                    modifier = Modifier.size(44.dp),
                    height = 44.dp,
                    width = 44.dp,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkeletonBox(height = 14.dp, width = 140.dp)
                    SkeletonBox(height = 11.dp, width = 90.dp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                SkeletonBox(height = 16.dp, width = 64.dp)
            }
        }
    }
}

/** Stand-in for the analytics headline card. */
@Composable
fun SummaryCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(24.dp)
            .semantics { contentDescription = "Loading spending summary" },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SkeletonBox(height = 12.dp, width = 100.dp)
        SkeletonBox(height = 38.dp, width = 200.dp, shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(3) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(height = 16.dp, width = 56.dp)
                    SkeletonBox(height = 10.dp, width = 44.dp)
                }
            }
        }
    }
}

/** Stand-in for a chart block, sized to match the real one so nothing jumps. */
@Composable
fun ChartSkeleton(
    height: Dp = 200.dp,
    circular: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "Loading chart" },
        contentAlignment = Alignment.Center
    ) {
        if (circular) {
            SkeletonBox(
                modifier = Modifier.size(160.dp),
                height = 160.dp,
                width = 160.dp,
                shape = CircleShape
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                listOf(0.45f, 0.7f, 0.35f, 0.9f, 0.55f, 0.75f).forEach { fraction ->
                    SkeletonBox(
                        modifier = Modifier.weight(1f),
                        height = height * fraction,
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    )
                }
            }
        }
    }
}
