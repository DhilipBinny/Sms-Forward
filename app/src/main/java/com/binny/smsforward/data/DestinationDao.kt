package com.binny.smsforward.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface DestinationDao {
    @Insert
    suspend fun insert(destination: DestinationEntity): Long

    @Update
    suspend fun update(destination: DestinationEntity)

    @Delete
    suspend fun delete(destination: DestinationEntity)

    @Query("SELECT * FROM destinations")
    fun getAll(): LiveData<List<DestinationEntity>>

    @Query("SELECT * FROM destinations WHERE enabled = 1")
    suspend fun getEnabled(): List<DestinationEntity>

    @Query("SELECT * FROM destinations WHERE id = :id")
    suspend fun getById(id: Long): DestinationEntity?
}
