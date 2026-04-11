package com.myapp.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d("BootReceiver", "Device booted / app updated — checking if monitoring is enabled")
            if (SmsMonitorService.isEnabled(context)) {
                SmsMonitorService.start(context)
                Log.d("BootReceiver", "SmsMonitorService started after boot")
            }
        }
    }
}
