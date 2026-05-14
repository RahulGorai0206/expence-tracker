package com.myapp.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class PinnedWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context, "Widget added to home screen!", Toast.LENGTH_SHORT).show()
    }
}
