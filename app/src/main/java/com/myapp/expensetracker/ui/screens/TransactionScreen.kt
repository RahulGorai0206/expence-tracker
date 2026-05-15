package com.myapp.expensetracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.expensetracker.Transaction
import com.myapp.expensetracker.ui.components.EmptyState
import com.myapp.expensetracker.ui.components.TransactionListItem
import com.myapp.expensetracker.viewmodel.TransactionViewModel
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionScreen(onTransactionClick: (Transaction) -> Unit) {
    val context = LocalContext.current
    val viewModel: TransactionViewModel = koinViewModel()
    val transactions by viewModel.transactions.collectAsState()
    val deletedSyncTransactions by viewModel.deletedSyncTransactions.collectAsState()

    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    val selectedTransactions = remember(transactions, selectedIds) {
        transactions.filter { it.id in selectedIds }
    }

    fun toggleSelection(transaction: Transaction) {
        selectedIds = if (transaction.id in selectedIds) {
            selectedIds - transaction.id
        } else {
            selectedIds + transaction.id
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text("Delete ${selectedIds.size} transaction${if (selectedIds.size == 1) "" else "s"}?")
            },
            text = {
                Text("They will disappear from History now. If cloud sync is enabled, deletion will retry in the background until it succeeds.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = selectedTransactions
                        viewModel.softDelete(context, toDelete) {
                            Toast.makeText(
                                context,
                                "${toDelete.size} transaction${if (toDelete.size == 1) "" else "s"} deleted",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        selectedIds = emptySet()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (selectionMode) "${selectedIds.size} selected" else "Ledger History",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black
                )
                Text(
                    if (selectionMode) "Tap more transactions to add them." else "Tracking your financial journey.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selectionMode) {
                IconButton(onClick = { selectedIds = emptySet() }) {
                    Icon(Icons.Default.Close, contentDescription = "Exit selection")
                }
                IconButton(
                    onClick = { showDeleteDialog = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (transactions.isEmpty() && deletedSyncTransactions.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "No Transactions Yet",
                description = "Your entire spending history will be listed here. Start by adding a transaction manually or letting the app track your SMS.",
                modifier = Modifier.weight(1f)
            )
        } else {
            val grouped = remember(transactions) {
                transactions.groupBy {
                    val date = Date(it.date)
                    val now = Calendar.getInstance()
                    val target = Calendar.getInstance().apply { time = date }

                    if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
                    ) {
                        "TODAY"
                    } else if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR) == 1
                    ) {
                        "YESTERDAY"
                    } else {
                        SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(date)
                            .uppercase()
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (deletedSyncTransactions.isNotEmpty()) {
                    item {
                        DeletedSyncStatusCard(
                            transactions = deletedSyncTransactions,
                            onRetry = {
                                viewModel.retryDeleteSync(context, deletedSyncTransactions)
                                Toast.makeText(
                                    context,
                                    "Retrying delete sync...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }

                grouped.forEach { (date, items) ->
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                date,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                    items(items, key = { it.id }) { transaction ->
                        Box(modifier = Modifier.animateItem()) {
                            TransactionListItem(
                                transaction = transaction,
                                onClick = {
                                    if (selectionMode) {
                                        toggleSelection(transaction)
                                    } else {
                                        onTransactionClick(transaction)
                                    }
                                },
                                selected = transaction.id in selectedIds,
                                selectionMode = selectionMode,
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectedIds = setOf(transaction.id)
                                    }
                                }
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }
}

@Composable
private fun DeletedSyncStatusCard(
    transactions: List<Transaction>,
    onRetry: () -> Unit
) {
    val failedCount = transactions.count { it.syncStatus == "failed" }
    val pendingCount = transactions.count { it.syncStatus == "pending" }
    val isFailed = failedCount > 0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (isFailed) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        },
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isFailed) Icons.Default.CloudOff else Icons.Default.Sync,
                contentDescription = null,
                tint = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isFailed) "Cloud delete needs retry" else "Cloud delete pending",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    when {
                        failedCount > 0 && pendingCount > 0 -> "$failedCount failed, $pendingCount waiting"
                        failedCount > 0 -> "$failedCount transaction${if (failedCount == 1) "" else "s"} hidden locally but not deleted from cloud yet"
                        else -> "$pendingCount transaction${if (pendingCount == 1) "" else "s"} waiting for cloud confirmation"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isFailed) {
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}
