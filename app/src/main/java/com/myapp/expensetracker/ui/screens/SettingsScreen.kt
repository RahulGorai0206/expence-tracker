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
    var apiKey by remember { mutableStateOf(sharedPrefs.getString("api_key", "") ?: "") }
    var isCloudSaved by remember { mutableStateOf(sharedPrefs.contains("script_url")) }
    var isCloudExpanded by remember { mutableStateOf(false) }

    val extractedSheetId = remember(sheetUrl) {
        val pattern = "/spreadsheets/d/([a-zA-Z0-9-_]+)".toRegex()
        pattern.find(sheetUrl)?.groupValues?.get(1) ?: "YOUR_SHEET_ID_HERE"
    }

    val scriptCode = """
// ============================================================
//  CONFIGURATION
// ============================================================
var SPREADSHEET_ID = "$extractedSheetId";
var API_KEY = PropertiesService.getScriptProperties().getProperty('API_KEY');
var DB_SHEET_NAME  = "database";
var LOG_SHEET_NAME = "logs";
var LEGACY_SHEET_INDEX = 0; 

var COL = {
  ID: 1, DATE: 2, AMOUNT: 3, SENDER: 4, CATEGORY: 5, STATUS: 6,
  TYPE: 7, BODY: 8, LATITUDE: 9, LONGITUDE: 10, CREATED_AT: 11, UPDATED_AT: 12
};

var HEADERS = ["id","date","amount","sender","category","status","type","body","latitude","longitude","created_at","updated_at"];

// ============================================================
//  AUTH HELPER
// ============================================================
function isAuthorized(params) {
  if (!API_KEY) {
    logError("Auth", "CRITICAL: API_KEY Script Property is not set. All requests blocked.");
    return false;
  }
  if (!params.api_key || params.api_key !== API_KEY) {
    logInfo("Auth_FAIL", "Unauthorized attempt. Key prefix: " + String(params.api_key || "none").slice(0, 4) + "...");
    return false;
  }
  return true;
}

// --- ENHANCED LOGGING ENGINE ---
function logInfo(tag, msg) { writeLog("INFO", tag, msg); }
function logError(tag, err) { writeLog("ERROR", tag, err.toString()); }

function writeLog(lvl, tag, msg) {
  try {
    // 1. Log to Apps Script Execution Console (Internal)
    console.log("[" + lvl + "] " + tag + ": " + msg);
    
    // 2. Log to Spreadsheet Tab (External/User-facing)
    var ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    var s = ss.getSheetByName(LOG_SHEET_NAME) || ss.insertSheet(LOG_SHEET_NAME);
    
    // Keep only last 1000 logs to prevent bloat
    if (s.getLastRow() > 1000) { s.deleteRows(2, 500); }
    
    s.appendRow([new Date(), lvl, tag, msg]);
  } catch(e) { 
    console.error("Logging failed: " + e.toString()); 
  }
}

function doGet(e) {
  logInfo("doGet", "GET request rejected. Use POST with action=read instead.");
  return respondError("GET not supported. Send all requests via POST.");
}

function doPost(e) {
  if (!isAuthorized(e.parameter)) return respondError("Unauthorized");
  try {
    var action = (e.parameter.action || "legacy").toLowerCase();
    var safeParams = Object.assign({}, e.parameter);
    delete safeParams.api_key;
    logInfo("doPost_Entry", "Action: " + action + " | Params: " + JSON.stringify(safeParams));

    var result;
    switch (action) {
      case "create" : result = handleCreate(e.parameter); break;
      case "read"   : result = handleRead(e.parameter); break;
      case "update" : result = handleUpdate(e.parameter); break;
      case "delete" : result = handleDelete(e.parameter); break;
      case "legacy" : return handleLegacy(e.parameter); // legacy returns text
      default       : return respondError("Unknown action: " + action);
    }
    logInfo("doPost_Success", action + " completed successfully");
    return respond(result);
  } catch (err) {
    logError("doPost_Error", err);
    return respondError(err.message);
  }
}

function handleCreate(params) {
  var lock = LockService.getScriptLock();
  try { lock.waitLock(20000); } catch(e) { throw new Error("Server busy, try again."); }

  try {
    requireParams(params, ["amount"]);
    var sheet = getDbSheet();
    
    // DUPLICATE PREVENTION: Primary check using Timestamp and Amount
    var data = sheet.getDataRange().getValues();
    var pAmount = parseFloat(params.amount);
    var pDate = parseFloat(params.date || 0);
    var pSender = String(params.sender || "").trim().toLowerCase();

    for (var i = 1; i < data.length; i++) {
      var dAmount = parseFloat(data[i][COL.AMOUNT-1]);
      var dDate = parseFloat(data[i][COL.DATE-1]);
      var dSender = String(data[i][COL.SENDER-1] || "").trim().toLowerCase();

      // STRICT DUPLICATE CHECK: Date and Amount must be identical (Millisecond Precision)
      // Date is the millisecond timestamp from the device
      if (dAmount === pAmount && dDate === pDate) {
        logInfo("Create_Skip", "Exact MS Duplicate found. Returning ID: " + data[i][COL.ID-1]);
        return { success: true, action: "create", id: data[i][COL.ID-1], records: [rowToObject(data[i])] };
      }
    }

    var id    = generateId();
    var now   = new Date().toISOString();

    var row = buildEmptyRow();
    row[COL.ID - 1] = id;
    row[COL.DATE - 1] = pDate || new Date().getTime();
    row[COL.AMOUNT - 1] = pAmount;
    row[COL.SENDER - 1] = params.sender || "";
    row[COL.CATEGORY - 1] = params.category || "Other";
    row[COL.STATUS - 1] = params.status || "active";
    row[COL.TYPE - 1] = params.type || "manual";
    row[COL.BODY - 1] = params.body || "";
    row[COL.LATITUDE - 1] = (params.latitude && params.latitude !== "null") ? Number(params.latitude) : "";
    row[COL.LONGITUDE - 1] = (params.longitude && params.longitude !== "null") ? Number(params.longitude) : "";
    row[COL.CREATED_AT - 1] = now;
    row[COL.UPDATED_AT - 1] = now;

    sheet.appendRow(row);
    logInfo("Create", "New record ID: " + id);

    try { handleLegacy(params); } catch(e) { logError("Legacy_Auto_Fail", e); }
    
    return { success: true, action: "create", id: id, records: [rowToObject(row)] };
  } finally {
    lock.releaseLock();
  }
}

function handleRead(params) {
  var sheet   = getDbSheet();
  var records = getAllRecords(sheet);
  if (params.id) {
    var found = records.find(function(r) { return r.id == params.id; });
    if (!found) throw new Error("Record not found: id=" + params.id);
    return { success: true, action: "read", record: found };
  }
  return { success: true, action: "read", count: records.length, records: records };
}

function handleUpdate(params) {
  requireParams(params, ["id"]);
  var sheet = getDbSheet();
  var data  = sheet.getDataRange().getValues();
  var rowIndex = findRowById(data, params.id);
  if (rowIndex === -1) throw new Error("Record not found: id=" + params.id);

  var row = data[rowIndex];
  if (params.amount !== undefined) row[COL.AMOUNT - 1] = Number(params.amount);
  if (params.category !== undefined) row[COL.CATEGORY - 1] = params.category;
  if (params.status !== undefined) row[COL.STATUS - 1] = params.status;
  row[COL.UPDATED_AT - 1] = new Date().toISOString();

  sheet.getRange(rowIndex + 1, 1, 1, row.length).setValues([row]);
  logInfo("Update", "Updated record ID: " + params.id);
  return { success: true, action: "update", id: params.id, record: rowToObject(row) };
}

function handleDelete(params) {
  requireParams(params, ["id"]);
  var sheet = getDbSheet();
  var data = sheet.getDataRange().getValues();
  var rowIndex = findRowById(data, params.id);
  if (rowIndex === -1) throw new Error("Record not found: id=" + params.id);

  if (params.hard === "true") {
    sheet.deleteRow(rowIndex + 1);
    logInfo("Delete", "Hard deleted ID: " + params.id);
    return { success: true, action: "delete", id: params.id, type: "hard" };
  }

  var row = data[rowIndex];
  row[COL.STATUS - 1] = "deleted";
  row[COL.UPDATED_AT - 1] = new Date().toISOString();
  sheet.getRange(rowIndex + 1, 1, 1, row.length).setValues([row]);
  logInfo("Delete", "Soft deleted ID: " + params.id);
  return { success: true, action: "delete", id: params.id, type: "soft", record: rowToObject(row) };
}

function handleLegacy(params) {
  var amount = parseFloat(params.amount);
  if (isNaN(amount) || amount >= 0) {
    logInfo("Legacy_Skip", "Amount " + amount + " ignored (positive or invalid)");
    return respondLegacy("Ignored");
  }

  amount = Math.abs(amount);
  var ss    = SpreadsheetApp.openById(SPREADSHEET_ID);
  var sheet = ss.getSheets()[LEGACY_SHEET_INDEX];
  
  var vals = sheet.getRange("C1:C" + (sheet.getLastRow() + 1)).getValues();
  var targetRow = 1;
  for (var i = 0; i < vals.length; i++) {
    if (vals[i][0] === "" || vals[i][0] === null) {
      targetRow = i + 1;
      break;
    }
  }

  sheet.getRange(targetRow, 3).setValue(amount);
  logInfo("Legacy_Success", "Logged " + amount + " to row " + targetRow + " in sheet index " + LEGACY_SHEET_INDEX);
  return respondLegacy("Success");
}

function getDbSheet() {
  var ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  var sheet = ss.getSheetByName(DB_SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(DB_SHEET_NAME);
    sheet.appendRow(HEADERS);
    sheet.setFrozenRows(1);
    logInfo("Setup", "Created database sheet");
  } else {
    var firstCell = sheet.getRange(1, 1).getValue();
    if (firstCell !== "id") {
      sheet.insertRowBefore(1);
      sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]);
      sheet.setFrozenRows(1);
      logInfo("Setup", "Inserted missing headers");
    } else {
      // Ensure all headers are present in case columns were added
      var currentHeaders = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
      if (currentHeaders.length < HEADERS.length) {
         sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]);
         logInfo("Setup", "Repaired partial headers");
      }
    }
  }
  return sheet;
}

function getAllRecords(sheet) {
  var data = sheet.getDataRange().getValues();
  if (data.length <= 1) return [];
  
  // Stricter header check to avoid returning the header row as data
  var startIndex = 1;
  if (data[0][0] !== "id") {
    logError("Sync", "Header 'id' not found in first row. Unexpected sheet format.");
    // Try to find where the header is, or just skip if it's total garbage
    for (var i = 0; i < data.length; i++) {
      if (data[i][0] === "id") {
        startIndex = i + 1;
        break;
      }
    }
  }

  return data.slice(startIndex).filter(function(row) {
    return row[COL.ID - 1] && String(row[COL.ID - 1]).indexOf("REC-") === 0;
  }).map(function(row) {
    return rowToObject(row);
  });
}

function findRowById(data, id) {
  for (var i = 1; i < data.length; i++) {
    if (String(data[i][COL.ID - 1]) === String(id)) return i;
  }
  return -1;
}

function rowToObject(row) {
  var obj = {};
  Object.keys(COL).forEach(function(key) {
    var val = row[COL[key] - 1];
    // Convert empty strings or undefined to null for numeric/date fields
    if ((val === "" || val === undefined || val === null) && 
        (key === "AMOUNT" || key === "LATITUDE" || key === "LONGITUDE" || key === "DATE")) {
      val = null;
    }
    obj[key.toLowerCase()] = val;
  });
  return obj;
}

function buildEmptyRow() {
  var max = 0;
  for (var k in COL) { if (COL[k] > max) max = COL[k]; }
  return new Array(max).fill("");
}

function generateId() { return "REC-" + new Date().getTime() + "-" + Math.floor(Math.random() * 1000); }
function requireParams(p, f) { f.forEach(function(x) { if (!p[x]) throw new Error("Missing: " + x); }); }
function respond(p) { return ContentService.createTextOutput(JSON.stringify(p)).setMimeType(ContentService.MimeType.JSON); }
function respondError(m) { return respond({ success: false, error: m }); }
function respondLegacy(m) { return ContentService.createTextOutput(m).setMimeType(ContentService.MimeType.TEXT); }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            }
            
            IconButton(
                onClick = {
                    if (isCloudSaved) {
                        scope.launch {
                            Toast.makeText(context, "Syncing with cloud...", Toast.LENGTH_SHORT).show()
                            GoogleSheetsLogger.syncFromCloud(context)
                            Toast.makeText(context, "Sync complete", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Please enable Cloud Sync in settings first", Toast.LENGTH_SHORT).show()
                        isCloudExpanded = true
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = "Sync Now",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
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
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    maxLines = 4,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                            "• Go to Project Settings (gear icon) in Apps Script.\n" +
                            "• Scroll down to 'Script Properties' and click 'Edit script properties'.\n" +
                            "• Add a new property: Property='API_KEY', Value='(your-secret-key)'.\n" +
                            "• Click 'Deploy' > 'New Deployment'.\n" +
                            "• Select 'Web App'. Set 'Who has access' to 'Anyone'.\n" +
                            "• Copy the 'Web App URL' and paste below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("4. API Security Key (Required)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCloudSaved,
                            placeholder = { Text("your-secret-key") },
                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("5. Apps Script Web App URL", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedTextField(
                            value = scriptUrl,
                            onValueChange = { scriptUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCloudSaved,
                            placeholder = { Text("https://script.google.com/macros/s/...") },
                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isCloudSaved) {
                            var isSyncing by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    scope.launch {
                                        isSyncing = true
                                        GoogleSheetsLogger.syncFromCloud(context)
                                        isSyncing = false
                                        Toast.makeText(context, "Cloud data restored!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSecondary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Restoring...")
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Restore from Cloud")
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (isCloudSaved) {
                                TextButton(
                                    onClick = { 
                                        isCloudSaved = false 
                                        sheetUrl = ""
                                        scriptUrl = ""
                                        apiKey = ""
                                        sharedPrefs.edit().remove("sheet_url").remove("script_url").remove("api_key").apply()
                                        GoogleSheetsLogger.updateUrl("")
                                        GoogleSheetsLogger.updateApiKey("")
                                    }
                                ) {
                                    Text("Reset", color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (scriptUrl.isNotBlank() && apiKey.isNotBlank()) {
                                            sharedPrefs.edit()
                                                .putString("sheet_url", sheetUrl)
                                                .putString("script_url", scriptUrl)
                                                .putString("api_key", apiKey)
                                                .apply()
                                            GoogleSheetsLogger.updateUrl(scriptUrl)
                                            GoogleSheetsLogger.updateApiKey(apiKey)
                                            isCloudSaved = true
                                            isCloudExpanded = false // Auto-collapse on save
                                            Toast.makeText(context, "Cloud sync saved", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val msg = if (scriptUrl.isBlank()) "Please enter Web App URL" else "Please enter API Security Key"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
