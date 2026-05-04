package com.binny.smsforward.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.binny.smsforward.R
import com.binny.smsforward.data.AppDatabase
import com.binny.smsforward.data.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SmsNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SmsNotifListener"

        private val SMS_PACKAGES = setOf(
            "com.samsung.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.oneplus.mms",
            "com.miui.messaging",
            "com.coloros.smsmms",
            "com.vivo.messaging",
            "com.asus.message",
        )
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg !in SMS_PACKAGES) return

        val prefs = getSharedPreferences("sms_forward", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("forwarding_enabled", false)) return

        val extras = sbn.notification.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return

        val body = extractBody(extras, sbn)
        if (body == null) {
            Log.d(TAG, "No body extracted, may be sensitive content")
            showSensitiveContentGuidance()
            return
        }
        if (body.contains("sensitive notification", ignoreCase = true) ||
            body.contains("content hidden", ignoreCase = true)) {
            Log.d(TAG, "Sensitive content hidden by OS")
            showSensitiveContentGuidance()
            return
        }

        val timestamp = sbn.postTime

        Log.d(TAG, "Notification from=$sender body=${body.take(30)} key=${sbn.key}")

        val db = AppDatabase.get(this)

        serviceScope.launch {
            val filters = db.filterDao().getEnabled()
            val passes = filters.isEmpty() || filters.any { f ->
                when (f.type) {
                    "sender" -> sender.contains(f.value, ignoreCase = true)
                    "keyword" -> body.contains(f.value, ignoreCase = true)
                    else -> false
                }
            }

            if (!passes) {
                Log.d(TAG, "Filtered out: $sender")
                return@launch
            }

            val id = db.messageDao().insert(
                MessageEntity(
                    sender = sender,
                    body = body,
                    timestamp = timestamp,
                    status = "pending"
                )
            )

            if (id < 0) {
                Log.d(TAG, "Duplicate, DB ignored insert")
                return@launch
            }

            Log.d(TAG, "Saved to DB (id=$id), scheduling forward")

            val work = OneTimeWorkRequestBuilder<ForwardWorker>()
                .setInitialDelay(2, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(this@SmsNotificationListener)
                .enqueueUniqueWork("forward_sms", ExistingWorkPolicy.KEEP, work)
        }
    }

    private fun extractBody(extras: android.os.Bundle, sbn: StatusBarNotification): String? {
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let {
            if (it.isNotBlank()) return it
        }
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let {
            if (it.isNotBlank()) return it
        }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { lines ->
            val combined = lines.joinToString("\n")
            if (combined.isNotBlank()) return combined
        }
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.let { messages ->
            val last = messages.lastOrNull() as? android.os.Bundle
            last?.getCharSequence("text")?.toString()?.let {
                if (it.isNotBlank()) return it
            }
        }
        sbn.notification.tickerText?.toString()?.let {
            if (it.isNotBlank()) return it
        }
        return null
    }

    private fun showSensitiveContentGuidance() {
        val prefs = getSharedPreferences("sms_forward", Context.MODE_PRIVATE)
        if (prefs.getBoolean("sensitive_guidance_shown", false)) return
        prefs.edit().putBoolean("sensitive_guidance_shown", true).apply()

        val channelId = "sms_forward_guidance"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Setup Guidance", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SMS content hidden by Android")
            .setContentText("Tap to fix: disable Enhanced Notifications")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Android is hiding SMS content. Go to Settings → Notifications → Enhanced notifications → turn OFF to allow SMS Forward to read messages."
            ))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(200, notification)
    }
}
