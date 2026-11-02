package com.example.helloworld.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImMessageDao {
    @Query("SELECT * FROM im_messages WHERE peer = :peer ORDER BY ts ASC, id ASC")
    fun observeFor(peer: String): Flow<List<ImMessageEntity>>

    @Insert
    suspend fun insert(message: ImMessageEntity): Long

    @Query("SELECT peer, COUNT(*) AS cnt FROM im_messages WHERE read = 0 AND outgoing = 0 GROUP BY peer")
    fun observeUnreadCounts(): Flow<List<UnreadCount>>

    @Query("UPDATE im_messages SET read = 1 WHERE peer = :peer AND outgoing = 0")
    suspend fun markRead(peer: String)
}

data class UnreadCount(val peer: String, val cnt: Int)
