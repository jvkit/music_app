package com.example.helloworld.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.helloworld.MainActivity
import com.example.helloworld.R
import com.example.helloworld.data.CacheRepository
import com.example.helloworld.data.SourceCatalog
import com.example.helloworld.data.local.CachedTrackEntity
import com.example.helloworld.data.local.YinHeDatabase
import com.example.helloworld.data.remote.ApiTrack
import com.example.helloworld.data.remote.MusicApiClient
import com.example.helloworld.data.remote.PreviewRequest
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class YinHeMediaService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var cache: SimpleCache
    private lateinit var cacheRepository: CacheRepository
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private var cacheWatcher: Job? = null
    private var stallWatcher: Job? = null

    /** trackId -> 已尝试过的音源 id（换源链路的记账本） */
    private val triedSources = ConcurrentHashMap<String, MutableList<String>>()

    /** trackId -> 已做过试听剪辑判定（每首只判定一次） */
    private val truncationChecked = ConcurrentHashMap<String, Boolean>()

    private val resolver = object : ResolvingDataSource.Resolver {
        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val uri = dataSpec.uri
            if (uri.scheme != SCHEME_YINHE) return dataSpec
            val trackId = uri.getQueryParameter("id") ?: throw IOException("缺少曲目 ID")
            val url = resolveStreamUrl(trackId)
            return dataSpec.withUri(Uri.parse(url))
        }
    }

    /**
     * 多音源解析：原始音源 → 按优先级逐个换源，直到拿到可播放地址。
     *
     * 正确性约束：换源只在「同名同歌手」的候选里挑（bestMatch 标题须完全匹配），
     * 宁可失败也不乱放一首同名的别的歌；每次尝试限时（OkHttp 6s/8s）。
     */
    private fun resolveStreamUrl(trackId: String): String {
        TrackPayloadStore.resolvedUrl(trackId)?.let { return it }
        val payload = TrackPayloadStore.get(trackId)
        if (payload == null) {
            // 无元数据时只能指望本地缓存（离线兜底）
            return runBlocking { cachedFallbackUrl(trackId) }
                ?: throw IOException("缺少服务端歌曲信息")
        }
        if (triedSources.size > 64) triedSources.clear()
        val tried = triedSources.getOrPut(trackId) { mutableListOf() }
        val chain = (listOf(payload.source) + SourceCatalog.fallbackOrder(payload.source))
            .filter { it !in tried }
        if (chain.isEmpty()) {
            YinheLogger.log("播放", "《${payload.title}》${triedSources[trackId]?.size ?: 0} 个音源全部试过，放弃")
            throw IOException("所有音源均已尝试")
        }
        var lastError: Exception = IOException("无可用音源")
        var lastGood: String? = null
        for (src in chain) {
            tried.add(src)
            val candidate = if (src == payload.source) {
                payload
            } else {
                findOnSource(src, payload)
            }
            if (candidate == null) {
                YinheLogger.log("换源", "${SourceCatalog.displayName(src)} 未找到《${payload.title}》")
                continue
            }
            try {
                val stream = previewBlocking(candidate)
                if (TrackPayloadStore.isBlocked(trackId, stream)) {
                    // 该地址此前被判定不完整（试听剪辑/截断），跳过这个源
                    lastError = IOException("流地址疑似不完整，已屏蔽")
                    YinheLogger.record(src, ok = false, detail = "stream-blocked")
                    continue
                }
                lastGood = stream
                TrackPayloadStore.cacheResolvedUrl(trackId, stream)
                TrackPayloadStore.markPlayedSource(trackId, candidate.source)
                runBlocking { cacheRepository.trackPlayStarted(trackId, payload, stream) }
                if (candidate.source != payload.source) {
                    YinheLogger.log(
                        "换源",
                        "《${payload.title}》${SourceCatalog.displayName(payload.source)} 不可用 → " +
                            "${SourceCatalog.displayName(candidate.source)} 救回"
                    )
                }
                YinheLogger.record(candidate.source, ok = true)
                return stream
            } catch (e: Exception) {
                lastError = e
                YinheLogger.record(src, ok = false, detail = (e.message ?: e.javaClass.simpleName).take(60))
            }
        }
        // 全部源试尽：若有被屏蔽剪辑外的可用地址，宁可放它也不跳歌（元数据时长可能是错的）
        lastGood?.let {
            YinheLogger.log("播放", "《${payload.title}》换源无更优，用回此前地址")
            TrackPayloadStore.cacheResolvedUrl(trackId, it)
            return it
        }
        YinheLogger.log("播放", "《${payload.title}》全部音源均失败，最后一错：${lastError.message}")
        throw lastError
    }

    private fun previewBlocking(track: ApiTrack): String = runBlocking {
        val response = MusicApiClient.service.preview(PreviewRequest(track = track))
        MusicApiClient.resolveServerUrl(response.streamUrl)
            ?: throw IOException("服务器未返回播放地址")
    }

    /** 在其他音源搜同名同歌手候选；匹配不上返回 null（不乱换） */
    private fun findOnSource(source: String, want: ApiTrack): ApiTrack? {
        val query = listOf(want.title, want.artist).filter { it.isNotBlank() }.joinToString(" ")
        return runCatching {
            val resp = runBlocking {
                MusicApiClient.service.search(query = query, source = source, limit = 8, offset = 0)
            }
            bestMatch(resp.tracks, want)
        }.getOrNull()
    }

    private fun bestMatch(candidates: List<ApiTrack>, want: ApiTrack): ApiTrack? {
        fun norm(s: String?) = (s ?: "")
            .lowercase()
            .replace(Regex("[\\s\\p{P}\\p{S}]+"), "")
            .replace(Regex("[（(](live|remix|伴奏|cover|原唱|深情版|dj|加速版)[^）)]*[）)]"), "")
        val wantTitle = norm(want.title)
        val wantArtist = norm(want.artist)
        if (wantTitle.isEmpty()) return null
        var best: ApiTrack? = null
        var bestScore = 0
        for (c in candidates) {
            val t = norm(c.title)
            val a = norm(c.artist)
            var score = 0
            if (t == wantTitle) score += 4
            else if (t.contains(wantTitle) || wantTitle.contains(t)) score += 2
            if (a.isNotEmpty() && a == wantArtist) score += 3
            else if (a.isNotEmpty() && (a.contains(wantArtist) || wantArtist.contains(a))) score += 1
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        // 标题完全匹配才换（4 分），防止同名异曲污染播放队列
        return if (bestScore >= 4) best else null
    }

    private suspend fun cachedFallbackUrl(trackId: String): String? {
        val cached = cacheRepository.get(trackId)
        return if (cached != null && cached.status == CachedTrackEntity.STATUS_DONE) {
            YinheLogger.log("播放", "服务器不可达，使用本地缓存播放")
            cached.streamUrl
        } else {
            null
        }
    }

    override fun onCreate() {
        super.onCreate()
        cacheRepository = CacheRepository(YinHeDatabase.getInstance(this).cachedTrackDao())
        cache = SimpleCache(
            File(cacheDir, "yinhe_audio"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(this)
        )
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory().setUserAgent("YinHeMusic/1.0")
            )
        val resolvingFactory = ResolvingDataSource.Factory(cacheDataSourceFactory, resolver)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekForwardIncrementMs(15_000)
            .setSeekBackIncrementMs(15_000)
            .build()
        // 预加载下一首：提前 30s 缓冲队列中后续曲目，切歌秒播（配合下面的 URL 预解析）
        runCatching {
            player.setPreloadConfiguration(ExoPlayer.PreloadConfiguration(30_000L))
        }
        player.setWakeMode(C.WAKE_MODE_LOCAL)
        player.addListener(playerListener)

        session = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        val notificationProvider = DefaultMediaNotificationProvider(this)
        notificationProvider.setSmallIcon(R.drawable.ic_yinhe_note)
        setMediaNotificationProvider(notificationProvider)

        cacheWatcher = serviceScope.launch(Dispatchers.Main) { watchCacheProgress() }
        stallWatcher = serviceScope.launch(Dispatchers.Main) { watchStall() }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.let { current ->
                val dead = triedSources.keys.filter { it != current }
                dead.forEach {
                    triedSources.remove(it)
                    truncationChecked.remove(it)
                    TrackPayloadStore.clearBlocked(it)
                }
            }
            // 预解析下一首的播放地址：切歌时 resolveDataSpec 命中缓存，零等待秒播
            preloadNextUrl()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_IS_LOADING_CHANGED
                )
            ) {
                checkTruncatedStream()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val title = player.currentMediaItem?.mediaMetadata?.title ?: "未知歌曲"
            YinheLogger.log("播放", "《$title》出错 ${error.errorCodeName}")
            // 已解析地址可能已过期（签名 URL 有时效）：清掉让重试/换源重新解析，避免在缓存边界反复撞旧地址
            player.currentMediaItem?.mediaId?.let { TrackPayloadStore.clearResolved(it) }
            handleSourceFailure()
        }
    }

    /**
     * 试听剪辑检测：部分音源给的是 15s/30s/50s 试听片段，播放器会当完整歌放完。
     * 流时长明显短于元数据时长时，屏蔽当前地址并换源找完整版；
     * 每个曲目只判定一次，避免与元数据时长不准的歌互相拉扯。
     */
    private fun checkTruncatedStream() {
        if (player.playbackState != Player.STATE_READY) return
        val item = player.currentMediaItem ?: return
        val trackId = item.mediaId
        if (trackId.isBlank() || truncationChecked.containsKey(trackId)) return
        val durationMs = player.duration
        if (durationMs == C.TIME_UNSET || durationMs <= 0) return
        truncationChecked[trackId] = true
        val expectedMs = (TrackPayloadStore.get(trackId)?.duration ?: 0) * 1000L
        if (expectedMs < 90_000L) return
        if (durationMs >= expectedMs * 0.6 || durationMs >= 75_000L) return
        val title = item.mediaMetadata.title ?: trackId
        val badSource = TrackPayloadStore.playedSource(trackId)
        YinheLogger.log(
            "换源",
            "《$title》实际 ${durationMs / 1000}s ≪ 元数据 ${expectedMs / 1000}s，疑似试听剪辑（${badSource ?: "?"}）→ 换源"
        )
        TrackPayloadStore.resolvedUrl(trackId)?.let { TrackPayloadStore.blockUrl(trackId, it) }
        if (badSource != null) triedSources.getOrPut(trackId) { mutableListOf() }.add(badSource)
        TrackPayloadStore.clearResolved(trackId)
        player.setMediaItem(item.buildUpon().build())
        player.prepare()
        player.playWhenReady = true
    }

    /** 解析队列中下一首的流地址并存入 TrackPayloadStore（失败静默，播放时还会走正常解析） */
    private fun preloadNextUrl() {
        val nextIndex = player.currentMediaItemIndex + 1
        if (nextIndex >= player.mediaItemCount) return
        val nextId = player.getMediaItemAt(nextIndex).mediaId
        if (nextId.isBlank()) return
        if (TrackPayloadStore.resolvedUrl(nextId) != null) return
        val payload = TrackPayloadStore.get(nextId) ?: return
        serviceScope.launch {
            runCatching {
                val stream = previewBlocking(payload)
                TrackPayloadStore.cacheResolvedUrl(nextId, stream)
                TrackPayloadStore.markPlayedSource(nextId, payload.source)
            }
        }
    }

    /** 当前源失败：优先换源重试同一首，音源试尽才切下一首 */
    private fun handleSourceFailure() {
        val item = player.currentMediaItem ?: run { player.pause(); return }
        val trackId = item.mediaId
        val triedCount = triedSources[trackId]?.size ?: 0
        if (trackId.isNotBlank() && triedCount < MAX_SOURCE_TRIES) {
            YinheLogger.log("换源", "《${item.mediaMetadata?.title}》已试 $triedCount 源，继续换源重试")
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = true
            return
        }
        YinheLogger.log("播放", "《${item.mediaMetadata?.title}》音源试尽，跳到下一首")
        if (player.hasNextMediaItem() || player.repeatMode == Player.REPEAT_MODE_ALL) {
            player.seekToNextMediaItem()
            player.prepare()
            player.playWhenReady = true
        } else {
            player.pause()
        }
    }

    /** 缓冲看门狗：播放心切状态下卡 buffer 超过阈值视为失败，走换源链路 */
    private suspend fun watchStall() {
        var bufferingSinceMs = 0L
        while (currentCoroutineContext().isActive) {
            if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
                val now = System.currentTimeMillis()
                if (bufferingSinceMs == 0L) {
                    bufferingSinceMs = now
                } else if (now - bufferingSinceMs > STALL_TIMEOUT_MS) {
                    bufferingSinceMs = 0L
                    YinheLogger.log("播放", "《${player.currentMediaItem?.mediaMetadata?.title}》缓冲超时，换源")
                    handleSourceFailure()
                }
            } else {
                bufferingSinceMs = 0L
            }
            delay(1000)
        }
    }

    private suspend fun watchCacheProgress() {
        var lastTrackId: String? = null
        var lastBytes = -1L
        while (currentCoroutineContext().isActive) {
            val trackId = player.currentMediaItem?.mediaId
            if (trackId == null || trackId != lastTrackId) {
                lastTrackId = trackId
                lastBytes = -1L
            }
            if (trackId != null) {
                val key = cacheKey(trackId)
                val bytes = runCatching { cache.getCachedBytes(key, 0, Long.MAX_VALUE) }
                    .getOrDefault(0L)
                if (bytes != lastBytes) {
                    lastBytes = bytes
                    val total = runCatching {
                        ContentMetadata.getContentLength(cache.getContentMetadata(key))
                    }.getOrDefault(-1L)
                    withContext(Dispatchers.IO) {
                        cacheRepository.updateProgress(trackId, bytes, total)
                    }
                }
                // 毒缓存自愈：字节数已封顶但码率低到离谱（如 320s 的歌只有 ~500KB），
                // 是旧版本"断流被当正常 EOF"写入的截断缓存——清掉让它重新拉完整流
                if (bytes > 0 && bytes == lastBytes) {
                    val entity = cacheRepository.get(trackId)
                    val dur = entity?.durationSeconds ?: 0
                    if (entity != null && dur >= 120 && bytes < dur * 8_000L) {
                        YinheLogger.log("播放", "《${entity.title}》缓存疑似截断（${bytes / 1024}KB / ${dur}s），清除重拉")
                        runCatching { cache.removeResource(key) }
                        TrackPayloadStore.clearResolved(trackId)
                        withContext(Dispatchers.IO) { cacheRepository.remove(trackId) }
                        lastBytes = -1L
                    }
                }
            }
            delay(1500)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        cacheWatcher?.cancel()
        stallWatcher?.cancel()
        session?.release()
        player.removeListener(playerListener)
        player.release()
        runBlocking {
            cacheRepository.reconcile(
                actualCachedBytes = { trackId ->
                    runCatching { cache.getCachedBytes(cacheKey(trackId), 0, Long.MAX_VALUE) }.getOrDefault(0L)
                },
                actualContentLength = { trackId ->
                    runCatching { ContentMetadata.getContentLength(cache.getContentMetadata(cacheKey(trackId))) }
                        .getOrDefault(-1L)
                }
            )
        }
        cache.release()
        super.onDestroy()
    }

    companion object {
        const val SESSION_ID = "yinhe_media_session"
        const val SCHEME_YINHE = "yinhe"
        /** SimpleCache key 命名空间：换版本号可整体作废历史缓存（如 v1 的毒缓存截断条目） */
        private const val CACHE_NS = "v2:"
        private fun cacheKey(trackId: String) = "$CACHE_NS$trackId"

        private const val MAX_CACHE_BYTES = 512L * 1024 * 1024
        private const val MAX_SOURCE_TRIES = 7
        private const val STALL_TIMEOUT_MS = 12_000L
    }
}
