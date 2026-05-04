package com.binny.smsforward.destination

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class WebhookForwarder(configJson: String) : Forwarder {

    private val url: String
    private val format: String
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    init {
        val config = JSONObject(configJson)
        url = config.getString("url")
        format = config.optString("format", "generic")
    }

    override suspend fun forward(sender: String, body: String, timestamp: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = buildPayload(sender, body, timestamp)
                val request = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody("application/json".toMediaType()))
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
                val payload = buildPayload("Test", "SMS Forward connected!", System.currentTimeMillis())
                val request = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody("application/json".toMediaType()))
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

    private fun buildPayload(sender: String, body: String, timestamp: Long): String {
        val time = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))
        val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        return when (format) {
            "slack" -> JSONObject().apply {
                put("text", "📨 *$sender*\n🕓 $time\n\n$body\n\n_via ${device}_")
            }.toString()

            "discord" -> JSONObject().apply {
                put("embeds", JSONArray().put(JSONObject().apply {
                    put("title", "📨 $sender")
                    put("description", "$body\n\n_via ${device}_")
                    put("color", 13923150) // #D4714E
                    put("footer", JSONObject().put("text", time))
                }))
            }.toString()

            else -> JSONObject().apply {
                put("sender", sender)
                put("body", body)
                put("timestamp", timestamp)
                put("device", device)
            }.toString()
        }
    }
}
