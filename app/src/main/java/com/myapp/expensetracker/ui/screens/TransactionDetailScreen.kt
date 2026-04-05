package com.myapp.expensetracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.myapp.expensetracker.AppDatabase
import com.myapp.expensetracker.Transaction
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(transaction: Transaction, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCategoryDialog by remember { mutableStateOf(false) }

    if (showCategoryDialog) {
        CategorySelectionDialog(
            currentCategory = transaction.category,
            onDismiss = { showCategoryDialog = false },
            onCategorySelected = { newCategory ->
                scope.launch {
                    AppDatabase.getDatabase(context).transactionDao().insert(transaction.copy(category = newCategory))
                }
                showCategoryDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) 
                    }
                },
                actions = {
                    IconButton(
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        onClick = {
                        val locationInfo = if (transaction.latitude != null && transaction.longitude != null) {
                            "\nLocation: https://www.google.com/maps/search/?api=1&query=${transaction.latitude},${transaction.longitude}"
                        } else ""

                        val shareText = """
                            Expense Tracker Transaction
                            --------------------------
                            Merchant: ${transaction.sender}
                            Amount: ₹${"%,.2f".format(transaction.amount)}
                            Date: ${SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(transaction.date))}
                            Category: ${transaction.category}
                            Status: ${transaction.status}$locationInfo
                            
                            Message: ${transaction.body}
                        """.trimIndent()
                        
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Transaction"))
                    }) { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary) }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val (icon, color) = when(transaction.category) {
                "Groceries" -> Icons.Default.ShoppingBag to MaterialTheme.colorScheme.secondaryContainer
                "Transport" -> Icons.Default.DirectionsCar to MaterialTheme.colorScheme.tertiaryContainer
                "Dining" -> Icons.Default.Restaurant to MaterialTheme.colorScheme.primaryContainer
                else -> Icons.Default.Payments to MaterialTheme.colorScheme.surfaceVariant
            }

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                transaction.sender.uppercase(), 
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            Text(
                "₹${"%,.2f".format(transaction.amount)}",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp),
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            Row {
                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(transaction.category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(transaction.status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            DetailCard("TRANSACTION DATE", SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(transaction.date)), Icons.Default.CalendarMonth)
            DetailCard("MERCHANT SOURCE", transaction.sender, null, "Identified from incoming SMS")
            
            if (transaction.latitude != null && transaction.longitude != null) {
                DetailCard(
                    label = "LOCATION CAPTURED",
                    value = "${"%.4f".format(transaction.latitude)}, ${"%.4f".format(transaction.longitude)}",
                    icon = Icons.Default.LocationOn,
                    subValue = "Precise coordinates at time of SMS"
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        val uri = "geo:${transaction.latitude},${transaction.longitude}?q=${transaction.latitude},${transaction.longitude}(Transaction Location)"
                        val mapIntent = Intent(Intent.ACTION_VIEW, uri.toUri())
                        mapIntent.setPackage("com.google.android.apps.maps")
                        context.startActivity(mapIntent)
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Map, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("View on Google Maps", fontWeight = FontWeight.Bold)
                }
            }

            DetailCard("ORIGINAL MESSAGE", "\"${transaction.body}\"", null, null, false)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showCategoryDialog = true },
                    modifier = Modifier.weight(1f).height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-categorize")
                }
                
                IconButton(
                    onClick = {
                        scope.launch {
                            AppDatabase.getDatabase(context).transactionDao().delete(transaction)
                            onBack()
                        }
                    },
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DetailCard(label: String, value: String, icon: ImageVector? = null, subValue: String? = null, isCategory: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (subValue != null) Text(subValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                if (isCategory) {
                    Row {
                        Box(modifier = Modifier.width(32.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySelectionDialog(
    currentCategory: String,
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit
) {
    val categories = listOf("Dining", "Shopping", "Transport", "Groceries", "Bills", "Other")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Category", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySelected(category) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (category == currentCategory),
                            onClick = { onCategorySelected(category) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = category, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
