package com.binny.smsforward.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val status: String = "pending",
    val retryCount: Int = 0,
    val destinationId: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
