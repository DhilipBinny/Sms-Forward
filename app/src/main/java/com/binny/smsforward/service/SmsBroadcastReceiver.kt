package com.binny.smsforward.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.*
import com.binny.smsforward.data.AppDatabase
import com.binny.smsforward.data.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences("sms_forward", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("forwarding_enabled", false)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val grouped = messages.groupBy { it.originatingAddress ?: "Unknown" }
        val db = AppDatabase.get(context)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                var anySaved = false

                for ((sender, parts) in grouped) {
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    val timestamp = parts.first().timestampMillis

                    Log.d(TAG, "SMS from $sender: ${body.take(30)}")

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
                        continue
                    }

                    val id = db.messageDao().insert(
                        MessageEntity(
                            sender = sender,
                            body = body,
                            timestamp = timestamp,
                            status = "pending"
                        )
                    )

                    if (id > 0) {
                        Log.d(TAG, "Saved to DB via broadcast (id=$id)")
                        anySaved = true
                    } else {
                        Log.d(TAG, "Duplicate, DB ignored insert")
                    }
                }

                if (anySaved) {
                    val work = OneTimeWorkRequestBuilder<ForwardWorker>()
                        .setInitialDelay(2, TimeUnit.SECONDS)
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .build()

                    WorkManager.getInstance(context)
                        .enqueueUniqueWork("forward_sms", ExistingWorkPolicy.KEEP, work)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
