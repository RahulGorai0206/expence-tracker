package com.myapp.expensetracker.ui.components

import android.annotation.SuppressLint
import android.location.Location
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.myapp.expensetracker.AppDatabase
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

    val categories = listOf("Dining", "Shopping", "Transport", "Groceries", "Bills", "Other")

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Manual Log",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) amount = it },
                label = { Text("Amount (₹)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                leadingIcon = { Icon(Icons.Default.CurrencyRupee, null) }
            )

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant / Description") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Store, null) }
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Source Message / Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) }
            )

            Text("Category", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(categories.size) { index ->
                    val cat = categories[index]
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) },
                        leadingIcon = if (category == cat) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        isCapturingLocation = true
                        try {
                            location = fusedLocationClient.lastLocation.await()
                        } catch (e: Exception) {
                            // Handle error
                        } finally {
                            isCapturingLocation = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (location != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (location != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                if (isCapturingLocation) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(if (location != null) Icons.Default.LocationOn else Icons.Default.MyLocation, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (location != null) "Location Captured" else "Tap to Fetch Location")
                }
            }

            Button(
                onClick = {
                    if (amount.isNotEmpty() && merchant.isNotEmpty()) {
                        scope.launch {
                            val transaction = Transaction(
                                amount = -amount.toDouble(),
                                sender = merchant,
                                body = body,
                                date = System.currentTimeMillis(),
                                category = category,
                                type = "manual",
                                latitude = location?.latitude,
                                longitude = location?.longitude
                            )
                            AppDatabase.getDatabase(context).transactionDao().insert(transaction)
                            Toast.makeText(context, "Transaction saved successfully", Toast.LENGTH_SHORT).show()
                            
                            // Log to sheets in background without blocking the UI dismiss
                            scope.launch {
                                try {
                                    com.myapp.expensetracker.GoogleSheetsLogger.log(transaction)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            sheetState.hide()
                            onTransactionSaved()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = amount.isNotEmpty() && merchant.isNotEmpty()
            ) {
                Text("Save Transaction", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun ScrollableRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement
    ) {
        item { content() }
    }
}
