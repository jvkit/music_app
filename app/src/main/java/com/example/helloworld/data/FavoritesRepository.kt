package com.example.helloworld.data

import com.example.helloworld.data.local.FavoriteTrackDao
import com.example.helloworld.data.local.FavoriteTrackEntity
import com.example.helloworld.data.remote.MusicApiClient
import com.example.helloworld.data.remote.formatTrackDuration
import com.example.helloworld.data.remote.trackPalette
import com.example.helloworld.data.remote.ApiTrack
import com.example.helloworld.ui.home.Track
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoritesRepository(private val dao: FavoriteTrackDao) {
    private val gson = Gson()

    val tracks: Flow<List<Track>> = dao.observeAll().map { entities ->
        entities.map { it.toTrack() }
    }

    val ids: Flow<Set<String>> = dao.observeIds().map { it.toSet() }

    suspend fun isFavorite(trackId: String): Boolean = dao.getById(trackId) != null

    suspend fun toggle(track: Track): Boolean {
        return if (dao.getById(track.id) != null) {
            dao.delete(track.id)
            false
        } else {
            dao.upsert(track.toEntity())
            true
        }
    }

    /** 登录拉取：用服务器收藏全量覆盖本地 */
    suspend fun replaceAllFromApi(tracks: List<ApiTrack>) {
        dao.getAll().forEach { dao.delete(it.trackId) }
        tracks.forEach { apiTrack ->
            dao.upsert(
                FavoriteTrackEntity(
                    trackId = apiTrack.id,
                    title = apiTrack.title,
                    artist = apiTrack.artist,
                    album = apiTrack.album,
                    durationSeconds = apiTrack.duration,
                    coverUrl = apiTrack.coverUrl?.let { MusicApiClient.resolveServerUrl(it) }
                        ?: MusicApiClient.thumbnailProxyUrl(apiTrack.thumbnail),
                    source = apiTrack.source,
                    apiTrackJson = gson.toJson(apiTrack),
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** 推送导出：本地收藏 → 完整 ApiTrack 列表 */
    suspend fun exportAll(): List<ApiTrack> =
        dao.getAll().mapNotNull { entity ->
            runCatching { gson.fromJson(entity.apiTrackJson, ApiTrack::class.java) }.getOrNull()
        }

    private fun FavoriteTrackEntity.toTrack(): Track {
        val apiTrack = runCatching {
            if (apiTrackJson.isBlank()) null else gson.fromJson(apiTrackJson, ApiTrack::class.java)
        }.getOrNull()
        return Track(
            id = trackId,
            title = title,
            artist = artist,
            duration = formatTrackDuration(durationSeconds),
            colors = trackPalette(trackId),
            durationSeconds = durationSeconds,
            source = source,
            coverUrl = coverUrl,
            apiTrack = apiTrack
        )
    }

    private fun Track.toEntity() = FavoriteTrackEntity(
        trackId = id,
        title = title,
        artist = artist,
        album = apiTrack?.album,
        durationSeconds = durationSeconds,
        coverUrl = coverUrl,
        source = source,
        apiTrackJson = apiTrack?.let { gson.toJson(it) } ?: "",
        addedAt = System.currentTimeMillis()
    )
}
