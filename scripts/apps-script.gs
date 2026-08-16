// ============================================================
//  CONFIGURATION
// ============================================================
var SPREADSHEET_ID = "__SPREADSHEET_ID__";
var API_KEY = PropertiesService.getScriptProperties().getProperty('API_KEY');
var DB_SHEET_NAME  = "database";
var LOG_SHEET_NAME = "logs";
var SETTINGS_SHEET_NAME = "settings";
var LEGACY_SHEET_INDEX = 0; 

var COL = {
  ID: 1, DATE: 2, AMOUNT: 3, SENDER: 4, CATEGORY: 5, STATUS: 6,
  TYPE: 7, BODY: 8, LATITUDE: 9, LONGITUDE: 10, CREATED_AT: 11, UPDATED_AT: 12, TAG: 13
};

var HEADERS = ["id","date","amount","sender","category","status","type","body","latitude","longitude","created_at","updated_at","tag"];
var SETTINGS_HEADERS = ["key","value","updated_at"];

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
      case "settings": result = handleSettings(e.parameter); break;
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

      // FUZZY DUPLICATE CHECK: Date within 2 seconds and Amount very close
      // Date is the millisecond timestamp from the device
      if (Math.abs(dAmount - pAmount) < 0.001 && Math.abs(dDate - pDate) < 2000) {
        logInfo("Create_Skip", "Fuzzy Duplicate found. Returning ID: " + data[i][COL.ID-1]);
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
    row[COL.TAG - 1] = params.tag || "";
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
  if (params.tag !== undefined) row[COL.TAG - 1] = params.tag;
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

function handleSettings(params) {
  var mode = (params.mode || "read").toLowerCase();
  var sheet = getSettingsSheet();

  if (mode === "write") {
    requireParams(params, ["settings_json"]);
    var settings = JSON.parse(params.settings_json);
    var now = new Date().toISOString();
    var rows = [SETTINGS_HEADERS];

    Object.keys(settings).sort().forEach(function(key) {
      rows.push([key, JSON.stringify(settings[key]), now]);
    });

    sheet.clearContents();
    sheet.getRange(1, 1, rows.length, SETTINGS_HEADERS.length).setValues(rows);
    sheet.setFrozenRows(1);
    logInfo("Settings", "Backed up " + (rows.length - 1) + " settings");
    return { success: true, action: "settings", settings: settings };
  }

  return { success: true, action: "settings", settings: getSettingsObject(sheet) };
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

function getSettingsSheet() {
  var ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  var sheet = ss.getSheetByName(SETTINGS_SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SETTINGS_SHEET_NAME);
    sheet.appendRow(SETTINGS_HEADERS);
    sheet.setFrozenRows(1);
    logInfo("Setup", "Created settings sheet");
  } else {
    var firstCell = sheet.getRange(1, 1).getValue();
    if (firstCell !== "key") {
      sheet.insertRowBefore(1);
      sheet.getRange(1, 1, 1, SETTINGS_HEADERS.length).setValues([SETTINGS_HEADERS]);
      sheet.setFrozenRows(1);
      logInfo("Setup", "Inserted missing settings headers");
    }
  }
  return sheet;
}

function getSettingsObject(sheet) {
  var data = sheet.getDataRange().getValues();
  var settings = {};
  if (data.length <= 1) return settings;

  data.slice(1).forEach(function(row) {
    var key = row[0];
    if (!key) return;
    settings[key] = parseSettingValue(row[1]);
  });
  return settings;
}

function parseSettingValue(value) {
  if (value === "" || value === null || value === undefined) return null;
  try {
    return JSON.parse(value);
  } catch (e) {
    return value;
  }
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
