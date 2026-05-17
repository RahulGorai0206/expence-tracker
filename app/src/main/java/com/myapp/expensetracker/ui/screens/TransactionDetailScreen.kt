package com.myapp.expensetracker.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.myapp.expensetracker.AppDatabase
import com.myapp.expensetracker.CloudSettingsBackupManager
import com.myapp.expensetracker.Transaction
import com.myapp.expensetracker.ui.components.getCategoryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class ShareOptions(
    val shareScreenshot: Boolean = true,
    val shareMerchant: Boolean = true,
    val shareDateTime: Boolean = true,
    val shareLocation: Boolean = true,
    val shareMessage: Boolean = true
)

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
    var showEditDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditTransactionDialog(
            transaction = currentTransaction,
            onDismiss = { showEditDialog = false },
            onSave = { updatedTransaction ->
                scope.launch {
                    val toUpdate = updatedTransaction.copy(syncStatus = "pending")
                    AppDatabase.getDatabase(context).transactionDao().insert(toUpdate)
                    com.myapp.expensetracker.enqueueWidgetUpdate(context)
                    com.myapp.expensetracker.GoogleSheetsLogger.logAsync(
                        context,
                        toUpdate,
                        toUpdate.id.toLong()
                    )
                }
                showEditDialog = false
            }
        )
    }

    fun captureAndShare(options: ShareOptions) {
        scope.launch {
            val uri = if (options.shareScreenshot) {
                val bitmap = withContext(Dispatchers.Main) {
                    val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(b)
                    view.draw(canvas)
                    b
                }

                withContext(Dispatchers.IO) {
                    val imagesDir = File(context.cacheDir, "shared_images")
                    if (!imagesDir.exists()) imagesDir.mkdirs()
                    val file = File(imagesDir, "transaction_${currentTransaction.id}.png")
                    FileOutputStream(file).use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    FileProvider.getUriForFile(
                        context,
                        "com.myapp.expensetracker.fileprovider",
                        file
                    )
                }
            } else null

            val locationInfo =
                if (options.shareLocation && currentTransaction.latitude != null && currentTransaction.longitude != null) {
                "\n|📍 *Location:* https://www.google.com/maps/search/?api=1&query=${currentTransaction.latitude},${currentTransaction.longitude}"
            } else ""

            val merchantInfo = if (options.shareMerchant) {
                "\n|🏢 *Merchant:* ${currentTransaction.sender}"
            } else ""

            val dateTimeInfo = if (options.shareDateTime) {
                val dateStr =
                    SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.getDefault()).format(
                        Date(currentTransaction.date)
                    )
                "\n|📅 *Date & Time:* $dateStr"
            } else ""

            val messageInfo = if (options.shareMessage) {
                "\n|\n|💬 *Message:*\n|${currentTransaction.body}"
            } else ""

            val shareText = """
                |💸 *Transaction Receipt*
                |━━━━━━━━━━━━━━━━━━$merchantInfo
                |💰 *Total Amount:* \u20B9${"%,.2f".format(abs(currentTransaction.amount))}$dateTimeInfo
                |━━━━━━━━━━━━━━━━━━$locationInfo$messageInfo
                |
                |_Shared via Expense Tracker_
            """.trimMargin()

            val intent = Intent(Intent.ACTION_SEND).apply {
                if (uri != null) {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                } else {
                    type = "text/plain"
                }
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Transaction"))
        }
    }

    if (showShareDialog) {
        ShareOptionsDialog(
            onDismiss = { showShareDialog = false },
            onShare = { options ->
                showShareDialog = false
                captureAndShare(options)
            },
            hasLocation = currentTransaction.latitude != null && currentTransaction.longitude != null
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars,
        containerColor = MaterialTheme.colorScheme.background,
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
                        onClick = { showShareDialog = true }
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
                "\u20B9${"%,.2f".format(currentTransaction.amount)}",
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

            if (currentTransaction.tag.isNotBlank()) {
                DetailCard("TAG", currentTransaction.tag, Icons.AutoMirrored.Filled.Label)
            }

            val sourceLabel = when (currentTransaction.type) {
                "manual" -> "LOGGED BY USER"
                "AI" -> "AI ANALYZED SOURCE"
                else -> "MERCHANT SOURCE"
            }
            val sourceValue = currentTransaction.sender
            val sourceSub = when (currentTransaction.type) {
                "manual" -> "Manual entry via Dashboard"
                "AI" -> "Historical SMS analyzed by On-Device AI"
                else -> "Identified from incoming SMS"
            }
            
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
                    onClick = { showEditDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Transaction")
                }
                
                IconButton(
                    onClick = {
                        scope.launch {
                            val toDelete = currentTransaction
                            // 1. Mark as deleted locally (Instant UI update)
                            AppDatabase.getDatabase(context).transactionDao()
                                .softDelete(toDelete.id)
                            com.myapp.expensetracker.enqueueWidgetUpdate(context)

                            // 2. Close window immediately
                            onBack()

                            // 3. Trigger cloud sync in background (doesn't block UI)
                            val deletedTransaction = toDelete.copy(
                                status = "deleted",
                                syncStatus = "pending"
                            )
                            if (com.myapp.expensetracker.GoogleSheetsLogger.isConfigured()) {
                                com.myapp.expensetracker.GoogleSheetsLogger.logAsync(
                                    context,
                                    deletedTransaction,
                                    deletedTransaction.id.toLong()
                                )
                            } else {
                                AppDatabase.getDatabase(context).transactionDao().updateSyncStatus(
                                    deletedTransaction.id,
                                    deletedTransaction.remoteId,
                                    "synced"
                                )
                            }
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
            .padding(vertical = 6.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (subValue != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            subValue,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (icon != null) Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    val isManual = transaction.type == "manual"
    var amountText by remember { mutableStateOf(abs(transaction.amount).toString()) }
    var senderText by remember { mutableStateOf(transaction.sender) }
    var bodyText by remember { mutableStateOf(transaction.body) }
    var category by remember { mutableStateOf(transaction.category) }
    var tagText by remember { mutableStateOf(transaction.tag) }
    var isDebit by remember { mutableStateOf(transaction.amount < 0) }
    val context = LocalContext.current
    val categories = listOf("Dining", "Shopping", "Transport", "Groceries", "Bills", "Other")
    val savedTags = remember { CloudSettingsBackupManager.getSavedTags(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Edit Transaction",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                if (isManual) {
                    val debitBg by animateColorAsState(
                        if (isDebit) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceContainerHighest,
                        tween(200),
                        label = ""
                    )
                    val creditBg by animateColorAsState(
                        if (!isDebit) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceContainerHighest,
                        tween(200),
                        label = ""
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(debitBg)
                                .clickable { isDebit = true }, contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    null,
                                    tint = if (isDebit) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        0.4f
                                    ),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Debit",
                                    fontWeight = if (isDebit) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (isDebit) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        0.4f
                                    ),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(creditBg)
                                .clickable { isDebit = false }, contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ArrowDownward,
                                    null,
                                    tint = if (!isDebit) Color(0xFF388E3C) else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        0.4f
                                    ),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Credit",
                                    fontWeight = if (!isDebit) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (!isDebit) Color(0xFF388E3C) else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        0.4f
                                    ),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "Source: ${transaction.sender}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        if (it.isEmpty() || it.toDoubleOrNull() != null) amountText = it
                    },
                    label = { Text("Amount (\u20b9)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Icon(Icons.Default.CurrencyRupee, null) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(0.6f)
                    )
                )

                if (isManual) {
                    OutlinedTextField(
                        value = senderText,
                        onValueChange = { senderText = it },
                        label = { Text("Merchant / Description") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Store, null) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(
                                0.6f
                            )
                        )
                    )
                    OutlinedTextField(
                        value = bodyText,
                        onValueChange = { bodyText = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(
                                0.6f
                            )
                        )
                    )
                }

                Text(
                    "CATEGORY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f),
                    letterSpacing = 0.8.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        val catInfo = getCategoryInfo(cat)
                        val isSel = category == cat
                        val chipBg by animateColorAsState(
                            if (isSel) catInfo.color.copy(0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                            tween(180),
                            label = ""
                        )
                        Surface(
                            onClick = { category = cat },
                            shape = RoundedCornerShape(100.dp),
                            color = chipBg,
                            modifier = Modifier.border(
                                1.dp,
                                if (isSel) catInfo.color.copy(0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(
                                    0.35f
                                ),
                                RoundedCornerShape(100.dp)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isSel) Icon(
                                    catInfo.icon,
                                    null,
                                    tint = catInfo.color,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    cat,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSel) catInfo.color else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    label = { Text("Tag (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                    trailingIcon = if (tagText.isNotBlank()) {
                        {
                            IconButton(onClick = { tagText = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    null
                                )
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(0.6f)
                    )
                )

                if (savedTags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            val isSel = tagText.isBlank()
                            Surface(
                                onClick = { tagText = "" }, shape = RoundedCornerShape(100.dp),
                                color = if (isSel) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.border(
                                    1.dp,
                                    if (isSel) MaterialTheme.colorScheme.outline.copy(0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(
                                        0.3f
                                    ),
                                    RoundedCornerShape(100.dp)
                                )
                            ) {
                                Text(
                                    "No tag",
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 7.dp
                                    ),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        items(savedTags) { tag ->
                            val isSel = tagText == tag
                            Surface(
                                onClick = { tagText = tag }, shape = RoundedCornerShape(100.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(
                                    0.4f
                                ) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.border(
                                    1.dp,
                                    if (isSel) MaterialTheme.colorScheme.primary.copy(0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(
                                        0.3f
                                    ),
                                    RoundedCornerShape(100.dp)
                                )
                            ) {
                                Text(
                                    tag,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 7.dp
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val newAmount = amountText.toDoubleOrNull() ?: 0.0
                            val updated = transaction.copy(
                                amount = if (isDebit) -abs(newAmount) else abs(newAmount),
                                sender = if (isManual) senderText else transaction.sender,
                                body = if (isManual) bodyText else transaction.body,
                                category = category, tag = tagText.trim()
                            )
                            onSave(updated)
                        },
                        enabled = amountText.isNotEmpty() && (if (isManual) senderText.isNotEmpty() else true),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun ShareOptionsDialog(
    onDismiss: () -> Unit,
    onShare: (ShareOptions) -> Unit,
    hasLocation: Boolean
) {
    var options by remember { mutableStateOf(ShareOptions()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Share Options",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Select what you'd like to include:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                ShareOptionItem("Screenshot", options.shareScreenshot) {
                    options = options.copy(shareScreenshot = it)
                }
                ShareOptionItem("Merchant", options.shareMerchant) {
                    options = options.copy(shareMerchant = it)
                }
                ShareOptionItem("Date & Time", options.shareDateTime) {
                    options = options.copy(shareDateTime = it)
                }
                if (hasLocation) {
                    ShareOptionItem("Location", options.shareLocation) {
                        options = options.copy(shareLocation = it)
                    }
                }
                ShareOptionItem("Message", options.shareMessage) {
                    options = options.copy(shareMessage = it)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onShare(options) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
fun ShareOptionItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
