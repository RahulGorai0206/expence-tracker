package com.myapp.expensetracker.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.expensetracker.Transaction
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionListItem(
    transaction: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val cardShape = RoundedCornerShape(24.dp)
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "transactionContainer"
    )
    val tonalElevation by animateDpAsState(
        targetValue = if (selected || selectionMode) 0.dp else 1.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "transactionTonalElevation"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (selected || selectionMode) 0.dp else 2.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "transactionShadowElevation"
    )

    Surface(
        color = containerColor,
        shape = cardShape,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .animateContentSize(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val categoryInfo = getCategoryInfo(transaction.category)
            val icon = categoryInfo.icon
            val color = categoryInfo.color

            AnimatedVisibility(
                visible = selectionMode,
                enter = fadeIn(tween(160)) + expandHorizontally(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Start
                ),
                exit = fadeOut(tween(120)) + shrinkHorizontally(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Start
                )
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onClick() }
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    null, 
                    tint = color,
                    modifier = Modifier.size(26.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.sender, 
                    fontWeight = FontWeight.ExtraBold, 
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    transaction.category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Surface(
                    color = when (transaction.type) {
                        "manual" -> MaterialTheme.colorScheme.tertiaryContainer
                        "AI" -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        transaction.type.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = when (transaction.type) {
                            "manual" -> MaterialTheme.colorScheme.tertiary
                            "AI" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (transaction.amount > 0) "+\u20B9${"%,.0f".format(transaction.amount)}" else "-\u20B9${
                        "%,.0f".format(
                            -transaction.amount
                        )
                    }",
                    fontWeight = FontWeight.Black,
                    color = if (transaction.amount > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (transaction.syncStatus == "pending") {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    } else if (transaction.syncStatus == "synced") {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.CloudDone,
                            null,
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFF4CAF50).copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    } else if (transaction.syncStatus == "failed") {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.CloudOff,
                            null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    Text(
                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(transaction.date)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}
