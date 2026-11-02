package com.example.helloworld.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteTrackDao {
    @Query("SELECT * FROM favorite_tracks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteTrackEntity>>

    @Query("SELECT trackId FROM favorite_tracks")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT * FROM favorite_tracks WHERE trackId = :trackId LIMIT 1")
    suspend fun getById(trackId: String): FavoriteTrackEntity?

    @Query("SELECT * FROM favorite_tracks")
    suspend fun getAll(): List<FavoriteTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE trackId = :trackId")
    suspend fun delete(trackId: String)
}
