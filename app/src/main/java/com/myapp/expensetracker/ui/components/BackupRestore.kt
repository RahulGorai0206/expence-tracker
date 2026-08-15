package com.myapp.expensetracker.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.expensetracker.BackupManager
import com.myapp.expensetracker.BackupResult
import com.myapp.expensetracker.BackupScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drives export/import through the Storage Access Framework so the user picks
 * the file location themselves — no storage permission required.
 */
@Stable
class BackupController internal constructor(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onResult: (BackupResult) -> Unit
) {
    var isBusy by mutableStateOf(false)
        private set
    var busyMessage by mutableStateOf("")
        private set

    internal var pendingScope: BackupScope = BackupScope.DATA
    internal var launchCreate: (String) -> Unit = {}
    internal var launchOpen: () -> Unit = {}

    fun startExport(scope: BackupScope) {
        if (isBusy) return
        pendingScope = scope
        launchCreate(BackupManager.suggestedFileName(scope))
    }

    fun startImport() {
        if (isBusy) return
        launchOpen()
    }

    internal fun onCreateDocumentResult(uri: Uri?) {
        if (uri == null) return // user cancelled
        val scope = pendingScope
        coroutineScope.launch {
            isBusy = true
            busyMessage = "Writing backup file…"
            val result = BackupManager.exportTo(context, uri, scope)
            isBusy = false
            onResult(result)
        }
    }

    internal fun onOpenDocumentResult(uri: Uri?) {
        if (uri == null) return // user cancelled
        coroutineScope.launch {
            isBusy = true
            busyMessage = "Reading backup file…"
            val result = BackupManager.importFrom(context, uri)
            isBusy = false
            onResult(result)
        }
    }
}

@Composable
fun rememberBackupController(
    coroutineScope: CoroutineScope,
    onResult: (BackupResult) -> Unit
): BackupController {
    val context = LocalContext.current
    val controller = remember(context) {
        BackupController(context.applicationContext, coroutineScope, onResult)
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE)
    ) { uri -> controller.onCreateDocumentResult(uri) }

    // Deliberately "*/*": file pickers and cloud providers report .json files
    // with wildly inconsistent MIME types, and a strict filter hides the very
    // file the user is looking for. The contents are validated on read.
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> controller.onOpenDocumentResult(uri) }

    controller.launchCreate = { fileName -> createLauncher.launch(fileName) }
    controller.launchOpen = { openLauncher.launch(arrayOf("*/*")) }

    return controller
}

/** Blocking progress dialog shown while a backup is being written or read. */
@Composable
fun BackupProgressDialog(controller: BackupController) {
    if (!controller.isBusy) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Working…", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    controller.busyMessage,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {},
        dismissButton = {},
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
