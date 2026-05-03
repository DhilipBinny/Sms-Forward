package com.binny.smsforward.destination

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NtfyForwarder(configJson: String) : Forwarder {

    private val server: String
    private val topic: String
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    init {
        val config = JSONObject(configJson)
        server = config.optString("server", "https://ntfy.sh")
        topic = config.getString("topic")
    }

    override suspend fun forward(sender: String, body: String, timestamp: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$server/$topic")
                    .post(body.toRequestBody("text/plain".toMediaType()))
                    .addHeader("Title", "SMS from $sender")
                    .addHeader("Tags", "envelope")
                    .build()

                val response = client.newCall(request).execute()
                response.use {
                    if (it.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("Ntfy error: ${it.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun test(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$server/$topic")
                    .post("SMS Forward connected!".toRequestBody("text/plain".toMediaType()))
                    .addHeader("Title", "Test Message")
                    .addHeader("Tags", "white_check_mark")
                    .build()

                val response = client.newCall(request).execute()
                response.use {
                    if (it.isSuccessful) Result.success("Sent! Check your Ntfy app.")
                    else Result.failure(Exception("Ntfy error: ${it.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
