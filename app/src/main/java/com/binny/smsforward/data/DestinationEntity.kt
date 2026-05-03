package com.binny.smsforward.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "destinations")
data class DestinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // telegram, ntfy, webhook
    val name: String,
    val enabled: Boolean = true,
    val config: String // JSON string with type-specific config
)
