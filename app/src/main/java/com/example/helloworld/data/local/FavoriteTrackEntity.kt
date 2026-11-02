package com.example.helloworld.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_tracks")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int?,
    val coverUrl: String?,
    val source: String,
    val apiTrackJson: String,
    val addedAt: Long
)
