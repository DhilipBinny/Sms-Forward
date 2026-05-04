package com.binny.smsforward.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): LiveData<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE status = 'pending' OR status = 'failed' ORDER BY timestamp ASC")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'sent' AND createdAt > :since")
    fun getSentCountSince(since: Long): LiveData<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'failed'")
    fun getFailedCount(): LiveData<Int>

    @Query("DELETE FROM messages WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE body = :body AND createdAt > :since")
    suspend fun countRecentWithBody(body: String, since: Long): Int

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE sender LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): LiveData<List<MessageEntity>>
}
