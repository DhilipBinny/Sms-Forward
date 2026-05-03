package com.binny.smsforward.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filters")
data class FilterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // sender, keyword
    val value: String,
    val enabled: Boolean = true
)
