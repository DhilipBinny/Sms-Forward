package com.binny.smsforward.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.work.*
import com.binny.smsforward.data.AppDatabase
import com.binny.smsforward.data.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class SmsNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SmsNotifListener"
        private const val DEDUP_WINDOW_MS = 10_000L

        private val SMS_PACKAGES = setOf(
            "com.samsung.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.mms",
        )
    }

    private val serviceJob = kotlinx.coroutines.SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val insertMutex = Mutex()

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
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val timestamp = sbn.postTime

        Log.d(TAG, "Notification from=$sender body=${body.take(30)} key=${sbn.key}")

        val db = AppDatabase.get(this)

        serviceScope.launch {
            insertMutex.withLock {
                val since = System.currentTimeMillis() - DEDUP_WINDOW_MS
                val dupeCount = db.messageDao().countRecentWithBody(body, since)
                if (dupeCount > 0) {
                    Log.d(TAG, "Duplicate in DB, skipping: ${body.take(30)}")
                    return@launch
                }

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

                db.messageDao().insert(
                    MessageEntity(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        status = "pending"
                    )
                )
                Log.d(TAG, "Saved to DB, scheduling forward")
            }

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
}
