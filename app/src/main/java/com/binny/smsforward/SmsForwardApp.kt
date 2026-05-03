package com.binny.smsforward

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SmsForwardApp : Application() {

    companion object {
        const val CHANNEL_ID = "sms_forward_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Forward Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SMS forwarding active in background"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
