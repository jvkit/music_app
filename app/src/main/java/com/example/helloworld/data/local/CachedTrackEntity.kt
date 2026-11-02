package com.example.helloworld.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int?,
    val coverUrl: String?,
    val source: String,
    val streamUrl: String,
    val apiTrackJson: String,
    val totalBytes: Long,
    val cachedBytes: Long,
    val status: String,
    val updatedAt: Long
) {
    companion object {
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_DONE = "done"
    }
}
