package com.binny.smsforward.destination

import com.binny.smsforward.data.DestinationEntity

interface Forwarder {
    suspend fun forward(sender: String, body: String, timestamp: Long): Result<Unit>
    suspend fun test(): Result<String>
}

object ForwarderFactory {
    fun create(destination: DestinationEntity): Forwarder {
        return when (destination.type) {
            "telegram" -> TelegramForwarder(destination.config)
            "ntfy" -> NtfyForwarder(destination.config)
            "webhook" -> WebhookForwarder(destination.config)
            else -> throw IllegalArgumentException("Unknown destination: ${destination.type}")
        }
    }
}
