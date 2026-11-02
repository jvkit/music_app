package com.example.helloworld.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedTrackDao {
    @Query("SELECT * FROM cached_tracks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks WHERE trackId = :trackId LIMIT 1")
    suspend fun getById(trackId: String): CachedTrackEntity?

    @Query("SELECT * FROM cached_tracks")
    suspend fun getAll(): List<CachedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedTrackEntity)

    @Query("DELETE FROM cached_tracks WHERE trackId = :trackId")
    suspend fun delete(trackId: String)
}
