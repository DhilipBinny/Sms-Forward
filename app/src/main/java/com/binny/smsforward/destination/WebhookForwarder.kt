package com.binny.smsforward.destination

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebhookForwarder(configJson: String) : Forwarder {

    private val url: String
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    init {
        val config = JSONObject(configJson)
        url = config.getString("url")
    }

    override suspend fun forward(sender: String, body: String, timestamp: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("sender", sender)
                    put("body", body)
                    put("timestamp", timestamp)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                response.use {
                    if (it.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("Webhook error: ${it.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun test(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("sender", "test")
                    put("body", "SMS Forward connected!")
                    put("timestamp", System.currentTimeMillis())
                }

                val request = Request.Builder()
                    .url(url)
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                response.use {
                    if (it.isSuccessful) Result.success("Webhook responded: ${it.code}")
                    else Result.failure(Exception("Webhook error: ${it.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
