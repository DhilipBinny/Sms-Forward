package com.binny.smsforward.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("sms_forward", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("forwarding_enabled", false)) return

        val serviceIntent = Intent(context, ForwardService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
