package com.myapp.expensetracker.ui.components

import android.annotation.SuppressLint
import android.location.Location
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import com.myapp.expensetracker.AppDatabase
import com.myapp.expensetracker.CloudSettingsBackupManager
import com.myapp.expensetracker.Transaction
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTransactionBottomSheet(
    onDismiss: () -> Unit,
    onTransactionSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Other") }
    var body by remember { mutableStateOf("") }
    var location by remember { mutableStateOf<Location?>(null) }
    var isCapturingLocation by remember { mutableStateOf(false) }
    var isDebit by remember { mutableStateOf(true) }
    var selectedTag by remember { mutableStateOf("") }

    val categories = listOf("Dining", "Shopping", "Transport", "Groceries", "Bills", "Other")
    val savedTags = remember { CloudSettingsBackupManager.getSavedTags(context) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        dragHandle = {
            // Custom drag handle
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        "Manual Log",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Add a transaction manually",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ── Debit / Credit toggle (pill style)
            val debitBg by animateColorAsState(
                if (isDebit) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = tween(200), label = "debitBg"
            )
            val creditBg by animateColorAsState(
                if (!isDebit) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = tween(200), label = "creditBg"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    ),
                horizontalArrangement = Arrangement.Center
            ) {
                // Debit
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .background(debitBg)
                        .then(if (!isDebit) Modifier else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = { isDebit = true },
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                null,
                                tint = if (isDebit) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.5f
                                ),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Debit",
                                fontWeight = if (isDebit) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (isDebit) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.5f
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                // Credit
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                        .background(creditBg),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = { isDebit = false },
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                null,
                                tint = if (!isDebit) Color(0xFF388E3C) else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.5f
                                ),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Credit",
                                fontWeight = if (!isDebit) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (!isDebit) Color(0xFF388E3C) else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.5f
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // ── Amount field (large)
            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) amount = it },
                label = { Text("Amount (\u20B9)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                leadingIcon = { Icon(Icons.Default.CurrencyRupee, null) },
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            )

            // ── Merchant field
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant / Description") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Store, null) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            )

            // ── Notes
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            )

            // ── Category chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "CATEGORY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(categories) { cat ->
                        val catInfo = getCategoryInfo(cat)
                        val isSelected = category == cat
                        val chipBg by animateColorAsState(
                            if (isSelected) catInfo.color.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = tween(180), label = "catBg$cat"
                        )
                        val chipBorder by animateColorAsState(
                            if (isSelected) catInfo.color.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            animationSpec = tween(180), label = "catBorder$cat"
                        )
                        Surface(
                            onClick = { category = cat },
                            shape = RoundedCornerShape(100.dp),
                            color = chipBg,
                            modifier = Modifier.border(1.dp, chipBorder, RoundedCornerShape(100.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        catInfo.icon,
                                        null,
                                        tint = catInfo.color,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    cat,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) catInfo.color else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Tags
            if (savedTags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "TAG",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        item {
                            val isSelected = selectedTag.isBlank()
                            Surface(
                                onClick = { selectedTag = "" },
                                shape = RoundedCornerShape(100.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = 0.3f
                                    ),
                                    RoundedCornerShape(100.dp)
                                )
                            ) {
                                Text(
                                    "No tag",
                                    modifier = Modifier.padding(
                                        horizontal = 14.dp,
                                        vertical = 8.dp
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(savedTags) { tag ->
                            val isSelected = selectedTag == tag
                            Surface(
                                onClick = { selectedTag = tag },
                                shape = RoundedCornerShape(100.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(
                                    alpha = 0.4f
                                ) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = 0.3f
                                    ),
                                    RoundedCornerShape(100.dp)
                                )
                            ) {
                                Text(
                                    tag,
                                    modifier = Modifier.padding(
                                        horizontal = 14.dp,
                                        vertical = 8.dp
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Location button
            val locationBg =
                if (location != null) Color(0xFF4CAF50).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh
            val locationBorder =
                if (location != null) Color(0xFF4CAF50).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = 0.4f
                )
            Surface(
                onClick = {
                    scope.launch {
                        isCapturingLocation = true
                        try {
                            location = fusedLocationClient.lastLocation.await()
                        } catch (_: Exception) {
                        } finally {
                            isCapturingLocation = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(1.dp, locationBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = locationBg
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (isCapturingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (location != null) Icons.Default.LocationOn else Icons.Default.MyLocation,
                                null,
                                tint = if (location != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (location != null) "Location Captured" else "Tap to Fetch Location",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (location != null) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (location != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Save button
            val canSave = amount.isNotEmpty() && merchant.isNotEmpty()
            Button(
                onClick = {
                    if (canSave) {
                        scope.launch {
                            val doubleAmount = amount.toDouble()
                            val transaction = Transaction(
                                amount = if (isDebit) -doubleAmount else doubleAmount,
                                sender = merchant,
                                body = body,
                                date = System.currentTimeMillis(),
                                category = category,
                                tag = selectedTag.trim(),
                                type = "manual",
                                latitude = location?.latitude,
                                longitude = location?.longitude,
                                syncStatus = "pending"
                            )
                            val db = AppDatabase.getDatabase(context)
                            val localId = db.transactionDao().insertAndReturnId(transaction)
                            sheetState.hide()
                            onTransactionSaved()
                            Toast.makeText(context, "Transaction saved", Toast.LENGTH_SHORT).show()
                            com.myapp.expensetracker.enqueueWidgetUpdate(context)
                            com.myapp.expensetracker.GoogleSheetsLogger.logAsync(context, transaction, localId)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Save Transaction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
