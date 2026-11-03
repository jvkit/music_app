package com.example.helloworld.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.helloworld.ui.home.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLEncoder

class PlayerController(
    context: Context,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val controllerFuture: ListenableFuture<MediaController> = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, YinHeMediaService::class.java))
    ).buildAsync()

    private var controller: MediaController? = null
    private var ticker: Job? = null

    /**
     * 连接期缓冲：MediaController 是异步连上的（冷启动要等 MediaSessionService 起来），
     * 这期间用户点播放/切歌若直接 return 会被静默丢掉——表现为「点了没反应、上下一曲失效」。
     * 所以先排队，连上后按序补执行。
     */
    private val pendingActions = ArrayDeque<(MediaController) -> Unit>()
    @Volatile
    private var connected = false

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null && connected) {
            block(c)
            return
        }
        synchronized(pendingActions) {
            // 双检：排队瞬间可能刚好连上
            val now = controller
            if (now != null && connected) {
                block(now)
            } else {
                pendingActions.addLast({ mc -> block(mc) })
            }
        }
    }

    private val _isPlaying = MutableStateFlow(false)
    private val _isBuffering = MutableStateFlow(false)
    private val _positionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)
    private val _currentMediaId = MutableStateFlow<String?>(null)
    private val _repeatModeLabel = MutableStateFlow(REPEAT_LABELS[0])
    private val _playbackError = MutableStateFlow<String?>(null)

    val isPlaying: StateFlow<Boolean> = _isPlaying
    val isBuffering: StateFlow<Boolean> = _isBuffering
    val positionMs: StateFlow<Long> = _positionMs
    val durationMs: StateFlow<Long> = _durationMs
    val currentMediaId: StateFlow<String?> = _currentMediaId
    val repeatModeLabel: StateFlow<String> = _repeatModeLabel
    val playbackError: StateFlow<String?> = _playbackError

    private var modeIndex = 0

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
            val duration = controller?.duration ?: C.TIME_UNSET
            _durationMs.value = if (duration > 0) duration else 0L
            if (playbackState == Player.STATE_IDLE) {
                _isPlaying.value = false
                _positionMs.value = 0L
                _durationMs.value = 0L
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaId.value = mediaItem?.mediaId
            _positionMs.value = 0L
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            publishModeLabel()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            publishModeLabel()
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackError.value = "播放失败：${error.errorCodeName}"
        }
    }

    init {
        controllerFuture.addListener(
            {
                val mediaController = runCatching { controllerFuture.get() }.getOrNull()
                controller = mediaController
                mediaController?.let { c ->
                    c.addListener(listener)
                    _isPlaying.value = c.isPlaying
                    _currentMediaId.value = c.currentMediaItem?.mediaId
                    syncModeIndex(c)
                    ticker = scope.launch(Dispatchers.Main) {
                        while (isActive) {
                            val position = c.currentPosition
                            val duration = c.duration
                            _positionMs.value = if (position > 0) position else 0L
                            _durationMs.value = if (duration > 0) duration else 0L
                            delay(500)
                        }
                    }
                }
                // 补执行连接期间排队的操作（必须在主线程跑：MediaController 是主线程对象）
                synchronized(pendingActions) {
                    connected = mediaController != null
                    if (mediaController != null) {
                        while (pendingActions.isNotEmpty()) {
                            val action = pendingActions.removeFirst()
                            runCatching { action(mediaController) }
                        }
                    }
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        loadQueue(tracks, startIndex, autoPlay = true)
    }

    /**
     * 冷启动恢复队列：只装载、prepare 到可播状态，**不自动播放**。
     * 这样播放条的播放/上一首/下一首都有真实队列可操作，不会"点了没反应"。
     */
    fun restoreQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        loadQueue(tracks, startIndex, autoPlay = false)
    }

    private fun loadQueue(tracks: List<Track>, startIndex: Int, autoPlay: Boolean) {
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        TrackPayloadStore.putAll(tracks.associate { it.id to it.apiTrack })
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(buildTrackUri(track.id))
                // v2 命名空间：旧版本写入的"截断却被标记完整"的缓存条目全部作废（曾导致歌曲只播 30s 显示 100%）
                .setCustomCacheKey("v2:${track.id}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(track.coverUrl?.let(Uri::parse))
                        .build()
                )
                .build()
        }
        _playbackError.value = null
        withController { c ->
            c.setMediaItems(items, safeIndex, C.TIME_UNSET)
            c.prepare()
            c.playWhenReady = autoPlay
        }
    }

    fun toggle() {
        _playbackError.value = null
        withController { c ->
            if (c.mediaItemCount == 0) return@withController
            if (c.isPlaying) c.pause() else c.play()
        }
    }

    fun play() {
        _playbackError.value = null
        withController { it.play() }
    }

    fun pause() {
        // 暂停不用排队：连接前本来就没在放
        controller?.pause()
    }

    fun seekToNext() {
        _playbackError.value = null
        withController { c ->
            if (c.mediaItemCount == 0) return@withController
            if (c.hasNextMediaItem() || c.repeatMode == Player.REPEAT_MODE_ALL) {
                c.seekToNextMediaItem()
                c.prepare()
                c.playWhenReady = true
            }
        }
    }

    fun seekToPrevious() {
        _playbackError.value = null
        withController { c ->
            if (c.mediaItemCount == 0) return@withController
            val atFirstItem = c.currentMediaItemIndex == 0
            if (atFirstItem && c.currentPosition <= 3000L) {
                c.seekTo(c.mediaItemCount - 1, C.TIME_UNSET)
                c.prepare()
                c.playWhenReady = true
            } else {
                c.seekToPrevious()
            }
        }
    }

    fun stop() {
        withController { c ->
            c.stop()
            c.clearMediaItems()
        }
        _isPlaying.value = false
        _isBuffering.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        _currentMediaId.value = null
    }

    fun cycleRepeatMode() {
        modeIndex = (modeIndex + 1) % REPEAT_LABELS.size
        withController { c -> applyMode(c, modeIndex) }
        publishModeLabel()
    }

    fun seekTo(positionMs: Long) {
        withController { c ->
            if (c.mediaItemCount == 0) return@withController
            c.seekTo(positionMs.coerceAtLeast(0L))
        }
    }

    private fun applyMode(c: MediaController, index: Int) {
        when (index) {
            MODE_ORDER -> {
                c.repeatMode = Player.REPEAT_MODE_OFF
                c.shuffleModeEnabled = false
            }
            MODE_REPEAT_LIST -> {
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.shuffleModeEnabled = false
            }
            MODE_REPEAT_ONE -> {
                c.repeatMode = Player.REPEAT_MODE_ONE
                c.shuffleModeEnabled = false
            }
            MODE_SHUFFLE -> {
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.shuffleModeEnabled = true
            }
        }
    }

    private fun syncModeIndex(c: MediaController) {
        modeIndex = when {
            c.repeatMode == Player.REPEAT_MODE_ONE -> MODE_REPEAT_ONE
            c.shuffleModeEnabled -> MODE_SHUFFLE
            c.repeatMode == Player.REPEAT_MODE_ALL -> MODE_REPEAT_LIST
            else -> MODE_ORDER
        }
        publishModeLabel()
    }

    private fun publishModeLabel() {
        _repeatModeLabel.value = REPEAT_LABELS[modeIndex]
    }

    fun clearError() {
        _playbackError.value = null
    }

    fun release() {
        ticker?.cancel()
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
    }

    companion object {
        const val MODE_ORDER = 0
        const val MODE_REPEAT_LIST = 1
        const val MODE_REPEAT_ONE = 2
        const val MODE_SHUFFLE = 3

        val REPEAT_LABELS = listOf("顺序", "列表", "单曲", "随机")

        fun buildTrackUri(trackId: String): Uri =
            Uri.parse("yinhe://play?id=${URLEncoder.encode(trackId, "UTF-8")}")
    }
}
