package com.myapp.expensetracker.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.myapp.expensetracker.AppDatabase
import com.myapp.expensetracker.Transaction
import com.myapp.expensetracker.ui.components.getCategoryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(initialTransaction: Transaction, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    
    // Observe the database for the specific transaction to ensure UI updates instantly
    val transaction by AppDatabase.getDatabase(context).transactionDao()
        .getTransactionById(initialTransaction.id)
        .collectAsState(initial = initialTransaction)

    // Handle case where transaction is null (e.g., just deleted)
    if (transaction == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val currentTransaction = transaction!!
    var showCategoryDialog by remember { mutableStateOf(false) }

    if (showCategoryDialog) {
        CategorySelectionDialog(
            currentCategory = currentTransaction.category,
            onDismiss = { showCategoryDialog = false },
            onCategorySelected = { newCategory ->
                scope.launch {
                    val updated = currentTransaction.copy(category = newCategory)
                    AppDatabase.getDatabase(context).transactionDao().insert(updated)
                    com.myapp.expensetracker.GoogleSheetsLogger.update(updated)
                }
                showCategoryDialog = false
            }
        )
    }

    fun captureAndShare() {
        scope.launch {
            val bitmap = withContext(Dispatchers.Main) {
                val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(b)
                view.draw(canvas)
                b
            }

            val uri = withContext(Dispatchers.IO) {
                val imagesDir = File(context.cacheDir, "shared_images")
                if (!imagesDir.exists()) imagesDir.mkdirs()
                val file = File(imagesDir, "transaction_${currentTransaction.id}.png")
                FileOutputStream(file).use { 
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) 
                }
                FileProvider.getUriForFile(context, "com.myapp.expensetracker.fileprovider", file)
            }

            val locationInfo = if (currentTransaction.latitude != null && currentTransaction.longitude != null) {
                "\nLocation: https://www.google.com/maps/search/?api=1&query=${currentTransaction.latitude},${currentTransaction.longitude}"
            } else ""

            val shareText = """
                Expense Tracker Transaction
                --------------------------
                Merchant: ${currentTransaction.sender}
                Amount: ₹${"%,.2f".format(currentTransaction.amount)}
                Date: ${SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(currentTransaction.date))}
                Category: ${currentTransaction.category}
                Status: ${currentTransaction.status}$locationInfo
                
                Message: ${currentTransaction.body}
            """.trimIndent()

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Transaction"))
        }
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
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        onClick = { captureAndShare() }
                    ) { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary) }
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
            val categoryInfo = getCategoryInfo(currentTransaction.category)
            val icon = categoryInfo.icon
            val color = categoryInfo.color

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(48.dp), tint = color)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                currentTransaction.sender.uppercase(), 
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            Text(
                "₹${"%,.2f".format(currentTransaction.amount)}",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp),
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            Row {
                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(currentTransaction.category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(currentTransaction.status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            DetailCard("TRANSACTION DATE", SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(currentTransaction.date)), Icons.Default.CalendarMonth)
            
            val sourceLabel = if (currentTransaction.type == "manual") "LOGGED BY USER" else "MERCHANT SOURCE"
            val sourceValue = currentTransaction.sender
            val sourceSub = if (currentTransaction.type == "manual") "Manual entry via Dashboard" else "Identified from incoming SMS"
            
            DetailCard(sourceLabel, sourceValue, null, sourceSub)
            
            if (currentTransaction.type == "manual") {
                DetailCard("NOTES / BODY", currentTransaction.body, Icons.Default.Description)
            } else {
                DetailCard("ORIGINAL MESSAGE", "\"${currentTransaction.body}\"", null, null, false)
            }
            
            if (currentTransaction.latitude != null && currentTransaction.longitude != null) {
                DetailCard(
                    label = if (currentTransaction.type == "manual") "LOCATION LOGGED" else "LOCATION CAPTURED",
                    value = "${"%.4f".format(currentTransaction.latitude)}, ${"%.4f".format(currentTransaction.longitude)}",
                    icon = Icons.Default.LocationOn,
                    subValue = if (currentTransaction.type == "manual") "User location at time of logging" else "Precise coordinates at time of SMS"
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        try {
                            val uri = "geo:${currentTransaction.latitude},${currentTransaction.longitude}?q=${currentTransaction.latitude},${currentTransaction.longitude}(Transaction Location)"
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri.toUri())
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            // Fallback to browser if no map app
                            val webUri = "https://www.google.com/maps/search/?api=1&query=${currentTransaction.latitude},${currentTransaction.longitude}"
                            val browserIntent = Intent(Intent.ACTION_VIEW, webUri.toUri())
                            context.startActivity(browserIntent)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Map, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("View on Google Maps", fontWeight = FontWeight.Bold)
                }
            } else if (currentTransaction.type == "automated") {
                DetailCard(
                    label = "LOCATION",
                    value = "Not captured",
                    icon = Icons.Default.LocationOff,
                    subValue = "Enable 'Allow all the time' location permission in Settings."
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showCategoryDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
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
                            val toDelete = currentTransaction
                            AppDatabase.getDatabase(context).transactionDao().delete(toDelete)
                            com.myapp.expensetracker.GoogleSheetsLogger.delete(toDelete)
                            com.myapp.expensetracker.updateExpenseWidget(context)
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
                        Box(modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary))
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
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
