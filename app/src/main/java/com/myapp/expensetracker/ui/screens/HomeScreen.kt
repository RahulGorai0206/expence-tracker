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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import com.myapp.expensetracker.CloudSettingsBackupManager
import com.myapp.expensetracker.viewmodel.HomeViewModel
import org.koin.androidx.compose.koinViewModel
import com.myapp.expensetracker.Transaction
import com.myapp.expensetracker.ui.components.BudgetEditSheet
import com.myapp.expensetracker.ui.components.EmptyState
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
    val budget by viewModel.currentBudget.collectAsState()
    val smartSuggestions by viewModel.smartSuggestions.collectAsState()
    var showManualLog by remember { mutableStateOf(false) }
    var showBudgetEdit by remember { mutableStateOf(false) }

    if (showManualLog) {
        ManualTransactionBottomSheet(
            onDismiss = { showManualLog = false },
            onTransactionSaved = { showManualLog = false }
        )
    }

    if (showBudgetEdit) {
        BudgetEditSheet(
            currentBudget = budget,
            smartSuggestions = smartSuggestions,
            onSave = { amount ->
                viewModel.saveBudget(amount)
                CloudSettingsBackupManager.backupAsync(context)
            },
            onDismiss = { showBudgetEdit = false }
        )
    }

    val sharedPrefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    val isMonthlyBudget =
        remember { mutableStateOf(sharedPrefs.getBoolean("budget_monthly", true)) }

    val totalSpent = remember(transactions, isMonthlyBudget.value) {
        val now = java.util.Calendar.getInstance()
        val currentMonth = now.get(java.util.Calendar.MONTH)
        val currentYear = now.get(java.util.Calendar.YEAR)

        transactions.filter { tx ->
            val inMonth = if (isMonthlyBudget.value) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = tx.date }
                cal.get(java.util.Calendar.MONTH) == currentMonth && cal.get(java.util.Calendar.YEAR) == currentYear
            } else true
            tx.amount < 0 && inMonth
        }.sumOf { abs(it.amount) }
    }

    val remainingBudget = budget - totalSpent
    val budgetProgress = if (budget > 0) (totalSpent / budget).toFloat().coerceIn(0f, 1.5f) else 0f
    val budgetStatusColor = when {
        budget <= 0 -> Color.White
        budgetProgress < 0.5f -> Color(0xFF4CAF50)
        budgetProgress < 0.75f -> Color(0xFFFFA726)
        budgetProgress <= 1.0f -> Color(0xFFFF5722)
        else -> Color(0xFFFF1744)
    }

    val wholePart = remember(totalSpent) { totalSpent.toInt().toString() }
    val decimalPart =
        remember(totalSpent) { "%.2f".format(totalSpent % 1).removePrefix("0").removePrefix("-0") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // ── Header Row ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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

                val sheetUrl2 = sharedPrefs.getString("sheet_url", "") ?: ""
                val scriptUrl2 = sharedPrefs.getString("script_url", "") ?: ""
                val isSyncEnabled = sheetUrl2.isNotBlank() && scriptUrl2.isNotBlank()

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
                            scope.launch {
                                Toast.makeText(context, "Resyncing ${failedCount + pendingCount} transactions...", Toast.LENGTH_SHORT).show()
                                transactions.filter { it.syncStatus == "failed" || it.syncStatus == "pending" }
                                    .forEach {
                                    GoogleSheetsLogger.logAsync(context, it, it.id.toLong())
                                }
                            }
                        } else {
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
                            Icon(
                                Icons.Default.Sync, null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
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

            Spacer(modifier = Modifier.height(16.dp))

            // ── Balance Card ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .graphicsLayer {
                        shadowElevation = 24f
                        shape = RoundedCornerShape(28.dp)
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
                        .padding(24.dp),
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "\u20B9 ",
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
                                    fontSize = 22.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
                            )
                        }
                    }

                    Column {
                        // Budget progress bar
                        if (budget > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(budgetProgress.coerceAtMost(1f))
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    budgetStatusColor.copy(alpha = 0.7f),
                                                    budgetStatusColor
                                                )
                                            )
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showBudgetEdit = true }
                                .padding(vertical = 2.dp),
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
                                if (budget <= 0) {
                                    Text(
                                        "Tap to set budget",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                } else if (remainingBudget >= 0) {
                                    Text(
                                        "\u20B9 ${"%,.0f".format(remainingBudget)} left",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = budgetStatusColor,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                } else {
                                    Text(
                                        "\u20B9 ${"%,.0f".format(abs(remainingBudget))} over!",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = budgetStatusColor,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Recent Activity ───────────────────────────────────────
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

            Spacer(modifier = Modifier.height(8.dp))

            if (transactions.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = "No Recent Activity",
                    description = "Your recent transactions will appear here as you spend or log them manually.",
                    modifier = Modifier.padding(top = 40.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(transactions.take(10), key = { it.id }) { transaction ->
                        Box(modifier = Modifier.animateItem()) {
                            TransactionListItem(
                                transaction,
                                onClick = { onTransactionClick(transaction) })
                        }
                    }
                }
            }
        }

        // ── FAB ───────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { showManualLog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 128.dp, end = 22.dp),
            containerColor = Color(0xFF2979FF),
            contentColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 14.dp
            )
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Transaction",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
