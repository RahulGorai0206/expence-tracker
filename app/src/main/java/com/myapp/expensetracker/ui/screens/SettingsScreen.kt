package com.myapp.expensetracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.expensetracker.AppDatabase
import com.myapp.expensetracker.GoogleSheetsLogger
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    isDarkTheme: Boolean, 
    onDarkThemeChange: (Boolean) -> Unit,
    followSystemTheme: Boolean,
    onFollowSystemThemeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val sharedPrefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    
    var budgetText by remember { mutableStateOf(sharedPrefs.getFloat("budget", 0f).let { if (it == 0f) "" else it.toString() }) }
    var isBudgetSaved by remember { mutableStateOf(sharedPrefs.contains("budget")) }
    
    var sheetUrl by remember { mutableStateOf(sharedPrefs.getString("sheet_url", "") ?: "") }
    var scriptUrl by remember { mutableStateOf(sharedPrefs.getString("script_url", "") ?: "") }
    var isCloudSaved by remember { mutableStateOf(sharedPrefs.contains("script_url")) }
    var isCloudExpanded by remember { mutableStateOf(false) }

    val extractedSheetId = remember(sheetUrl) {
        val pattern = "/spreadsheets/d/([a-zA-Z0-9-_]+)".toRegex()
        pattern.find(sheetUrl)?.groupValues?.get(1) ?: "YOUR_SHEET_ID_HERE"
    }

    val scriptCode = """
function doPost(e) {
  // Your Spreadsheet ID
  var ss = SpreadsheetApp.openById("$extractedSheetId");
  var sheet = ss.getSheets()[0];
  var amount = e.parameter.amount;

  // 1. Find the first empty cell in Column C
  var columnCVals = sheet.getRange("C:C").getValues();
  var targetRow = 1;
  
  for (var i = 0; i < columnCVals.length; i++) {
    // Check if the cell in Column C is empty
    if (columnCVals[i][0] === "" || columnCVals[i][0] === null) {
      targetRow = i + 1;
      break;
    }
    // If we reach the end of existing data, set target to next new row
    if (i === columnCVals.length - 1) {
      targetRow = columnCVals.length + 1;
    }
  }

  // 2. Insert data into that specific row
  sheet.getRange(targetRow, 3).setValue(amount);
  
  return ContentService.createTextOutput("Success").setMimeType(ContentService.MimeType.TEXT);
}
    """.trimIndent()

    if (showDeleteDialog) {
        val db = AppDatabase.getDatabase(context)
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Wipe All Data", fontWeight = FontWeight.Black) },
            text = { Text("This will permanently remove all transaction history. This action cannot be undone.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        scope.launch {
                            db.transactionDao().deleteAllTransactions()
                            showDeleteDialog = false
                        }
                    }
                ) {
                    Text("Clear All")
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

    Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Text(
            "Preferences", 
            style = MaterialTheme.typography.headlineLarge, 
            fontWeight = FontWeight.Black, 
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Customize your financial workspace.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("BUDGET PLANNING", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Monthly Target Budget", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Budget Amount (₹)") },
                    enabled = !isBudgetSaved,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (isBudgetSaved) {
                        TextButton(
                            onClick = { 
                                isBudgetSaved = false 
                                budgetText = ""
                                sharedPrefs.edit().remove("budget").apply()
                            }
                        ) {
                            Text("Reset", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(
                            onClick = {
                                val budget = budgetText.toFloatOrNull() ?: 0f
                                sharedPrefs.edit().putFloat("budget", budget).apply()
                                isBudgetSaved = true
                                Toast.makeText(context, "Budget saved", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("CLOUD SYNC (GOOGLE SHEETS)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column {
                // Header / Toggle Area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCloudExpanded = !isCloudExpanded }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudSync, "Sync", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Google Sheets Sync", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (isCloudSaved) "Connected & Synchronized" else "Configure cloud backup",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCloudSaved) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val rotation by animateFloatAsState(if (isCloudExpanded) 180f else 0f)
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        modifier = Modifier.graphicsLayer(rotationZ = rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = isCloudExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                        HorizontalDivider(modifier = Modifier.padding(bottom = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        
                        Text("1. Google Sheet URL", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedTextField(
                            value = sheetUrl,
                            onValueChange = { sheetUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCloudSaved,
                            placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("2. Apps Script Code", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    scriptCode,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Apps Script Code", scriptCode)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Copy Code")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("3. Instructions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "• Go to Extensions > Apps Script in your sheet.\n" +
                            "• Paste the code above and Save.\n" +
                            "• Click 'Deploy' > 'New Deployment'.\n" +
                            "• Select 'Web App'. Set 'Who has access' to 'Anyone'.\n" +
                            "• Copy the 'Web App URL' and paste below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("4. Apps Script Web App URL", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedTextField(
                            value = scriptUrl,
                            onValueChange = { scriptUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCloudSaved,
                            placeholder = { Text("https://script.google.com/macros/s/...") },
                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (isCloudSaved) {
                                TextButton(
                                    onClick = { 
                                        isCloudSaved = false 
                                        sheetUrl = ""
                                        scriptUrl = ""
                                        sharedPrefs.edit().remove("sheet_url").remove("script_url").apply()
                                        GoogleSheetsLogger.updateUrl("")
                                    }
                                ) {
                                    Text("Reset", color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (scriptUrl.isNotBlank()) {
                                            sharedPrefs.edit()
                                                .putString("sheet_url", sheetUrl)
                                                .putString("script_url", scriptUrl)
                                                .apply()
                                            GoogleSheetsLogger.updateUrl(scriptUrl)
                                            isCloudSaved = true
                                            isCloudExpanded = false // Auto-collapse on save
                                            Toast.makeText(context, "Cloud sync saved", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Please enter Web App URL", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Text("APPEARANCE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SettingsSuggest, "System", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Follow System", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Match device theme settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = followSystemTheme, onCheckedChange = onFollowSystemThemeChange)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.graphicsLayer(alpha = if (followSystemTheme) 0.5f else 1.0f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NightsStay, "Theme", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Premium Dark Mode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Deep blacks and soft accents", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = isDarkTheme, 
                        onCheckedChange = onDarkThemeChange,
                        enabled = !followSystemTheme
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("DATA MANAGEMENT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDeleteDialog = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DeleteForever, "Clear Data", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Clear All Transactions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("Reset your database to zero", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Expense Tracker v2.0 • Premium Edition",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}
