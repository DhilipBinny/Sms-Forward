package com.binny.smsforward.destination

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class TelegramForwarder(configJson: String) : Forwarder {

    private val botToken: String
    private val chatId: String
    private val client = ForwarderFactory.sharedClient

    init {
        val config = JSONObject(configJson)
        botToken = config.getString("bot_token")
        chatId = config.getString("chat_id")
    }

    override suspend fun forward(sender: String, body: String, timestamp: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val time = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                    .format(Date(timestamp))

                val escapedBody = body
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                val escapedSender = sender
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")

                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

                val text = buildString {
                    append("📨 <b>$escapedSender</b>\n")
                    append("🕓 $time\n\n")
                    append("$escapedBody\n\n")
                    append("<i>via $deviceName</i>")
                }

                val json = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                    put("parse_mode", "HTML")
                }

                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/sendMessage")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                response.use {
                    if (it.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        val errorBody = it.body?.string() ?: "Unknown error"
                        Result.failure(Exception("Telegram error ${it.code}: $errorBody"))
                    }
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
                    put("chat_id", chatId)
                    put("text", "SMS Forward connected successfully!\nYour messages will appear here.")
                }

                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/sendMessage")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                response.use {
                    if (it.isSuccessful) {
                        Result.success("Message sent! Check your Telegram.")
                    } else {
                        val errorBody = it.body?.string() ?: ""
                        val errorJson = runCatching { JSONObject(errorBody) }.getOrNull()
                        val description = errorJson?.optString("description") ?: "HTTP ${it.code}"
                        Result.failure(Exception(description))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
