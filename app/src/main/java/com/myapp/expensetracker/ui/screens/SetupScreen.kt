package com.myapp.expensetracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.myapp.expensetracker.AppDatabase
import com.myapp.expensetracker.GoogleSheetsLogger
import com.myapp.expensetracker.R
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    
    var budgetText by remember { mutableStateOf("") }
    var isCloudSyncEnabled by remember { mutableStateOf(false) }
    var sheetUrl by remember { mutableStateOf("") }
    var scriptUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "SetupTransition",
                modifier = Modifier.weight(1f)
            ) { step ->
                when (step) {
                    0 -> WelcomeStep()
                    1 -> FeaturesStep()
                    2 -> BudgetStep(
                        value = budgetText,
                        isError = showError && budgetText.isEmpty(),
                        onValueChange = { 
                            budgetText = it
                            if (it.isNotEmpty()) showError = false
                        }
                    )
                    3 -> CloudSyncStep(
                        isEnabled = isCloudSyncEnabled,
                        onToggle = { isCloudSyncEnabled = it },
                        sheetUrl = sheetUrl,
                        onSheetUrlChange = { sheetUrl = it },
                        scriptUrl = scriptUrl,
                        onUrlChange = { scriptUrl = it },
                        apiKey = apiKey,
                        onKeyChange = { apiKey = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Progress Indicators
            Row(
                modifier = Modifier.padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) { index ->
                    val width by animateDpAsState(
                        targetValue = if (currentStep == index) 24.dp else 8.dp,
                        label = "DotWidth"
                    )
                    val color by animateColorAsState(
                        targetValue = if (currentStep == index) MaterialTheme.colorScheme.primary 
                                     else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        label = "DotColor"
                    )
                    Box(
                        modifier = Modifier
                            .size(width, 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            Button(
                onClick = {
                    if (isTestingConnection) return@Button
                    when (currentStep) {
                        0 -> currentStep = 1
                        1 -> currentStep = 2
                        2 -> {
                            if (budgetText.isEmpty()) {
                                showError = true
                            } else {
                                showError = false
                                currentStep = 3
                            }
                        }
                        3 -> {
                            if (isCloudSyncEnabled) {
                                if (scriptUrl.isBlank() || apiKey.isBlank()) {
                                    Toast.makeText(context, "Please fill all cloud fields", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                
                                scope.launch {
                                    isTestingConnection = true
                                    val error = GoogleSheetsLogger.testConnection(scriptUrl, apiKey)
                                    if (error == null) {
                                        sharedPrefs.edit().apply {
                                            putFloat("budget", budgetText.toFloatOrNull() ?: 0f)
                                            putBoolean("cloud_sync", true)
                                            putString("sheet_url", sheetUrl)
                                            putString("script_url", scriptUrl)
                                            putString("api_key", apiKey)
                                            putBoolean("is_setup_complete", true)
                                            apply()
                                        }
                                        GoogleSheetsLogger.updateUrl(scriptUrl)
                                        GoogleSheetsLogger.updateApiKey(apiKey)
                                        onSetupComplete()
                                    } else {
                                        isTestingConnection = false
                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                sharedPrefs.edit().apply {
                                    putFloat("budget", budgetText.toFloatOrNull() ?: 0f)
                                    putBoolean("cloud_sync", false)
                                    remove("sheet_url")
                                    remove("script_url")
                                    remove("api_key")
                                    putBoolean("is_setup_complete", true)
                                    apply()
                                }
                                onSetupComplete()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                AnimatedContent(
                    targetState = isTestingConnection,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "ButtonContent"
                ) { testing ->
                    if (testing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Checking connection...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = when (currentStep) {
                                    0 -> stringResource(R.string.setup_get_started)
                                    1, 2 -> stringResource(R.string.setup_continue)
                                    else -> stringResource(R.string.setup_finish)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.setup_welcome_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.setup_welcome_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
fun BudgetStep(value: String, isError: Boolean, onValueChange: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.setup_budget_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.setup_budget_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(48.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.setup_budget_label)) },
            placeholder = { Text(stringResource(R.string.setup_budget_placeholder)) },
            prefix = { Text(stringResource(R.string.setup_currency_prefix), fontWeight = FontWeight.Bold) },
            isError = isError,
            supportingText = {
                if (isError) {
                    Text(stringResource(R.string.setup_budget_error), color = MaterialTheme.colorScheme.error)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun FeaturesStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.setup_features_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        FeatureItem(
            icon = Icons.Default.Sms,
            title = stringResource(R.string.setup_feature_sms_title),
            description = stringResource(R.string.setup_feature_sms_desc)
        )
        Spacer(modifier = Modifier.height(24.dp))
        FeatureItem(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            title = stringResource(R.string.setup_feature_insights_title),
            description = stringResource(R.string.setup_feature_insights_desc)
        )
    }
}

@Composable
fun CloudSyncStep(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    sheetUrl: String,
    onSheetUrlChange: (String) -> Unit,
    scriptUrl: String,
    onUrlChange: (String) -> Unit,
    apiKey: String,
    onKeyChange: (String) -> Unit
) {
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
    
    // Safely clone parameters into a pure JS object, explicitly ignoring the API key
    var safeParams = {};
    for (var key in e.parameter) {
      if (key.toLowerCase() !== 'api_key') { // .toLowerCase() catches API_KEY, api_key, etc.
        safeParams[key] = e.parameter[key];
      }
    }

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

    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.CloudSync,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.setup_cloud_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.setup_cloud_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            onClick = { onToggle(!isEnabled) }
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.setup_cloud_enable_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }
        }
        
        AnimatedVisibility(
            visible = isEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Instructions Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.setup_instructions_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf(
                            R.string.setup_step_1, R.string.setup_step_2, R.string.setup_step_3,
                            R.string.setup_step_4, R.string.setup_step_5, R.string.setup_step_6,
                            R.string.setup_step_7, R.string.setup_step_8, R.string.setup_step_9,
                            R.string.setup_step_10, R.string.setup_step_11
                        ).forEach { stepRes ->
                            Text(
                                stringResource(stepRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = sheetUrl,
                    onValueChange = onSheetUrlChange,
                    label = { Text(stringResource(R.string.setup_sheet_url_label)) },
                    placeholder = { Text(stringResource(R.string.setup_sheet_url_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Apps Script Code", scriptCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.setup_copy_code))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onKeyChange,
                    label = { Text(stringResource(R.string.setup_cloud_key_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                            if (apiKey.isNotEmpty()) {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("API Key", apiKey)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Key copied to clipboard", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                                }
                            }
                            TextButton(onClick = { 
                                val charPool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_"
                                val randomKey = (1..43)
                                    .map { charPool.random() }
                                    .joinToString("")
                                onKeyChange(randomKey)
                            }) {
                                Text(stringResource(R.string.setup_cloud_generate_key))
                            }
                        }
                    },
                    readOnly = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = scriptUrl,
                    onValueChange = onUrlChange,
                    label = { Text(stringResource(R.string.setup_cloud_url_label)) },
                    placeholder = { Text(stringResource(R.string.setup_cloud_url_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.setup_cloud_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun FeatureItem(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
