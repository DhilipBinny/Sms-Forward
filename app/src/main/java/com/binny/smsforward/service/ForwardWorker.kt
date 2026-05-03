package com.binny.smsforward.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.binny.smsforward.data.AppDatabase
import com.binny.smsforward.destination.ForwarderFactory
import java.util.concurrent.TimeUnit

class ForwardWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ForwardWorker"
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val prefs = applicationContext.getSharedPreferences("sms_forward", android.content.Context.MODE_PRIVATE)
        val maxRetries = prefs.getInt("max_retries", 5)
        val pending = db.messageDao().getPendingMessages()

        if (pending.isEmpty()) return Result.success()

        val destinations = db.destinationDao().getEnabled()
        if (destinations.isEmpty()) return Result.success()

        var hasRetryable = false

        for (message in pending) {
            if (message.retryCount >= maxRetries) {
                Log.d(TAG, "Max retries reached for message ${message.id}, marking failed")
                db.messageDao().update(message.copy(status = "failed"))
                continue
            }

            var allDestinationsOk = true

            for (destination in destinations) {
                val forwarder = ForwarderFactory.create(destination)
                val result = forwarder.forward(message.sender, message.body, message.timestamp)

                if (result.isFailure) {
                    Log.e(TAG, "Forward failed to ${destination.name}: ${result.exceptionOrNull()?.message}")
                    allDestinationsOk = false
                }
            }

            if (allDestinationsOk) {
                db.messageDao().update(message.copy(status = "sent", retryCount = message.retryCount + 1))
                Log.d(TAG, "Message ${message.id} sent successfully")
            } else {
                db.messageDao().update(message.copy(status = "pending", retryCount = message.retryCount + 1))
                hasRetryable = true
            }
        }

        if (hasRetryable) {
            val retryWork = OneTimeWorkRequestBuilder<ForwardWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork("forward_sms_retry", ExistingWorkPolicy.KEEP, retryWork)
        }

        return Result.success()
    }
}
