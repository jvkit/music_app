package com.example.helloworld.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY since ASC")
    fun observeAll(): Flow<List<FriendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FriendEntity>)

    @Query("UPDATE friends SET online = :online WHERE username = :username")
    suspend fun setOnline(username: String, online: Boolean)

    @Query("DELETE FROM friends WHERE username = :username")
    suspend fun delete(username: String)

    @Query("DELETE FROM friends")
    suspend fun clear()
}
