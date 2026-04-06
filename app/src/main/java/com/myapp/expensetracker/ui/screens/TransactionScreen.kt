package com.myapp.expensetracker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.myapp.expensetracker.AppDatabase
import com.myapp.expensetracker.Transaction
import com.myapp.expensetracker.ui.components.TransactionListItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionScreen(onTransactionClick: (Transaction) -> Unit) {
    val context = LocalContext.current
    val transactions by AppDatabase.getDatabase(context).transactionDao().getAllTransactions().collectAsState(initial = emptyList())

    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Ledger History", 
            style = MaterialTheme.typography.headlineLarge, 
            color = MaterialTheme.colorScheme.onBackground, 
            fontWeight = FontWeight.Black
        )
        Text(
            "Tracking your financial journey.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        val grouped = remember(transactions) {
            transactions.groupBy { 
                val date = Date(it.date)
                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply { time = date }
                
                if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
                    "TODAY"
                } else if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR) == 1) {
                    "YESTERDAY"
                } else {
                    SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(date).uppercase()
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
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
                    Box(modifier = Modifier.animateItemPlacement()) {
                        TransactionListItem(transaction, onClick = { onTransactionClick(transaction) })
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}
