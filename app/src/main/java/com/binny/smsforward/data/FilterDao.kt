package com.binny.smsforward.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FilterDao {
    @Insert
    suspend fun insert(filter: FilterEntity): Long

    @Update
    suspend fun update(filter: FilterEntity)

    @Delete
    suspend fun delete(filter: FilterEntity)

    @Query("SELECT * FROM filters")
    fun getAll(): LiveData<List<FilterEntity>>

    @Query("SELECT * FROM filters WHERE enabled = 1")
    suspend fun getEnabled(): List<FilterEntity>
}
