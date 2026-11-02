package com.example.helloworld.data

import com.example.helloworld.data.local.CachedTrackDao
import com.example.helloworld.data.local.CachedTrackEntity
import com.example.helloworld.data.remote.ApiTrack
import com.example.helloworld.data.remote.formatTrackDuration
import com.example.helloworld.data.remote.trackPalette
import com.example.helloworld.ui.home.Track
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CacheRepository(private val dao: CachedTrackDao) {
    private val gson = Gson()

    val tracks: Flow<List<Track>> = dao.observeAll().map { entities ->
        entities.map { it.toTrack() }
    }

    val statusMap: Flow<Map<String, String>> = dao.observeAll().map { entities ->
        entities.associate { it.trackId to it.status }
    }

    /** trackId -> (已缓存字节, 总字节)，用于展示「缓存 xx%」 */
    val progressMap: Flow<Map<String, Pair<Long, Long>>> = dao.observeAll().map { entities ->
        entities.associate { it.trackId to (it.cachedBytes to it.totalBytes) }
    }

    suspend fun get(trackId: String): CachedTrackEntity? = dao.getById(trackId)

    suspend fun trackPlayStarted(track: Track, streamUrl: String) {
        val existing = dao.getById(track.id)
        dao.upsert(
            CachedTrackEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.apiTrack?.album,
                durationSeconds = track.durationSeconds,
                coverUrl = track.coverUrl,
                source = track.source,
                streamUrl = streamUrl,
                apiTrackJson = track.apiTrack?.let { gson.toJson(it) } ?: "",
                totalBytes = existing?.totalBytes ?: 0L,
                cachedBytes = existing?.cachedBytes ?: 0L,
                status = CachedTrackEntity.STATUS_DOWNLOADING,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun trackPlayStarted(trackId: String, payload: ApiTrack, streamUrl: String) {
        val existing = dao.getById(trackId)
        dao.upsert(
            CachedTrackEntity(
                trackId = trackId,
                title = payload.title,
                artist = payload.artist,
                album = payload.album,
                durationSeconds = payload.duration,
                coverUrl = payload.coverUrl?.let { com.example.helloworld.data.remote.MusicApiClient.resolveServerUrl(it) }
                    ?: com.example.helloworld.data.remote.MusicApiClient.thumbnailProxyUrl(payload.thumbnail),
                source = payload.source,
                streamUrl = streamUrl,
                apiTrackJson = gson.toJson(payload),
                totalBytes = existing?.totalBytes ?: 0L,
                cachedBytes = existing?.cachedBytes ?: 0L,
                status = CachedTrackEntity.STATUS_DOWNLOADING,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateProgress(trackId: String, cachedBytes: Long, totalBytes: Long) {
        val existing = dao.getById(trackId) ?: return
        val knownTotal = if (totalBytes > 0) totalBytes else existing.totalBytes
        val done = knownTotal > 0 && cachedBytes >= knownTotal
        dao.upsert(
            existing.copy(
                cachedBytes = cachedBytes,
                totalBytes = knownTotal,
                status = if (done) CachedTrackEntity.STATUS_DONE else CachedTrackEntity.STATUS_DOWNLOADING,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun remove(trackId: String) = dao.delete(trackId)

    suspend fun reconcile(actualCachedBytes: (String) -> Long, actualContentLength: (String) -> Long) {
        dao.getAll().forEach { entity ->
            val bytes = actualCachedBytes(entity.trackId)
            if (bytes <= 0L) {
                dao.delete(entity.trackId)
            } else {
                val fromCache = actualContentLength(entity.trackId)
                val total = maxOf(entity.totalBytes, fromCache)
                val done = total > 0 && bytes >= total
                dao.upsert(
                    entity.copy(
                        cachedBytes = bytes,
                        totalBytes = total,
                        status = if (done) CachedTrackEntity.STATUS_DONE else CachedTrackEntity.STATUS_DOWNLOADING,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun CachedTrackEntity.toTrack(): Track {
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
}
