package com.smartspend.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-registers SMS listener after device reboot */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // SMS BroadcastReceiver is registered in manifest; it auto-starts.
        // If you add persistent foreground services, start them here.
    }
}
