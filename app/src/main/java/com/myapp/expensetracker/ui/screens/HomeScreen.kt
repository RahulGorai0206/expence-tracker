package com.myapp.expensetracker.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.expensetracker.GoogleSheetsLogger
import com.myapp.expensetracker.viewmodel.HomeViewModel
import org.koin.androidx.compose.koinViewModel
import com.myapp.expensetracker.Transaction
import com.myapp.expensetracker.ui.components.ManualTransactionBottomSheet
import com.myapp.expensetracker.ui.components.TransactionListItem
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(onTransactionClick: (Transaction) -> Unit, onSeeAllClick: () -> Unit, onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = koinViewModel()
    val transactions by viewModel.transactions.collectAsState()
    var showManualLog by remember { mutableStateOf(false) }

    if (showManualLog) {
        ManualTransactionBottomSheet(
            onDismiss = { showManualLog = false },
            onTransactionSaved = { showManualLog = false }
        )
    }

    val totalSpent = remember(transactions) { transactions.filter { it.amount < 0 }.sumOf { abs(it.amount) } }
    
    val sharedPrefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    val budget = remember { mutableStateOf(sharedPrefs.getFloat("budget", 0f)) }
    
    val remainingBudget = budget.value - totalSpent
    
    val totalBalance = remember(transactions) { transactions.sumOf { it.amount } }
    val wholePart = remember(totalSpent) { totalSpent.toInt().toString() }
    val decimalPart =
        remember(totalSpent) { "%.2f".format(totalSpent % 1).removePrefix("0").removePrefix("-0") }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Spacer(modifier = Modifier.height(12.dp))
            
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Welcome Back,",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Financial Dashboard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                val sharedPrefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
                val sheetUrl = sharedPrefs.getString("sheet_url", "") ?: ""
                val scriptUrl = sharedPrefs.getString("script_url", "") ?: ""
                val isSyncEnabled = sheetUrl.isNotBlank() && scriptUrl.isNotBlank()
                
                val pendingCount = transactions.count { it.syncStatus == "pending" }
                val failedCount = transactions.count { it.syncStatus == "failed" }

                val scope = rememberCoroutineScope()
                val failedWithLocation = transactions.any { it.syncStatus == "failed" && it.latitude != null && it.longitude != null }

                Surface(
                    onClick = {
                        if (!isSyncEnabled) {
                            Toast.makeText(context, "Please enable Cloud Sync in settings first", Toast.LENGTH_SHORT).show()
                            onSettingsClick()
                        } else if (failedCount > 0 || pendingCount > 0) {
                            // Resync failed/pending
                            scope.launch {
                                Toast.makeText(context, "Resyncing ${failedCount + pendingCount} transactions...", Toast.LENGTH_SHORT).show()
                                transactions.filter { it.syncStatus == "failed" || it.syncStatus == "pending" }.forEach { 
                                    GoogleSheetsLogger.logAsync(context, it, it.id.toLong())
                                }
                            }
                        } else {
                            // Already synced, but maybe user wants to check settings
                            onSettingsClick()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        !isSyncEnabled -> MaterialTheme.colorScheme.surfaceContainerHigh
                        failedWithLocation -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        pendingCount > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!isSyncEnabled) {
                            Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
                        } else if (pendingCount > 0) {
                            val infiniteTransition = rememberInfiniteTransition(label = "sync")
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f, targetValue = 360f,
                                animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
                                label = "rotation"
                            )
                            Icon(Icons.Default.Sync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { rotationZ = rotation })
                        } else if (failedCount > 0) {
                            Icon(
                                if (failedWithLocation) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                null,
                                tint = if (failedWithLocation) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(Icons.Default.CloudDone, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Premium Balance Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .graphicsLayer {
                        shadowElevation = 24f
                        shape = RoundedCornerShape(32.dp)
                        clip = true
                    }
                    .background(
                        Brush.linearGradient(
                            0.0f to Color(0xFF1A237E),
                            0.5f to Color(0xFF0D47A1),
                            1.0f to Color(0xFF01579B)
                        )
                    )
            ) {
                // Animated Abstract Pattern (Static for now but visual)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(0f, 0f),
                                radius = 600f
                            )
                        )
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "TOTAL EXPENSE",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "₹ ",
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Light,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = wholePart,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-1).sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = decimalPart.ifEmpty { ".00" },
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontSize = 24.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "MONTHLY BUDGET",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "₹ ${"%,.0f".format(remainingBudget)} left",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.TrendingUp,
                                null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent Activity",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black
                )
                TextButton(onClick = onSeeAllClick) {
                    Text("See All", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(transactions.take(10), key = { it.id }) { transaction ->
                    Box(modifier = Modifier.animateItem()) {
                        TransactionListItem(transaction, onClick = { onTransactionClick(transaction) })
                    }
                }
            }
        }

        // Floating Action Button for Manual Log
        LargeFloatingActionButton(
            onClick = { showManualLog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 140.dp, end = 24.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Transaction", modifier = Modifier.size(32.dp))
        }
    }
}
