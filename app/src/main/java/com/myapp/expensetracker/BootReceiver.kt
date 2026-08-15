package com.myapp.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.myapp.expensetracker.worker.FeatureNudgeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d("BootReceiver", "Device booted / app updated — checking if monitoring is enabled")
            FeatureNudgeWorker.ensureScheduled(context)

            // Timeout alarms don't survive a reboot; recover anything they left
            // stranded before it can be lost.
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    TransactionApproval.reconcileAbandoned(context)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Pending reconciliation failed", e)
                } finally {
                    pendingResult.finish()
                }
            }
            if (SmsMonitorService.isEnabled(context)) {
                SmsMonitorService.start(context)
                Log.d("BootReceiver", "SmsMonitorService started after boot")
            }
        }
    }
}
