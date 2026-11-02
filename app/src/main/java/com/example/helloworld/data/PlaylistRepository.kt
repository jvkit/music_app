package com.example.helloworld.data

import com.example.helloworld.data.local.PlaylistDao
import com.example.helloworld.data.local.PlaylistEntity
import com.example.helloworld.data.local.PlaylistTrackDao
import com.example.helloworld.data.local.PlaylistTrackEntity
import com.example.helloworld.data.model.Playlist
import com.example.helloworld.data.remote.ApiTrack
import com.example.helloworld.data.remote.formatTrackDuration
import com.example.helloworld.data.remote.trackPalette
import com.example.helloworld.ui.home.Track
import com.google.gson.Gson
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

/**
 * 自建歌单仓库：歌单元数据 + 独立曲目行（带完整 ApiTrack 载荷）。
 *
 * 同步模型（配合用户系统）：服务器是唯一事实源，本仓库负责本地缓存与
 * 变更上报。所有曲目操作携带完整 Track，保证换设备后仅凭元数据即可重播。
 */
class PlaylistRepository(
    private val dao: PlaylistDao,
    private val trackDao: PlaylistTrackDao
) {
    private val gson = Gson()
    private val trackMutationLock = Mutex()

    val playlists: Flow<List<Playlist>> = dao.observeAll().map { entities ->
        entities.map { it.toModel() }
    }

    fun tracksFor(playlistId: String): Flow<List<Track>> =
        trackDao.observeFor(playlistId).map { rows -> rows.mapNotNull { it.toTrack() } }

    /** 所有歌单的曲目 map（添加歌曲时「从其他歌单选」用） */
    val allTracksMap: Flow<Map<String, List<Track>>> =
        trackDao.observeAll().map { rows ->
            rows.groupBy { it.playlistId }
                .mapValues { (_, list) -> list.mapNotNull { it.toTrack() } }
        }

    suspend fun ensureDefaultPlaylist() {
        if (dao.count() == 0) {
            dao.insert(
                PlaylistEntity(
                    id = UUID.randomUUID().toString(),
                    name = "青春循环",
                    description = "那些一听就回到夏天的歌",
                    createdAt = System.currentTimeMillis(),
                    trackIdsJson = encodeTrackIds(emptyList())
                )
            )
        }
        // 旧版数据迁移：trackIdsJson 有值但 playlist_tracks 为空时回填占位行
        val all = dao.observeAll().first()
        for (entity in all) {
            val legacyIds = decodeTrackIds(entity.trackIdsJson)
            if (legacyIds.isNotEmpty() && trackDao.countFor(entity.id) == 0) {
                trackDao.upsertAll(
                    legacyIds.mapIndexed { index, trackId ->
                        PlaylistTrackEntity(
                            playlistId = entity.id,
                            position = index,
                            trackId = trackId,
                            trackJson = ""
                        )
                    }
                )
            }
        }
    }

    suspend fun createPlaylist(name: String, description: String): String {
        val id = UUID.randomUUID().toString()
        dao.insert(
            PlaylistEntity(
                id = id,
                name = name.trim(),
                description = description.trim(),
                createdAt = System.currentTimeMillis(),
                trackIdsJson = encodeTrackIds(emptyList())
            )
        )
        return id
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        dao.update(playlist.toEntity())
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        trackDao.clearFor(playlist.id)
        dao.delete(playlist.toEntity())
    }

    suspend fun addTrack(playlistId: String, track: Track): Boolean {
        val payload = track.apiTrack ?: return false
        trackMutationLock.withLock {
            val rows = trackDao.getFor(playlistId)
            if (rows.any { it.trackId == track.id }) return false
            val json = gson.toJson(payload)
            trackDao.upsertAll(rows + PlaylistTrackEntity(playlistId, rows.size, track.id, json))
            syncTrackIdsColumn(playlistId)
        }
        return true
    }

    /** 批量添加（多选导入）：跳过已在歌单里的，一次锁内完成 */
    suspend fun addTracks(playlistId: String, tracks: List<Track>): Int {
        var added = 0
        trackMutationLock.withLock {
            val rows = trackDao.getFor(playlistId).toMutableList()
            var changed = false
            for (track in tracks) {
                val payload = track.apiTrack ?: continue
                if (rows.any { it.trackId == track.id }) continue
                rows += PlaylistTrackEntity(playlistId, rows.size, track.id, gson.toJson(payload))
                added++
                changed = true
            }
            if (changed) {
                trackDao.upsertAll(rows)
                syncTrackIdsColumn(playlistId)
            }
        }
        return added
    }

    suspend fun removeTrack(playlistId: String, trackId: String) {
        trackMutationLock.withLock {
            trackDao.delete(playlistId, trackId)
            reindex(playlistId)
        }
    }

    suspend fun moveTrack(playlistId: String, fromIndex: Int, toIndex: Int) {
        trackMutationLock.withLock {
            val rows = trackDao.getFor(playlistId).toMutableList()
            if (fromIndex !in rows.indices || toIndex !in rows.indices) return@withLock
            val moved = rows.removeAt(fromIndex)
            rows.add(toIndex, moved)
            trackDao.upsertAll(rows.mapIndexed { index, row -> row.copy(position = index) })
            reindex(playlistId)
        }
    }

    private suspend fun reindex(playlistId: String) {
        val rows = trackDao.getFor(playlistId)
        trackDao.upsertAll(rows.mapIndexed { index, row -> row.copy(position = index) })
        syncTrackIdsColumn(playlistId)
    }

    /** 保持旧 trackIdsJson 列与曲目行一致（UI 计数仍读它） */
    private suspend fun syncTrackIdsColumn(playlistId: String) {
        val entity = dao.getById(playlistId) ?: return
        val ids = trackDao.getFor(playlistId).map { it.trackId }
        dao.update(entity.copy(trackIdsJson = encodeTrackIds(ids)))
    }

    /** 登录拉取：用服务器快照全量覆盖本地歌单与曲目 */
    suspend fun replaceAll(payloads: List<PlaylistPayloadLite>) {
        val existing = dao.observeAll().first()
        existing.forEach { entity ->
            trackDao.clearFor(entity.id)
            dao.delete(entity)
        }
        for (payload in payloads) {
            dao.insert(
                PlaylistEntity(
                    id = payload.id,
                    name = payload.name,
                    description = payload.description,
                    createdAt = payload.createdAt,
                    trackIdsJson = encodeTrackIds(payload.tracks.map { it.id })
                )
            )
            trackDao.upsertAll(
                payload.tracks.mapIndexed { index, apiTrack ->
                    PlaylistTrackEntity(
                        playlistId = payload.id,
                        position = index,
                        trackId = apiTrack.id,
                        trackJson = gson.toJson(apiTrack)
                    )
                }
            )
        }
    }

    /** 全量快照（同步服务器用）：曲目载荷缺失的旧数据自动跳过，无法跨设备重播 */
    suspend fun exportSnapshot(): Pair<List<PlaylistPayloadLite>, List<Track>> {
        val playlists = dao.observeAll().first().map { entity ->
            val tracks = trackDao.getFor(entity.id).mapNotNull { row ->
                row.toTrack()?.takeIf { it.apiTrack != null }
            }
            PlaylistPayloadLite(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                createdAt = entity.createdAt,
                tracks = tracks.mapNotNull { it.apiTrack }
            )
        }
        return playlists to emptyList()
    }

    private fun PlaylistTrackEntity.toTrack(): Track? {
        if (trackJson.isBlank()) {
            // 旧版迁移占位：无载荷，仅展示占位
            return Track(
                id = trackId,
                title = "未知歌曲",
                artist = "旧版本数据",
                duration = "--:--",
                colors = trackPalette(trackId)
            )
        }
        val apiTrack = runCatching { gson.fromJson(trackJson, ApiTrack::class.java) }.getOrNull()
            ?: return null
        return Track(
            id = trackId,
            title = apiTrack.title,
            artist = apiTrack.artist,
            duration = formatTrackDuration(apiTrack.duration),
            colors = trackPalette(trackId),
            durationSeconds = apiTrack.duration,
            source = apiTrack.source,
            coverUrl = apiTrack.coverUrl?.let { com.example.helloworld.data.remote.MusicApiClient.resolveServerUrl(it) }
                ?: com.example.helloworld.data.remote.MusicApiClient.thumbnailProxyUrl(apiTrack.thumbnail),
            apiTrack = apiTrack
        )
    }

    data class PlaylistPayloadLite(
        val id: String,
        val name: String,
        val description: String,
        val createdAt: Long,
        val tracks: List<ApiTrack>
    )

    private fun PlaylistEntity.toModel() = Playlist(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        trackIds = decodeTrackIds(trackIdsJson)
    )

    private fun Playlist.toEntity() = PlaylistEntity(
        id = id,
        name = name.trim(),
        description = description.trim(),
        createdAt = createdAt,
        trackIdsJson = encodeTrackIds(trackIds)
    )

    private fun encodeTrackIds(trackIds: List<String>): String {
        return JSONArray().apply { trackIds.forEach(::put) }.toString()
    }

    private fun decodeTrackIds(json: String): List<String> {
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())
    }
}
