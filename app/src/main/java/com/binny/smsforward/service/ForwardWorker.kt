package com.binny.smsforward.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.binny.smsforward.R
import com.binny.smsforward.data.AppDatabase
import com.binny.smsforward.destination.ForwarderFactory
import java.util.concurrent.TimeUnit

class ForwardWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ForwardWorker"
        private const val FAILURE_CHANNEL = "sms_forward_failures"

        fun enqueueRetry(context: Context, messageId: Long) {
            val work = OneTimeWorkRequestBuilder<ForwardWorker>()
                .setInputData(workDataOf("retry_message_id" to messageId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("forward_retry_$messageId", ExistingWorkPolicy.REPLACE, work)
        }
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val prefs = applicationContext.getSharedPreferences("sms_forward", Context.MODE_PRIVATE)
        val maxRetries = prefs.getInt("max_retries", 5)

        val retryMessageId = inputData.getLong("retry_message_id", -1)

        val pending = if (retryMessageId > 0) {
            val msg = db.messageDao().getById(retryMessageId)
            if (msg != null) listOf(msg.copy(status = "pending", retryCount = 0)) else emptyList()
        } else {
            db.messageDao().getPendingMessages()
        }

        if (pending.isEmpty()) return Result.success()

        val destinations = db.destinationDao().getEnabled()
        if (destinations.isEmpty()) return Result.success()

        var hasRetryable = false
        var failedCount = 0

        for (message in pending) {
            if (retryMessageId < 0 && message.retryCount >= maxRetries) {
                Log.d(TAG, "Max retries reached for message ${message.id}, marking failed")
                db.messageDao().update(message.copy(status = "failed"))
                failedCount++
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

        if (failedCount > 0) {
            showFailureNotification(failedCount)
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

    private fun showFailureNotification(count: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FAILURE_CHANNEL,
                "Forward Failures",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val text = if (count == 1) "1 message failed to forward" else "$count messages failed to forward"

        val notification = NotificationCompat.Builder(applicationContext, FAILURE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SMS Forward")
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        manager.notify(100, notification)
    }
}
