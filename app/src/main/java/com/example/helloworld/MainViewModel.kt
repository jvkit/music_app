package com.example.helloworld

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.helloworld.data.CacheRepository
import com.example.helloworld.data.FavoritesRepository
import com.example.helloworld.data.PlaylistRepository
import com.example.helloworld.data.SourceCatalog
import com.example.helloworld.data.local.AuthRepository
import com.example.helloworld.data.local.ImMessageEntity
import com.example.helloworld.data.local.SettingsRepository
import com.example.helloworld.data.local.YinHeDatabase
import com.example.helloworld.data.model.Playlist
import com.example.helloworld.data.remote.ApiTrack
import com.example.helloworld.data.remote.AuthRequest
import com.example.helloworld.data.remote.DiscoverCategory
import com.example.helloworld.data.remote.DiscoverPlaylistDto
import com.example.helloworld.data.remote.FriendRequest
import com.example.helloworld.data.remote.MusicApiClient
import com.example.helloworld.data.remote.PetEventRequest
import com.example.helloworld.data.remote.PetFeedRequest
import com.example.helloworld.data.remote.PetFriendRequest
import com.example.helloworld.data.remote.PetInteractRequest
import com.example.helloworld.data.remote.PetPairDto
import com.example.helloworld.data.remote.PetClaimRequest
import com.example.helloworld.data.remote.PetShelfItem
import com.example.helloworld.data.remote.PetActionDef
import com.example.helloworld.data.remote.PetShopItem
import com.example.helloworld.data.remote.PetSpeciesRequest
import com.example.helloworld.data.remote.PetRenameRequest
import com.example.helloworld.data.remote.PetBuyActionRequest
import com.example.helloworld.data.remote.YinheImClient
import com.example.helloworld.data.remote.PlaylistPayload
import com.example.helloworld.data.remote.UserLibraryRequest
import com.example.helloworld.data.remote.toUiTrack
import com.example.helloworld.playback.AsyncListState
import com.example.helloworld.playback.PlayerController
import com.example.helloworld.playback.TrackPayloadStore
import com.example.helloworld.ui.recognize.AudioCaptures
import com.example.helloworld.bubble.BubbleBus
import com.example.helloworld.bubble.BubbleService
import com.example.helloworld.playback.YinheLogger
import com.example.helloworld.ui.home.PlaceholderTrack
import com.example.helloworld.ui.home.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    /** 上游代码直接用 application 调系统服务 / 起服务，而 AndroidViewModel 的 application 是 private，这里显式暴露 */
    private val app: Application = application
    private val database = YinHeDatabase.getInstance(application)
    private val repository = PlaylistRepository(database.playlistDao(), database.playlistTrackDao())
    private val favoritesRepository = FavoritesRepository(database.favoriteTrackDao())
    private val cacheRepository = CacheRepository(database.cachedTrackDao())
    private val playerController = PlayerController(application, viewModelScope)

    /** 定时关闭：到点只暂停，不退出服务（用户可能马上又听） */
    private val sleepTimer = com.example.helloworld.playback.SleepTimer(viewModelScope) {
        playerController.pause()
        sleepTimerFired = true
    }
    val sleepTimerState = sleepTimer.state

    /** 刚触发过定时关闭：UI 用来弹一次「已暂停」提示 */
    var sleepTimerFired by mutableStateOf(false)
        private set

    fun clearSleepTimerFired() {
        sleepTimerFired = false
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimer.start(minutes)
    }

    /** 播完当前首再停 */
    fun startSleepTimerEndOfTrack() {
        sleepTimer.startEndOfTrack(playerController.currentMediaId.value)
    }

    fun cancelSleepTimer() {
        sleepTimer.cancel()
    }

    private val api get() = MusicApiClient.service

    sealed interface AuthState {
        data object Loading : AuthState
        data object LoggedOut : AuthState
        data class LoggedIn(val username: String) : AuthState
    }

    /** 我的头像文件名 + 版本号（上传后自增，给 Coil 缓存击穿用） */
    var meAvatar by mutableStateOf<String?>(null)
        private set
    var avatarVersion by mutableStateOf(0L)
        private set

    fun avatarUrl(username: String): String =
        (MusicApiClient.resolveServerUrl("api/auth/avatar/$username") ?: "") + "?v=$avatarVersion"

    /** 预设头像列表（打开用户中心时刷新） */
    var avatarPresets by mutableStateOf<List<String>>(emptyList())
        private set

    fun avatarPresetUrl(name: String): String =
        MusicApiClient.resolveServerUrl("api/auth/avatar_preset/$name") ?: ""

    fun refreshAvatarPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.avatarPresets() }
                .onSuccess { withContext(Dispatchers.Main) { avatarPresets = it.presets } }
        }
    }

    fun chooseAvatarPreset(name: String, onResult: (String?) -> Unit) {
        val token = AuthRepository.token() ?: run { onResult("未登录"); return }
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching {
                api.useAvatarPreset(com.example.helloworld.data.remote.AvatarPresetRequest(name), "Bearer $token")
            }
            withContext(Dispatchers.Main) {
                r.onSuccess {
                    meAvatar = it.avatar
                    avatarVersion++
                    onResult(null)
                }.onFailure { onResult(it.message ?: "设置失败") }
            }
        }
    }

    fun changeUsername(newName: String, onResult: (String?) -> Unit) {
        val token = AuthRepository.token() ?: run { onResult("未登录"); return }
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching { api.rename(com.example.helloworld.data.remote.RenameRequest(newName), "Bearer $token") }
            withContext(Dispatchers.Main) {
                r.onSuccess { resp ->
                    AuthRepository.save(resp.token, resp.username)
                    authState = AuthState.LoggedIn(resp.username)
                    refreshFriends()
                    onResult(null)
                }.onFailure { onResult(it.message ?: "修改失败") }
            }
        }
    }

    fun changePassword(oldP: String, newP: String, onResult: (String?) -> Unit) {
        val token = AuthRepository.token() ?: run { onResult("未登录"); return }
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching {
                api.changePassword(com.example.helloworld.data.remote.PasswordChangeRequest(oldP, newP), "Bearer $token")
            }
            withContext(Dispatchers.Main) {
                r.onSuccess { resp ->
                    AuthRepository.save(resp.token, resp.username)
                    onResult(null)
                }.onFailure { onResult(it.message ?: "修改失败") }
            }
        }
    }

    fun uploadAvatar(context: android.content.Context, uri: android.net.Uri, onResult: (String?) -> Unit) {
        val token = AuthRepository.token() ?: run { onResult("未登录"); return }
        viewModelScope.launch(Dispatchers.IO) {
            val base64 = try {
                encodeAvatar(context, uri)
            } catch (e: Exception) {
                YinheLogger.log("头像", "读取失败: ${e.javaClass.simpleName}: ${(e.message ?: "").take(80)}")
                withContext(Dispatchers.Main) { onResult(e.message ?: "图片读取失败") }
                return@launch
            }
            val r = runCatching {
                api.uploadAvatar(com.example.helloworld.data.remote.AvatarUploadRequest(base64), "Bearer $token")
            }
            withContext(Dispatchers.Main) {
                r.onSuccess {
                    meAvatar = it.avatar
                    avatarVersion++
                    YinheLogger.log("头像", "上传成功: ${it.avatar}")
                    onResult(null)
                }.onFailure {
                    YinheLogger.log("头像", "上传失败: ${(it.message ?: it.javaClass.simpleName).take(100)}")
                    onResult(it.message ?: "上传失败")
                }
            }
        }
    }

    /** 降采样到 512px 内 + JPEG 压缩 + base64。老解码器失败的格式（HEIC/HEIF 等）走 ImageDecoder */
    private fun encodeAvatar(context: android.content.Context, uri: android.net.Uri): String {
        val resolver = context.contentResolver
        android.util.Log.w("YinheAvatar", "选中 uri=$uri scheme=${uri.scheme} authority=${uri.authority}")
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = try {
            resolver.openInputStream(uri)
        } catch (e: Exception) {
            android.util.Log.w("YinheAvatar", "openInputStream(读尺寸) 抛错: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
        if (boundsStream == null) {
            // 部分 ROM（vivo 实测）Photo Picker 返回的 uri openInputStream 返回 null，走 ImageDecoder 兜底
            return encodeAvatarViaImageDecoder(context, uri)
        }
        boundsStream.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return encodeAvatarViaImageDecoder(context, uri)
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = try {
            resolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (e: Exception) {
            android.util.Log.w("YinheAvatar", "openInputStream(解码) 抛错: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
        if (bmp == null) return encodeAvatarViaImageDecoder(context, uri)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
        return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    }

    /** API 28+ ImageDecoder 兜底（支持 HEIC/HEIF/WebP 等新格式），失败给出可读错误 */
    private fun encodeAvatarViaImageDecoder(context: android.content.Context, uri: android.net.Uri): String {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
            throw IllegalArgumentException("图片格式不支持，请换一张 JPG/PNG")
        }
        val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        val bmp = try {
            android.graphics.ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = 512f / maxOf(w, h)
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("YinheAvatar", "ImageDecoder 也失败: ${e.javaClass.simpleName}: ${e.message}")
            throw IllegalArgumentException("图片读取失败（${e.javaClass.simpleName}），请换一张或重启应用后再试")
        }
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
        return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    }

    var authState by mutableStateOf<AuthState>(AuthState.Loading)
        private set
    var authError by mutableStateOf<String?>(null)
        private set
    var authBusy by mutableStateOf(false)
        private set
    var syncMessage by mutableStateOf<String?>(null)
        private set

    private var pushJob: Job? = null

    val playlists = repository.playlists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
    val favoriteTracks = favoritesRepository.tracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
    val favorites = favoritesRepository.ids.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )
    val cachedTracks = cacheRepository.tracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
    val cacheStatus = cacheRepository.statusMap.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )
    val cacheProgress = cacheRepository.progressMap.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )
    val repeatModeLabel = playerController.repeatModeLabel

    var selectedTab by mutableStateOf(0)

    /** 精灵中心投喂页展开态：全屏只显示角色（隐藏标题/tab 栏/迷你播放器/底部导航） */
    var petFullscreen by mutableStateOf(false)
    var selectedPlaylistId by mutableStateOf<String?>(null)
        private set
    var currentTrack by mutableStateOf(PlaceholderTrack)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var playbackPositionMs by mutableLongStateOf(0L)
        private set
    var playbackDurationMs by mutableLongStateOf(0L)
        private set
    var playbackError by mutableStateOf<String?>(null)
        private set
    var libraryState by mutableStateOf(AsyncListState<Track>())

    /** 今日每首的播放次数（服务端按天统计，首页排序/今日推荐用） */
    var todayPlays by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** 首页大卡：今日被播过、有封面、有歌词的歌 + 歌词文本（几句轮换展示） */
    var heroTrack by mutableStateOf<Track?>(null)
        private set
    var heroLyrics by mutableStateOf<List<String>>(emptyList())
        private set

    /** 从今日播放里挑一首有封面且有歌词的，取几句词做大卡动效 */
    private fun refreshHeroPick() {
        val candidates = libraryState.items
            .filter { !it.coverUrl.isNullOrBlank() && (todayPlays[it.id] ?: 0) > 0 }
            .sortedByDescending { todayPlays[it.id] ?: 0 }
        val track = candidates.firstOrNull() ?: return
        heroTrack = track
        heroLyrics = emptyList()
        val payload = track.apiTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val lines = runCatching {
                api.lyrics(com.example.helloworld.data.remote.LyricsRequest(payload))
            }.getOrNull()?.lines?.mapNotNull { it.text.takeIf { t -> t.isNotBlank() } }
                ?.filter { it.length <= 30 }
                ?.take(4)
                ?: emptyList()
            withContext(Dispatchers.Main) {
                if (heroTrack?.id == track.id) heroLyrics = lines
            }
        }
    }

    var searchState by mutableStateOf(AsyncListState<Track>())
        private set

    // ===================== 发现页（榜单/分类歌单墙/歌单详情/私人FM） =====================

    var discoverCategories by mutableStateOf<List<DiscoverCategory>>(emptyList())
        private set

    /** 当前分类（特殊值 personalized=为你推荐、hq=精品） */
    var discoverCat by mutableStateOf("personalized")
        private set
    var discoverToplists by mutableStateOf(AsyncListState<DiscoverPlaylistDto>())
        private set
    var discoverWall by mutableStateOf(AsyncListState<DiscoverPlaylistDto>())
        private set

    /** 歌单/榜单/FM 详情展开态；null=回到歌单墙 */
    var discoverDetail by mutableStateOf<DiscoverDetailUi?>(null)
        private set

    data class DiscoverDetailUi(
        val loading: Boolean = false,
        val playlist: DiscoverPlaylistDto? = null,
        val tracks: List<Track> = emptyList()
    )
    var serverUrl by mutableStateOf(SettingsRepository.baseUrl())
        private set
    var selectedSearchSource by mutableStateOf(SourceCatalog.AUTO)
        private set
    var currentPlaySource by mutableStateOf<String?>(null)
        private set
    var showNowPlaying by mutableStateOf(false)
        private set
    var nowPlayingLyrics by mutableStateOf<LyricsUiState>(LyricsUiState.None)
        private set

    sealed interface LyricsUiState {
        data object Loading : LyricsUiState
        data class Available(val lines: List<com.example.helloworld.data.remote.LyricsLine>) : LyricsUiState
        data object None : LyricsUiState
    }

    private val lyricsCache = mutableMapOf<String, LyricsUiState>()

    val serverTracks: List<Track>
        get() = (libraryState.items + searchState.items).distinctBy { it.id }

    private var queueById: Map<String, Track> = emptyMap()

    /** 播放计数：每首每次播放只上报一回（进度 ≥80% 时） */
    private var playReportedFor: String? = null

    init {
        SettingsRepository.init(application)
        AuthRepository.init(application)
        // 冷启动把上次的队列装回播放器，否则播放条有歌但队列是空的、怎么点都没反应
        restorePlaybackState()
        viewModelScope.launch {
            playerController.isPlaying.collect {
                isPlaying = it
                // 回传悬浮球：环形菜单中心按钮据此显示播放/暂停
                com.example.helloworld.bubble.BubbleBus.isPlaying = it
            }
        }
        viewModelScope.launch {
            playerController.isBuffering.collect { isBuffering = it }
        }
        // 悬浮球环形菜单：同步「当前歌是否已收藏 / 是否真的在放歌」
        viewModelScope.launch {
            favoriteTracks.collect { favs ->
                val cur = currentTrack
                com.example.helloworld.bubble.BubbleBus.isFavorited =
                    cur.id != PlaceholderTrack.id && favs.any { it.id == cur.id }
            }
        }
        viewModelScope.launch {
            playerController.currentMediaId.collect { mediaId ->
                com.example.helloworld.bubble.BubbleBus.hasTrack = mediaId != null
            }
        }
        viewModelScope.launch {
            playerController.positionMs.collect { pos ->
                playbackPositionMs = pos
                maybeReportPlay(pos)
            }
        }
        viewModelScope.launch {
            playerController.durationMs.collect { playbackDurationMs = it }
        }
        viewModelScope.launch {
            playerController.playbackError.collect { playbackError = it }
        }
        viewModelScope.launch {
            playerController.currentMediaId.collect { mediaId ->
                if (mediaId == null) return@collect
                // 「播完当前首」的定时关闭：切歌即暂停
                sleepTimer.onMediaChanged(mediaId)
                findTrack(mediaId)?.let {
                    currentTrack = it
                    // 切歌后立刻刷新悬浮球收藏态（否则红心会停在上首歌的状态）
                    com.example.helloworld.bubble.BubbleBus.isFavorited =
                        favoriteTracks.value.any { fav -> fav.id == it.id }
                    prefetchLyrics(it)
                    // 一起听：自动切到下一首时立即广播（对方不等 3 秒轮询）
                    if (listenHosting && !applyingListenState) {
                        listenRev++
                        reportListenStateNow()
                    }
                }
                currentPlaySource = TrackPayloadStore.playedSource(mediaId)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val token = AuthRepository.token()
            if (token.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    authState = AuthState.LoggedOut
                }
            } else {
                try {
                    val me = api.me("Bearer $token")
                    withContext(Dispatchers.Main) {
                        authState = AuthState.LoggedIn(me.username)
                        meAvatar = me.avatar
                    }
                    repository.ensureDefaultPlaylist()
                    pullSync()
                    refreshLibrary()
                } catch (e: Exception) {
                    YinheLogger.log("账号", "令牌失效：${(e.message ?: "").take(50)}")
                    AuthRepository.clear()
                    withContext(Dispatchers.Main) { authState = AuthState.LoggedOut }
                }
            }
        }
    }

    fun login(username: String, password: String) = doAuth(username, password, register = false)
    fun register(username: String, password: String) = doAuth(username, password, register = true)

    private fun doAuth(username: String, password: String, register: Boolean) {
        if (username.isBlank() || password.isBlank()) {
            authError = "账号和密码不能为空"
            return
        }
        if (authBusy) return
        authBusy = true
        authError = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resp = if (register) {
                    api.register(AuthRequest(username.trim(), password))
                } else {
                    api.login(AuthRequest(username.trim(), password))
                }
                AuthRepository.save(resp.token, resp.username)
                withContext(Dispatchers.Main) {
                    authState = AuthState.LoggedIn(resp.username)
                    YinheLogger.log("账号", "${if (register) "注册" else "登录"}成功：${resp.username}")
                }
                YinheImClient.connect(resp.token)
                repository.ensureDefaultPlaylist()
                pullSync()
                refreshLibrary()
                fetchAnnouncement()
            } catch (e: Exception) {
                val msg = friendlyAuthError(e, register)
                withContext(Dispatchers.Main) { authError = msg }
            } finally {
                withContext(Dispatchers.Main) { authBusy = false }
            }
        }
    }

    fun logout() {
        YinheImClient.disconnect()
        stopFollowListen()
        listenHosting = false
        AuthRepository.clear()
        authState = AuthState.LoggedOut
        YinheLogger.log("账号", "已退出登录（本地缓存保留，重新登录后会与云端合并）")
    }

    private fun friendlyAuthError(e: Exception, register: Boolean): String {
        val detail = (e as? retrofit2.HttpException)
            ?.response()?.errorBody()?.string()
            ?.let { body ->
                runCatching {
                    com.google.gson.JsonParser.parseString(body).asJsonObject.get("detail")?.asString
                }.getOrNull()
            }
        return detail ?: e.message ?: (if (register) "注册失败" else "登录失败")
    }

    /** 登录后从服务器拉全量覆盖本地（强制登录模型：服务器是唯一事实源） */
    private suspend fun pullSync() {
        val token = AuthRepository.token() ?: return
        try {
            val data = api.getMyLibrary("Bearer $token")
            repository.replaceAll(
                data.playlists.map {
                    PlaylistRepository.PlaylistPayloadLite(
                        id = it.id,
                        name = it.name,
                        description = it.description,
                        createdAt = it.createdAt,
                        tracks = it.tracks
                    )
                }
            )
            favoritesRepository.replaceAllFromApi(data.favorites)
            withContext(Dispatchers.Main) {
                syncMessage = "已从云端同步 ${data.playlists.size} 个歌单 / ${data.favorites.size} 首收藏"
            }
            YinheLogger.log("同步", "拉取：${data.playlists.size} 歌单 / ${data.favorites.size} 收藏")
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                syncMessage = "同步失败：${(e.message ?: "未知错误").take(40)}"
            }
            YinheLogger.record("netease", ok = false, detail = "pullSync ${(e.message ?: "").take(30)}")
        }
    }

    /** 变更后防抖推送全量快照 */
    private fun schedulePush() {
        if (authState !is AuthState.LoggedIn) return
        pushJob?.cancel()
        pushJob = viewModelScope.launch(Dispatchers.IO) {
            delay(600)
            val token = AuthRepository.token() ?: return@launch
            try {
                val (playlistSnapshots, _) = repository.exportSnapshot()
                val favorites = favoritesRepository.exportAll()
                val payload = UserLibraryRequest(
                    playlists = playlistSnapshots.map {
                        PlaylistPayload(it.id, it.name, it.description, it.createdAt, it.tracks)
                    },
                    favorites = favorites
                )
                api.putMyLibrary("Bearer $token", payload)
                YinheLogger.log("同步", "推送：${payload.playlists.size} 歌单 / ${payload.favorites.size} 收藏")
            } catch (e: Exception) {
                YinheLogger.log("同步", "推送失败：${(e.message ?: "").take(50)}")
            }
        }
    }

    override fun onCleared() {
        playerController.release()
        super.onCleared()
    }

    fun selectTab(index: Int) {
        selectedTab = index
        selectedPlaylistId = null
        if (index == 1) ensureDiscoverLoaded()
        if (index != 2) petFullscreen = false
    }

    fun openPlaylist(id: String) {
        selectedTab = 3
        selectedPlaylistId = id
    }

    // ===================== 发现页 =====================

    private var discoverInited = false

    /** 歌单墙代次：切分类后旧请求的迟到结果直接丢弃 */
    private var discoverWallGeneration = 0

    fun ensureDiscoverLoaded() {
        if (discoverInited) return
        discoverInited = true
        loadDiscoverCategories()
        loadDiscoverToplists()
        loadDiscoverWall()
    }

    private fun loadDiscoverCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            val cats = runCatching { api.discoverCategories().categories }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                if (discoverCategories.isEmpty() && cats.isNotEmpty()) discoverCategories = cats
            }
        }
    }

    private fun loadDiscoverToplists() {
        discoverToplists = discoverToplists.copy(loading = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = api.discoverToplists().items
                    .filter { it.id != 0L && it.name.isNotBlank() }
                    .distinctBy { it.id }
                withContext(Dispatchers.Main) {
                    discoverToplists = AsyncListState(items = items)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    discoverToplists = discoverToplists.copy(loading = false, error = e.message ?: "榜单加载失败")
                }
            }
        }
    }

    fun selectDiscoverCat(id: String) {
        if (discoverCat == id) return
        discoverCat = id
        discoverDetail = null
        loadDiscoverWall()
    }

    private fun loadDiscoverWall() {
        discoverWallGeneration++
        val gen = discoverWallGeneration
        discoverWall = AsyncListState(loading = true, items = emptyList(), error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = api.discoverPlaylists(cat = discoverCat).items
                    .filter { it.id != 0L && it.name.isNotBlank() }
                    .distinctBy { it.id }
                withContext(Dispatchers.Main) {
                    if (gen == discoverWallGeneration) discoverWall = AsyncListState(items = items)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (gen == discoverWallGeneration) {
                        discoverWall = discoverWall.copy(loading = false, error = e.message ?: "歌单加载失败")
                    }
                }
            }
        }
    }

    /** 失败重试入口（加载失败时按钮调用） */
    fun refreshDiscoverWall() = loadDiscoverWall()

    fun openDiscoverPlaylist(id: Long) {
        discoverDetail = DiscoverDetailUi(loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.discoverPlaylist(id)
                val tracks = response.tracks.map { it.toUiTrack() }.distinctBy { it.id }
                withContext(Dispatchers.Main) {
                    discoverDetail = DiscoverDetailUi(playlist = response.playlist, tracks = tracks)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    discoverDetail = null
                    discoverWall = discoverWall.copy(error = e.message ?: "歌单打开失败")
                }
            }
        }
    }

    fun closeDiscoverDetail() {
        discoverDetail = null
    }

    /** 私人 FM：拉一首随机推荐，直接进详情态并开播 */
    fun playDiscoverFm() {
        discoverDetail = DiscoverDetailUi(loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tracks = api.discoverFm().tracks.map { it.toUiTrack() }
                withContext(Dispatchers.Main) {
                    if (tracks.isEmpty()) {
                        discoverDetail = null
                        discoverWall = discoverWall.copy(error = "私人 FM 暂无返回")
                        return@withContext
                    }
                    discoverDetail = DiscoverDetailUi(
                        playlist = DiscoverPlaylistDto(name = "私人 FM", description = "来自网易云的随机推荐"),
                        tracks = tracks
                    )
                    playTrack(tracks.first(), tracks)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    discoverDetail = null
                    discoverWall = discoverWall.copy(error = e.message ?: "私人 FM 暂时不可用")
                }
            }
        }
    }

    fun playDiscoverAll() {
        val detail = discoverDetail ?: return
        if (detail.tracks.isEmpty()) return
        playTrack(detail.tracks.first(), detail.tracks)
    }

    // ===================== 听歌识曲（麦克风 / 系统内录） =====================

    var showRecognize by mutableStateOf(false)
        private set
    /** idle | recording | uploading | done */
    var recognizePhase by mutableStateOf("idle")
        private set
    var recognizeProgress by mutableStateOf(0f)
        private set
    var recognizeResults by mutableStateOf<List<Track>>(emptyList())
        private set
    var recognizeError by mutableStateOf<String?>(null)
        private set

    /** 悬浮球/外部入口带入的识曲模式（mic/system），RecognizeScreen 消费后自动开录 */
    var pendingRecognizeMode by mutableStateOf<String?>(null)
        private set

    private var recognizeGen = 0

    fun openRecognize(mode: String? = null) {
        recognizeGen++
        recognizePhase = "idle"
        recognizeProgress = 0f
        recognizeResults = emptyList()
        recognizeError = null
        pendingRecognizeMode = mode
        showRecognize = true
    }

    fun closeRecognize() {
        recognizeGen++
        showRecognize = false
        recognizePhase = "idle"
        recognizeProgress = 0f
    }

    fun consumeRecognizeMode(): String? {
        val mode = pendingRecognizeMode
        pendingRecognizeMode = null
        return mode
    }

    fun resetRecognize() {
        recognizeGen++
        recognizePhase = "idle"
        recognizeProgress = 0f
        recognizeResults = emptyList()
        recognizeError = null
    }

    /** 麦克风识曲（调用方需已获得 RECORD_AUDIO 权限） */
    fun startMicRecognize() {
        val gen = ++recognizeGen
        recognizePhase = "recording"
        recognizeProgress = 0f
        recognizeResults = emptyList()
        recognizeError = null
        viewModelScope.launch {
            val wav = withContext(Dispatchers.IO) {
                AudioCaptures.recordMic { p ->
                    viewModelScope.launch(Dispatchers.Main) {
                        if (gen == recognizeGen) recognizeProgress = p
                    }
                }
            }
            if (gen != recognizeGen) return@launch
            if (wav == null) {
                withContext(Dispatchers.Main) {
                    recognizePhase = "done"
                    recognizeError = "录音失败，请确认麦克风权限"
                }
                return@launch
            }
            submitRecognize(wav, gen)
        }
    }

    /**
     * 系统内录识曲（data = 屏幕捕获授权返回的 Intent）。
     *
     * Android 的「系统内录」必须经 MediaProjection（AudioPlaybackCapture 的唯一构造入口），
     * 而 Android 14+ 把 token 收得很紧：会话一旦被停止/回收，同一个 token 永久失效。
     * 实测在小米/Redmi 上缓存复用必然拿到 null，所以改成**每次识别都重新授权 + 用完即释放**，
     * 稳比省一次弹窗重要。
     */

    /** 每次都走新授权，不存在可复用的会话 */
    fun hasSystemCaptureSession(): Boolean = false

    fun startSystemRecognize(projectionData: android.content.Intent?) {
        // 诊断落盘：内录链路问题难复现，把关键节点记下来，便于事后读 shared_prefs 定位
        fun diag(msg: String) {
            com.example.helloworld.playback.YinheLogger.log("识曲", msg)
            runCatching {
                val prefs = app.getSharedPreferences("yinhe_recognize_diag", android.content.Context.MODE_PRIVATE)
                val prev = prefs.getString("log", "").orEmpty()
                val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                prefs.edit().putString("log", (prev + "\n$ts $msg").takeLast(3000)).apply()
            }
        }
        diag("startSystemRecognize data=${projectionData != null}")
        if (!AudioCaptures.canCaptureSystemAudio()) {
            recognizePhase = "done"
            recognizeError = "系统内录需要 Android 10 及以上"
            return
        }
        val manager = app.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
            as android.media.projection.MediaProjectionManager

        // 每次识别都用**本次新授权**的 data 建会话，不复用旧 token。
        // Android 14+ 对 MediaProjection token 收得很紧：会话被系统回收后同一 token 永久失效，
        // 复用反而必然失败（实测小米/Redmi 上反复返回 null）。
        val data = projectionData
        if (data == null) {
            diag("没有授权 data（应重新走 createScreenCaptureIntent）")
            recognizePhase = "done"
            recognizeError = "需要先授权系统音频捕获"
            return
        }
        val projection = runCatching {
            manager.getMediaProjection(android.app.Activity.RESULT_OK, data)
        }.onFailure {
            diag("getMediaProjection 抛异常 ${it::class.simpleName}: ${it.message}")
        }.getOrNull()
        if (projection == null) {
            diag("getMediaProjection 返回 null")
            recognizePhase = "done"
            recognizeError = "没能建立音频捕获会话，请重新授权"
            return
        }
        diag("getMediaProjection 成功")
        // Android 14+ 要求：拿到后必须**立刻**注册 Callback，否则后续 AudioRecord 会被系统拒绝
        projection.registerCallback(object : android.media.projection.MediaProjection.Callback() {
            override fun onStop() {}
        }, android.os.Handler(android.os.Looper.getMainLooper()))

        val gen = ++recognizeGen
        recognizePhase = "recording"
        recognizeProgress = 0f
        recognizeResults = emptyList()
        recognizeError = null
        val session = projection
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AudioCaptures.recordSystem(session) { p ->
                    viewModelScope.launch(Dispatchers.Main) {
                        if (gen == recognizeGen) recognizeProgress = p
                    }
                } }
            }
            // 本次会话用完即释放：token 一次性，留着也没法复用
            runCatching { session.stop() }
            if (gen != recognizeGen) return@launch
            val wav = result.getOrNull()
            if (wav == null) {
                val why = result.exceptionOrNull()?.let { "${it::class.simpleName}: ${it.message}" }
                    ?: "AudioRecord 初始化失败（设备不支持内录，或该声音被源应用禁止捕获）"
                diag("系统内录失败：$why")
                withContext(Dispatchers.Main) {
                    recognizePhase = "done"
                    recognizeError = "系统内录失败：$why"
                }
                return@launch
            }
            // 全静音检测：源应用设了 ALLOW_CAPTURE_BY_NONE 时，能录但全是 0
            if (AudioCaptures.isSilent(wav)) {
                diag("录到全静音，疑似源应用禁止捕获")
                withContext(Dispatchers.Main) {
                    recognizePhase = "done"
                    recognizeError = "录到的全是静音——可能是对方应用禁止了内录，改用麦克风识曲试试"
                }
                return@launch
            }
            diag("录音完成 ${wav.size} 字节，提交识别")
            submitRecognize(wav, gen)
        }
    }

    private suspend fun submitRecognize(wav: ByteArray, gen: Int) {
        withContext(Dispatchers.Main) {
            recognizePhase = "uploading"
            recognizeProgress = 1f
        }
        val result = runCatching { MusicApiClient.recognizeAudio(wav) }
        if (gen != recognizeGen) return
        withContext(Dispatchers.Main) {
            recognizePhase = "done"
            result.onSuccess { resp ->
                recognizeResults = resp.tracks.map { it.toUiTrack() }
                if (recognizeResults.isEmpty()) {
                    recognizeError = "没听出来这段声音，离音源近一点或换个段落再试"
                }
            }.onFailure { e ->
                recognizeError = "识别失败：${e.message ?: "网络异常"}"
            }
        }
    }

    // ===================== 悬浮球（小精灵） =====================

    /** 开关悬浮球：属性自带同签名 setter，方法必须换名，否则 JVM 签名冲突 */
    var bubbleEnabled by mutableStateOf(false)

    /**
     * 悬浮球默认常驻：登录后自动拉起（有悬浮窗权限才起，否则等用户去设置里开）。
     * 开关状态持久化，用户主动关掉就不再自动起。
     */
    fun autoStartBubble() {
        if (bubbleEnabled) return
        if (!android.provider.Settings.canDrawOverlays(app)) return
        toggleBubble(true)
    }

    /** 把当前角色的解锁动作同步给悬浮球，并让已运行的悬浮球重建引擎 */
    private fun syncBubbleUnlocked(species: String?) {
        val unlocked = species?.let { pets.value.values.firstOrNull { p -> p.species == it } }
            ?.actionUnlocks?.get(species).orEmpty().toSet()
        com.example.helloworld.bubble.BubbleBus.unlockedActions = unlocked
    }

    fun toggleBubble(enabled: Boolean) {
        if (enabled) {
            val species = pets.value.values.firstOrNull()?.species ?: "default"
            syncBubbleUnlocked(species)
            com.example.helloworld.playback.YinheLogger.log("悬浮球", "启动服务 species=$species")
            BubbleService.start(app, species)
        } else {
            com.example.helloworld.playback.YinheLogger.log("悬浮球", "停止服务")
            BubbleService.stop(app)
        }
        bubbleEnabled = enabled
        app.getSharedPreferences("yinhe_bubble", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", enabled).apply()
    }

    /** 悬浮球菜单命令（YinHeApp 收集后转发到这里） */
    fun onBubbleCommand(command: BubbleBus.Command) {
        when (command) {
            BubbleBus.Command.PlayPause -> togglePlayPause()
            BubbleBus.Command.Next -> seekToNext()
            BubbleBus.Command.Previous -> seekToPrevious()
            BubbleBus.Command.RecognizeMic -> openRecognize("mic")
            BubbleBus.Command.RecognizeSystem -> openRecognize("system")
            BubbleBus.Command.Favorite -> {
                val track = currentTrack
                if (track.id != PlaceholderTrack.id) toggleFavorite(track)
            }
            // 进入软件：悬浮球直接拉起 MainActivity 即可，这里无需额外动作
            BubbleBus.Command.OpenApp -> Unit
            BubbleBus.Command.Close -> toggleBubble(false)
        }
    }

    fun closePlaylist() {
        selectedPlaylistId = null
    }

    fun refreshLibrary() {
        libraryState = libraryState.copy(loading = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 服务器音乐库：所有用户收藏 + 歌单去重汇总（与 Web 共用 /api/server_library）
                val response = api.getServerLibrary()
                val tracks = response.items
                    .filter { it.mediaType != "video" }
                    .map { it.toUiTrack() }
                    .distinctBy { it.id }
                withContext(Dispatchers.Main) {
                    libraryState = AsyncListState(items = tracks)
                    todayPlays = response.todayPlays
                    if (currentTrack.id == PlaceholderTrack.id && tracks.isNotEmpty()) {
                        currentTrack = tracks.first()
                    }
                    refreshHeroPick()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    libraryState = libraryState.copy(
                        loading = false,
                        error = e.message ?: "无法连接音乐服务器"
                    )
                }
            }
        }
    }

    fun selectSearchSource(source: String) {
        selectedSearchSource = source
    }

    /** 搜索代次：新搜索发起后，旧搜索的迟到结果直接丢弃，防止覆盖新结果（不刷新假象） */
    private var searchGeneration = 0

    fun search(query: String) {
        val keyword = query.trim()
        searchGeneration++
        val gen = searchGeneration
        if (keyword.length < 2) {
            searchState = AsyncListState()
            return
        }
        searchState = AsyncListState(loading = true, items = searchState.items, error = null)
        reportPetEvent("search_songs")
        if (selectedSearchSource == SourceCatalog.AUTO) {
            searchMultiSource(keyword, gen)
        } else {
            searchSingleSource(keyword, selectedSearchSource, gen)
        }
    }

    /** 自动模式：多源并行搜，按优先级轮转合并、同名去重 */
    private fun searchMultiSource(keyword: String, gen: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val sources = SourceCatalog.autoSearch
            val results = sources.map { src ->
                async {
                    src to runCatching {
                        api.search(query = keyword, source = src, limit = 10, offset = 0).tracks
                    }
                }
            }.awaitAll()

            val summary = results.joinToString(" / ") { (src, r) ->
                val name = SourceCatalog.displayName(src)
                when {
                    r.isSuccess -> {
                        YinheLogger.record(src, ok = true)
                        "$name→${r.getOrThrow().size}首"
                    }
                    else -> {
                        YinheLogger.record(src, ok = false, detail = (r.exceptionOrNull()?.message ?: "").take(40))
                        "$name→失败"
                    }
                }
            }
            YinheLogger.log("搜索", "「$keyword」$summary")

            val perSource = results.mapNotNull { (src, r) -> r.getOrNull()?.let { src to it } }
            val merged = LinkedHashMap<String, Track>()
            val maxLen = perSource.maxOfOrNull { it.second.size } ?: 0
            for (round in 0 until maxLen) {
                for ((_, list) in perSource) {
                    if (round >= list.size) continue
                    val track = list[round].toUiTrack()
                    val key = mergeKey(track)
                    if (key !in merged) merged[key] = track
                }
            }
            val items = merged.values.take(20).toList()
            withContext(Dispatchers.Main) {
                if (gen != searchGeneration) return@withContext
                searchState = if (items.isEmpty()) {
                    AsyncListState(error = "所有音源都没有结果")
                } else {
                    AsyncListState(items = items)
                }
            }
        }
    }

    private fun searchSingleSource(keyword: String, source: String, gen: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.search(query = keyword, source = source, limit = 20, offset = 0)
                YinheLogger.record(source, ok = true)
                YinheLogger.log("搜索", "「$keyword」${SourceCatalog.displayName(source)}→${response.tracks.size}首")
                withContext(Dispatchers.Main) {
                    if (gen != searchGeneration) return@withContext
                    searchState = AsyncListState(items = response.tracks.map { it.toUiTrack() })
                }
            } catch (e: Exception) {
                YinheLogger.record(source, ok = false, detail = (e.message ?: "").take(40))
                withContext(Dispatchers.Main) {
                    if (gen != searchGeneration) return@withContext
                    searchState = searchState.copy(
                        loading = false,
                        error = "${SourceCatalog.displayName(source)} 搜索失败：${e.message ?: "未知错误"}"
                    )
                }
            }
        }
    }

    private fun mergeKey(track: Track): String {
        fun norm(s: String) = s.lowercase().replace(Regex("[\\s\\p{P}\\p{S}]+"), "")
        val artist = norm(track.artist).let { a -> a.split(" ").firstOrNull() ?: a }
        return norm(track.title) + "|" + artist
    }

    /** 首开需服务端下载的慢音源（googlevideo/SC-CDN 对国内直连不友好） */
    private val slowPrepareSources = setOf("youtube", "soundcloud")

    /** 慢源首开：下载进度弹窗状态（弹窗可关闭，关闭后下载继续、完成自动播） */
    var preparingTrack by mutableStateOf<Track?>(null)
        private set
    var preparingProgress by mutableStateOf(-1)
        private set
    var preparingError by mutableStateOf<String?>(null)
        private set
    var prepareDialogVisible by mutableStateOf(false)
        private set
    private var prepareJob: Job? = null
    private var pendingPlay: Pair<Track, List<Track>>? = null

    fun playTrack(track: Track, queue: List<Track>) {
        if (track.source in slowPrepareSources) {
            // 已在准备同一首：重新打开弹窗即可
            if (preparingTrack?.id == track.id && prepareJob?.isActive == true) {
                prepareDialogVisible = true
                return
            }
            startPrepare(track, queue)
            return
        }
        // 点了别的歌：取消等待中的自动播放，让位给用户的选择
        prepareJob?.cancel()
        prepareDialogVisible = false
        preparingTrack = null
        playTrackInternal(track, queue)
    }

    private fun playTrackInternal(track: Track, queue: List<Track>) {
        val playQueue = queue.ifEmpty { listOf(track) }
        val startIndex = playQueue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
        queueById = playQueue.associateBy { it.id }
        if (track.id != currentTrack.id) {
            playbackError = null
            playReportedFor = null
        }
        currentTrack = track
        playerController.playQueue(playQueue, startIndex)
        persistPlaybackState(playQueue, startIndex)
        if (listenHosting && !applyingListenState) {
            listenRev++
            reportListenStateNow()
        }
    }

    // ===================== 播放状态持久化 =====================

    /**
     * 记住"上次听的那首 + 队列"。
     *
     * 播放服务被系统回收后队列是空的，但 UI 的 currentTrack 还在内存里，
     * 于是出现「播放条显示某首歌、点播放/上一首/下一首全都没反应」——
     * 因为那些操作都作用在空队列上。重启时用这份记录把队列重建回来。
     */
    private fun persistPlaybackState(queue: List<Track>, index: Int) {
        runCatching {
            val json = com.google.gson.Gson().toJson(
                mapOf(
                    "index" to index,
                    "tracks" to queue.mapNotNull { it.apiTrack }
                )
            )
            app.getSharedPreferences("yinhe_last_playback", android.content.Context.MODE_PRIVATE)
                .edit().putString("state", json).apply()
        }
    }

    /** 冷启动恢复上次队列：队列为空时调用，让播放器重新持有可播的队列 */
    private fun restorePlaybackState() {
        val raw = app.getSharedPreferences("yinhe_last_playback", android.content.Context.MODE_PRIVATE)
            .getString("state", null) ?: return
        runCatching {
            val obj = com.google.gson.JsonParser.parseString(raw).asJsonObject
            val index = obj.get("index")?.asInt ?: 0
            val arr = obj.getAsJsonArray("tracks") ?: return
            val tracks = arr.mapNotNull { el ->
                runCatching {
                    com.google.gson.Gson().fromJson(
                        el, com.example.helloworld.data.remote.ApiTrack::class.java
                    ).toUiTrack()
                }.getOrNull()
            }
            if (tracks.isEmpty()) return
            // 只重建队列，不自动播放：用户点播放就能接上，不会再"点不动"
            val safeIndex = index.coerceIn(0, tracks.lastIndex)
            currentTrack = tracks[safeIndex]
            queueById = tracks.associateBy { it.id }
            playerController.restoreQueue(tracks, safeIndex)
        }
    }

    /** 慢源首开：提交服务端后台下载，轮询进度，完成后自动播放（api.preview 此时本地秒回） */
    private fun startPrepare(track: Track, queue: List<Track>) {
        val payload = track.apiTrack
        if (payload == null) {
            playTrackInternal(track, queue)
            return
        }
        prepareJob?.cancel()
        pendingPlay = track to queue
        preparingTrack = track
        preparingProgress = -1
        preparingError = null
        prepareDialogVisible = true
        prepareJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val submit = api.download(
                    com.example.helloworld.data.remote.DownloadRequest(payload)
                )
                val taskId = submit.taskId
                if (taskId.isNullOrBlank() || submit.status == "completed" || submit.fromCache) {
                    finishPrepare()
                    return@launch
                }
                while (isActive) {
                    delay(800)
                    val p = api.downloadProgress(taskId)
                    withContext(Dispatchers.Main) { preparingProgress = p.progress }
                    when (p.status) {
                        "completed" -> {
                            finishPrepare()
                            return@launch
                        }
                        "failed", "cancelled" -> {
                            withContext(Dispatchers.Main) { preparingError = p.error ?: "下载失败" }
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { preparingError = e.message ?: "网络错误" }
            }
        }
    }

    private suspend fun finishPrepare() {
        val pending = pendingPlay ?: return
        pendingPlay = null
        withContext(Dispatchers.Main) {
            preparingTrack = null
            prepareDialogVisible = false
            playTrackInternal(pending.first, pending.second)
        }
    }

    /** 关闭弹窗但下载继续，完成后仍会自动播放 */
    fun dismissPrepareDialog() {
        prepareDialogVisible = false
    }

    fun retryPrepare() {
        val pending = pendingPlay ?: return
        startPrepare(pending.first, pending.second)
    }

    /** 进度 ≥80% 上报一次播放（服务端记数，未来做榜单的数据基础） */
    private fun maybeReportPlay(positionMs: Long) {
        val duration = playbackDurationMs
        if (duration <= 0) return
        val track = currentTrack
        if (track.id == PlaceholderTrack.id) return
        if (positionMs < duration * 8 / 10) return
        if (playReportedFor == track.id) return
        playReportedFor = track.id
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                api.recordPlay(
                    com.example.helloworld.data.remote.PlayRecordRequest(track.id, track.apiTrack)
                )
            }.onSuccess {
                YinheLogger.log("统计", "《${track.title}》播放 +1（累计 ${it.count}）")
                reportPetEvent("play_songs")
            }
        }
    }

    private fun findTrack(trackId: String): Track? {
        queueById[trackId]?.let { return it }
        serverTracks.find { it.id == trackId }?.let { return it }
        favoriteTracks.value.find { it.id == trackId }?.let { return it }
        cachedTracks.value.find { it.id == trackId }?.let { return it }
        return null
    }

    fun togglePlayPause() {
        playerController.toggle()
        if (listenHosting && !applyingListenState) {
            listenRev++
            reportListenStateNow()
        }
    }

    fun openNowPlaying() {
        showNowPlaying = true
    }

    fun closeNowPlaying() {
        showNowPlaying = false
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
        if (listenHosting && !applyingListenState) {
            listenRev++
            reportListenStateNow()
        }
    }

    /** 歌词随播放预拉（不等到打开歌词页），内存缓存 */
    private fun prefetchLyrics(track: Track) {
        lyricsCache[track.id]?.let {
            nowPlayingLyrics = it
            return
        }
        val payload = track.apiTrack
        if (payload == null) {
            nowPlayingLyrics = LyricsUiState.None
            return
        }
        nowPlayingLyrics = LyricsUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val state = try {
                val resp = api.lyrics(com.example.helloworld.data.remote.LyricsRequest(payload))
                if (resp.hasLyrics && resp.lines.isNotEmpty()) {
                    // 保留时间轴（time 秒）与翻译，歌词跟随高亮靠它
                    val usable = resp.lines.filter { it.text.isNotBlank() }
                    if (usable.isNotEmpty()) LyricsUiState.Available(usable) else LyricsUiState.None
                } else {
                    LyricsUiState.None
                }
            } catch (e: Exception) {
                LyricsUiState.None
            }
            lyricsCache[track.id] = state
            if (currentTrack.id == track.id) {
                withContext(Dispatchers.Main) { nowPlayingLyrics = state }
            }
        }
    }

    fun cycleRepeatMode() {
        playerController.cycleRepeatMode()
    }

    fun seekToNext() {
        playerController.seekToNext()
    }

    fun seekToPrevious() {
        playerController.seekToPrevious()
    }

    fun toggleFavorite(track: Track) {
        val wasFavorite = favoriteTracks.value.any { it.id == track.id }
        viewModelScope.launch(Dispatchers.IO) {
            favoritesRepository.toggle(track)
            schedulePush()
        }
        if (!wasFavorite) reportPetEvent("like_songs")
    }

    fun updateServerUrl(url: String) {
        if (url.isBlank()) return
        SettingsRepository.saveBaseUrl(url)
        serverUrl = SettingsRepository.baseUrl()
        searchState = AsyncListState()
        refreshLibrary()
    }

    fun resetServerUrl() {
        SettingsRepository.resetBaseUrl()
        serverUrl = SettingsRepository.baseUrl()
        searchState = AsyncListState()
        refreshLibrary()
    }

    fun playlistTracks(playlistId: String): Flow<List<Track>> = repository.tracksFor(playlistId)

    /** 所有歌单的曲目 map（添加歌曲「从其他歌单选」用） */
    val playlistTracksMap = repository.allTracksMap.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )

    /** 从本地缓存（本地歌单）删除一条记录 */
    fun removeCachedTrack(trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            cacheRepository.remove(trackId)
        }
    }

    fun createPlaylist(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.createPlaylist(name, description)
            schedulePush()
        }
    }

    fun updatePlaylist(playlist: Playlist) {
        if (playlist.name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePlaylist(playlist)
            schedulePush()
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        selectedPlaylistId = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePlaylist(playlist)
            schedulePush()
        }
    }

    fun addTrack(playlistId: String, track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTrack(playlistId, track)
            schedulePush()
        }
    }

    /** 批量添加（多选导入），返回实际新增数 */
    fun addTracks(playlistId: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val added = repository.addTracks(playlistId, tracks)
            if (added > 0) schedulePush()
        }
    }

    fun removeTrack(playlistId: String, trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTrack(playlistId, trackId)
            schedulePush()
        }
    }

    fun moveTrack(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveTrack(playlistId, fromIndex, toIndex)
            schedulePush()
        }
    }

    // ===================== IM 即时通信 =====================

    private val friendDao = database.friendDao()
    private val imMessageDao = database.imMessageDao()

    val friends = friendDao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
    val unreadCounts = imMessageDao.observeUnreadCounts()
        .map { list -> list.associate { it.peer to it.cnt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** IM 导航：null=无，"friends"=好友列表，"chat:xxx"=与 xxx 聊天 */
    var imScreen by mutableStateOf<String?>(null)
        private set

    val imConnected = YinheImClient.connected

    /** 启动公告（登录后拉取；每个版本只弹一次，seen_version 持久化到本地） */
    var announcement by mutableStateOf<com.example.helloworld.data.remote.AnnouncementResponse?>(null)
        private set
    var announcementShown by mutableStateOf(false)
        private set

    private val annPrefs = application.getSharedPreferences("yinhe_announcement", android.content.Context.MODE_PRIVATE)

    fun dismissAnnouncement() {
        announcement?.let { annPrefs.edit().putString("seen_version", it.version).apply() }
        announcement = null
        announcementShown = true
    }

    private fun fetchAnnouncement() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.announcement() }
                .onSuccess { resp ->
                    val seen = annPrefs.getString("seen_version", null)
                    // 版本不同要弹；版本相同但服务端包比本机新也要弹（防"装旧包看不到公告"）
                    val shouldShow = resp.version != seen ||
                        resp.apkVersionCode > com.example.helloworld.BuildConfig.VERSION_CODE
                    YinheLogger.log(
                        "公告",
                        "拉取 v=${resp.version} code=${resp.apkVersionCode} seen=$seen 本机=${com.example.helloworld.BuildConfig.VERSION_CODE} → ${if (shouldShow) "弹" else "跳"}"
                    )
                    if (shouldShow) {
                        withContext(Dispatchers.Main) { announcement = resp }
                    }
                }
                .onFailure {
                    YinheLogger.log("公告", "拉取失败：${(it.message ?: "").take(60)}")
                }
        }
    }

    /** 设置页「检查更新」：比较服务端 apk_version_code 与本机 versionCode */
    fun checkUpdate(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching { api.announcement() }
            withContext(Dispatchers.Main) {
                r.onSuccess { resp ->
                    onResult(resp.apkVersionCode > com.example.helloworld.BuildConfig.VERSION_CODE, resp.apkUrl)
                }.onFailure { onResult(false, null) }
            }
        }
    }

    /** 设置页「提交反馈」→ 服务端 feedback.jsonl */
    fun submitFeedback(text: String, onResult: (String?) -> Unit) {
        val token = AuthRepository.token() ?: run { onResult("未登录"); return }
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching {
                api.submitFeedback(com.example.helloworld.data.remote.FeedbackRequest(text), "Bearer $token")
            }
            withContext(Dispatchers.Main) {
                r.onSuccess { onResult(null) }.onFailure { onResult(it.message ?: "提交失败") }
            }
        }
    }

    /** 「上报日志」：把 YinheLogger 最近 300 行发给开发者 */
    fun uploadLogs(onResult: (String?) -> Unit) {
        val token = AuthRepository.token() ?: run { onResult("未登录"); return }
        val lines = YinheLogger.lines.value + YinheLogger.stats.value.entries.map { (src, s) ->
            "统计|${YinheLogger.display(src)} 成功率${s.successRate}% (${s.ok}/${s.attempts})"
        }
        if (lines.isEmpty()) {
            onResult("暂无日志可以上报")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching {
                api.uploadLogs(com.example.helloworld.data.remote.LogUploadRequest(lines), "Bearer $token")
            }
            withContext(Dispatchers.Main) {
                r.onSuccess { onResult(null) }.onFailure { onResult(it.message ?: "上传失败") }
            }
        }
    }

    init {
        // 登录后连接 IM：收消息/在线状态，断线重连后补拉好友与信箱
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token()
            if (!t.isNullOrBlank()) {
                YinheImClient.connect(t)
                refreshFriends()
                pullInbox()
            }
            fetchAnnouncement()
            refreshAvatarPresets()
            refreshPets()
        }
        viewModelScope.launch {
            YinheImClient.incoming.collect { msg ->
                when (msg.type) {
                    // 一起听控制信令：即时事件，不落聊天记录
                    "listen_accept" -> onListenAccept(msg.from)
                    "listen_leave" -> onListenLeave(msg.from)
                    // 宠物信令：accept 即时反馈 + 刷新；action 触发聊天页动画
                    "pet_accept" -> {
                        listenFeedback = "${msg.from} 同意了领养邀请"
                        refreshPet(msg.from)
                    }
                    "pet_action" -> _petActions.tryEmit(PetActionEvent(msg.from, msg.action ?: "idle"))
                    "chat", "share_track", "listen_invite", "share_playlist", "pet_invite" -> {
                        // 歌单分享：note 存分享码，放 trackJson 给接收方拉取
                        val extra = if (msg.type == "share_playlist") (msg.note ?: "") else ""
                        imMessageDao.insert(
                            com.example.helloworld.data.local.ImMessageEntity(
                                peer = msg.from,
                                outgoing = false,
                                msgType = msg.type,
                                text = msg.text ?: if (msg.type == "share_playlist") "" else (msg.note ?: ""),
                                trackJson = msg.track?.let { com.google.gson.Gson().toJson(it) } ?: extra,
                                ts = msg.ts
                            )
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            YinheImClient.presence.collect { map ->
                map.forEach { (user, online) -> friendDao.setOnline(user, online) }
            }
        }
        viewModelScope.launch {
            // 重连成功后补拉（好友在线状态 + 离线信箱）
            var was = false
            YinheImClient.connected.collect { connected ->
                if (connected && !was) {
                    refreshFriends()
                    pullInbox()
                }
                was = connected
            }
        }
    }

    fun messagesFor(peer: String) = imMessageDao.observeFor(peer)

    // ===================== 聊天页 @小精灵 =====================

    /** 正在等精灵回复的会话（peer）；非空时聊天页显示"精灵正在想…" */
    var spriteThinkingFor by mutableStateOf<String?>(null)
        private set

    /**
     * 在聊天页 @精灵：把最近聊天记录拼成上下文，调 /sprite/chat 流式拿回复。
     *
     * - 精灵知道自己在谁的聊天里、两人分别是谁（区分用户靠消息前缀「名字: 内容」）
     * - 精灵下发的 player_command 直接落到本机播放器
     * - 回复存成一条 msgType="sprite" 的本地消息（不发给对方）
     */
    fun askSprite(peer: String, question: String, history: List<ImMessageEntity>) {
        val me = myName() ?: AuthRepository.username() ?: "我"
        val petName = pets.value[peer]?.name?.takeIf { it.isNotBlank() } ?: "小河灵"
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { spriteThinkingFor = peer }

            // 上下文：最近 12 条纯文字聊天，标明谁说的
            val recent = history
                .filter { it.msgType == "chat" && it.text.isNotBlank() }
                .takeLast(12)
                .joinToString("\n") { msg ->
                    val who = if (msg.outgoing) me else peer
                    "$who: ${msg.text}"
                }

            val prompt = buildString {
                append("[系统提示] 你在 $me 和 $peer 的聊天里，你是两人共养的小精灵「$petName」，")
                append("要分别认得出这两个人。下面是最近的聊天记录：\n")
                if (recent.isBlank()) append("（还没有聊天记录）\n") else append(recent).append("\n")
                append("[系统提示] 现在 $me @了你，说：")
                append(question)
            }

            val player = mapOf(
                "current_track" to currentTrack.takeIf { it.id != PlaceholderTrack.id }?.let {
                    mapOf("title" to it.title, "artist" to it.artist)
                },
                "is_playing" to isPlaying
            )

            var reply = ""
            try {
                MusicApiClient.spriteChat(
                    message = prompt,
                    sessionId = "im_${peer}",
                    player = player
                ) { event ->
                    when (event.type) {
                        "reply" -> reply = event.text
                        // 精灵要求识曲：拉起识曲页并自动开录
                        "recognize" -> {
                            val source = event.command?.optString("source").orEmpty()
                            openRecognize(source.ifBlank { "system" })
                        }
                        "player_command" -> handleSpriteCommand(event.command)
                        "error" -> if (reply.isBlank()) reply = event.message.ifBlank { "精灵走神了…" }
                    }
                }
            } catch (e: Exception) {
                reply = "精灵掉线了：${e.message?.take(60) ?: "网络不太好"}"
            }

            if (reply.isNotBlank()) {
                imMessageDao.insert(
                    ImMessageEntity(
                        peer = peer,
                        outgoing = false,
                        msgType = "sprite",
                        text = reply,
                        ts = System.currentTimeMillis()
                    )
                )
            }
            withContext(Dispatchers.Main) { spriteThinkingFor = null }
        }
    }

    /** 精灵下发的播放指令 → 本机播放器 */
    private fun handleSpriteCommand(command: org.json.JSONObject?) {
        command ?: return
        val action = command.optString("action")
        when (action) {
            "play" -> {
                val arr = command.optJSONArray("tracks") ?: return
                val tracks = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val apiTrack = runCatching {
                            com.google.gson.Gson().fromJson(
                                obj.toString(),
                                com.example.helloworld.data.remote.ApiTrack::class.java
                            )
                        }.getOrNull() ?: continue
                        add(apiTrack.toUiTrack())
                    }
                }
                if (tracks.isNotEmpty()) {
                    viewModelScope.launch(Dispatchers.Main) {
                        playTrack(tracks.first(), tracks)
                    }
                }
            }
            "pause" -> viewModelScope.launch(Dispatchers.Main) { playerController.pause() }
            "resume" -> viewModelScope.launch(Dispatchers.Main) { playerController.play() }
            "next" -> seekToNext()
            "prev" -> seekToPrevious()
        }
    }

    fun refreshFriends() {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching {
                api.friends("Bearer $t").friends.map {
                    com.example.helloworld.data.local.FriendEntity(
                        username = it.username,
                        since = System.currentTimeMillis(),
                        online = it.online || (YinheImClient.presence.value[it.username] == true)
                    )
                }
            }.onSuccess { friendDao.upsertAll(it) }
        }
    }

    private fun pullInbox() {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.inbox("Bearer $t") }.onSuccess { resp ->
                resp.messages.forEach { msg ->
                    when (msg.type) {
                        "listen_accept" -> onListenAccept(msg.from)
                        "listen_leave" -> onListenLeave(msg.from)
                        "pet_accept" -> {
                            listenFeedback = "${msg.from} 同意了领养邀请"
                            refreshPet(msg.from)
                        }
                        // 宠物动作是实时互动，离线补拉不追放
                        "pet_action" -> Unit
                        else -> {
                            val extra = if (msg.type == "share_playlist") (msg.note ?: "") else ""
                            imMessageDao.insert(
                                com.example.helloworld.data.local.ImMessageEntity(
                                    peer = msg.from,
                                    outgoing = false,
                                    msgType = msg.type,
                                    text = msg.text ?: if (msg.type == "share_playlist") "" else (msg.note ?: ""),
                                    trackJson = msg.track?.let { com.google.gson.Gson().toJson(it) } ?: extra,
                                    ts = msg.ts
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun lookupUsers(q: String, onResult: (List<String>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            val result = runCatching { api.lookup(q, "Bearer $t").users }.getOrDefault(emptyList())
            onResult(result)
        }
    }

    fun addFriend(username: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.addFriend("Bearer $t", FriendRequest(username)) }
                .onSuccess {
                    refreshFriends()
                    onDone(true, "已添加 $username")
                }
                .onFailure { onDone(false, friendlyAuthError(it as Exception, register = false)) }
        }
    }

    fun removeFriend(username: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.removeFriend(username, "Bearer $t") }
            friendDao.delete(username)
            refreshFriends()
        }
    }

    fun openChat(peer: String) {
        imScreen = "chat:$peer"
        viewModelScope.launch(Dispatchers.IO) { imMessageDao.markRead(peer) }
        refreshPet(peer)
    }

    fun openFriends() {
        imScreen = "friends"
        refreshFriends()
    }

    fun closeIm() {
        imScreen = null
    }

    fun sendChat(peer: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            imMessageDao.insert(
                com.example.helloworld.data.local.ImMessageEntity(
                    peer = peer,
                    outgoing = true,
                    msgType = "chat",
                    text = text.trim(),
                    ts = System.currentTimeMillis()
                )
            )
            YinheImClient.sendChat(peer, text.trim())
            reportPetEvent("chat_msgs")
        }
    }

    fun shareTrackTo(peer: String, track: Track) {
        val payload = track.apiTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            imMessageDao.insert(
                com.example.helloworld.data.local.ImMessageEntity(
                    peer = peer,
                    outgoing = true,
                    msgType = "share_track",
                    trackJson = com.google.gson.Gson().toJson(payload),
                    ts = System.currentTimeMillis()
                )
            )
            YinheImClient.sendTrack(peer, payload)
            reportPetEvent("share_tracks")
        }
    }

    /** 分享整个歌单给好友：服务端生成短码，IM 发送，对方点"添加"整单入裤 */
    fun sharePlaylistTo(peer: String, playlistId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = playlists.value.find { it.id == playlistId }?.name ?: "歌单"
            val tracks = repository.tracksFor(playlistId).first()
            if (tracks.isEmpty()) {
                withContext(Dispatchers.Main) { onResult("歌单是空的") }
                return@launch
            }
            val payload = mapOf(
                "type" to "playlist",
                "playlist" to mapOf(
                    "name" to name,
                    "tracks" to tracks.mapNotNull { it.apiTrack }
                )
            )
            val r = runCatching { api.createShare(payload) }
            val code = r.getOrNull()?.code
            if (code.isNullOrBlank()) {
                withContext(Dispatchers.Main) { onResult(r.exceptionOrNull()?.message ?: "分享失败") }
                return@launch
            }
            imMessageDao.insert(
                com.example.helloworld.data.local.ImMessageEntity(
                    peer = peer,
                    outgoing = true,
                    msgType = "share_playlist",
                    text = name,
                    trackJson = code,
                    ts = System.currentTimeMillis()
                )
            )
            YinheImClient.sendPlaylistShare(peer, code)
            withContext(Dispatchers.Main) { onResult(null) }
        }
    }

    /** 接收方：按分享码拉取歌单并整单添加到自己的歌单 */
    fun addSharedPlaylist(code: String, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching { api.getShare(code) }
            val payload = r.getOrNull()?.playlist
            if (payload == null || payload.tracks.isEmpty()) {
                withContext(Dispatchers.Main) { onResult(r.exceptionOrNull()?.message ?: "分享已过期") }
                return@launch
            }
            val newId = repository.createPlaylist(payload.name, "来自好友分享")
            repository.addTracks(newId, payload.tracks.map { it.toUiTrack() })
            withContext(Dispatchers.Main) { onResult(null) }
        }
    }

    // ===================== 一起听（双向平等：双方都广播都跟随，rev 防回声循环） =====================

    /** 我正在广播播放状态 */
    var listenHosting by mutableStateOf(false)
        private set

    /** 我的同步对象 */
    var listenPartner by mutableStateOf<String?>(null)
        private set

    /** 一起听事件反馈（对方加入/退出/邀请过期/来晚），YinHeApp 底部横幅展示后自动消 */
    var listenFeedback by mutableStateOf<String?>(null)
        private set

    fun clearListenFeedback() {
        listenFeedback = null
    }

    /** 通用底部横幅提示（宠物等非一起听场景复用 listenFeedback 横幅） */
    fun notifyHint(msg: String) {
        listenFeedback = msg
    }

    private var listenFollowJob: Job? = null
    private var listenInviteJob: Job? = null

    /** 周期上报位置/播放态（每 5s）：跟随方靠它纠漂，rev 不变不会触发对方全量应用 */
    private var listenReportJob: Job? = null

    private fun startListenReporter() {
        listenReportJob?.cancel()
        listenReportJob = viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(5_000)
                if (listenHosting && listenPartner != null) reportListenStateNow()
            }
        }
    }

    /** 切歌后的保护期截止戳：期内只纠进度不动播放状态 */
    private var listenSwitchGuardUntil: Long = 0L

    /** 纠漂冷却截止戳：一次纠漂后等 seek 落地，避免双方互按旧快照反复回拉 */
    private var driftCooldownUntil: Long = 0L

    /** 本地播放状态版本：我的播放操作/自动切歌时递增并广播；对方只在 rev 变化时全量应用 */
    private var listenRev: Long = 0

    /** 上次应用的对方状态版本（相同则只做进度纠漂） */
    private var lastAppliedPeerRev: Long = -1

    /** 邀请方标志：rev 相同时以邀请方为领导（领导永不跟随，杜绝双方互纠） */
    private var listenAmInviter: Boolean = false

    /** 一起听会话开始时刻：退出时按分钟上报"一起听"任务进度 */
    private var listenTogetherSince: Long = 0L

    private fun reportListenTogetherMinutes() {
        val since = listenTogetherSince
        listenTogetherSince = 0L
        if (since <= 0L) return
        val minutes = ((System.currentTimeMillis() - since) / 60_000).toInt()
        if (minutes >= 1) reportPetEvent("listen_together", minutes)
    }

    /** 应用对方状态期间置 true：playTrackInternal/toggle/seek 副作用触发的上报全部跳过 */
    private var applyingListenState: Boolean = false

    private fun myName(): String? = (authState as? AuthState.LoggedIn)?.username

    fun startListenHost() {
        listenHosting = true
        lastAppliedPeerRev = -1
        startListenReporter()
        reportListenStateNow()
    }

    fun inviteListen(peer: String) {
        startListenHost()
        listenAmInviter = true
        viewModelScope.launch(Dispatchers.IO) {
            imMessageDao.insert(
                com.example.helloworld.data.local.ImMessageEntity(
                    peer = peer,
                    outgoing = true,
                    msgType = "listen_invite",
                    text = "邀请你一起听",
                    ts = System.currentTimeMillis()
                )
            )
            YinheImClient.sendListenInvite(peer)
        }
        // 邀请 1 分钟时效：无人加入自动撤销
        listenInviteJob?.cancel()
        listenInviteJob = viewModelScope.launch {
            delay(60_000)
            if (listenHosting && listenPartner == null) {
                stopListenHost()
                listenFeedback = "邀请已过期，无人加入"
            }
        }
    }

    /** 对方接受邀请（listen_accept 到达）：只认第一个加入者；邀请者也跟随对方（双向平等） */
    private fun onListenAccept(peer: String) {
        if (!listenHosting) return
        if (listenPartner == null) {
            listenPartner = peer
            listenInviteJob?.cancel()
            listenFeedback = "$peer 已加入，现在双方都可以切歌"
            listenRev++
            listenTogetherSince = System.currentTimeMillis()
            reportListenStateNow()
            // 邀请者开始跟随对方；首次只登记基线，保留自己当前的播放
            followPeerLoop(peer, applyFirst = false)
        }
    }

    /** 对方明确退出（listen_leave 到达） */
    private fun onListenLeave(peer: String) {
        if (listenPartner != null) listenFeedback = "$peer 已退出一起听"
        exitListenMode()
    }

    /** 我主动解散：清空状态广播 + 通知对方，两边同步退出 */
    fun stopListenHost() {
        reportListenTogetherMinutes()
        val peer = listenPartner
        val wasHosting = listenHosting
        listenHosting = false
        listenPartner = null
        listenInviteJob?.cancel()
        listenFollowJob?.cancel()
        listenFollowJob = null
        listenReportJob?.cancel()
        listenReportJob = null
        if (!wasHosting) return
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching {
                api.reportListenState(
                    "Bearer $t",
                    com.example.helloworld.data.remote.ListenStateRequest(null, 0, false, ++listenRev, null)
                )
            }
            if (peer != null) YinheImClient.sendListenLeave(peer)
        }
    }

    fun stopFollowListen() = stopListenHost()

    /** 被动退出（对方退出/来晚/状态清空）：清本地广播，不再发 leave */
    private fun exitListenMode() {
        reportListenTogetherMinutes()
        listenFollowJob?.cancel()
        listenFollowJob = null
        listenInviteJob?.cancel()
        listenReportJob?.cancel()
        listenReportJob = null
        val wasHosting = listenHosting
        listenHosting = false
        listenPartner = null
        if (wasHosting) {
            viewModelScope.launch(Dispatchers.IO) {
                val t = AuthRepository.token() ?: return@launch
                runCatching {
                    api.reportListenState(
                        "Bearer $t",
                        com.example.helloworld.data.remote.ListenStateRequest(null, 0, false, ++listenRev, null)
                    )
                }
            }
        }
    }

    /** 接受邀请：1 分钟时效校验；接受后双方互相广播（权利平等） */
    fun followListen(peer: String, inviteTs: Long = System.currentTimeMillis()) {
        if (System.currentTimeMillis() - inviteTs > 60_000) {
            listenFeedback = "邀请已过期，请让对方重新邀请"
            return
        }
        listenFollowJob?.cancel()
        listenPartner = peer
        listenAmInviter = false
        listenTogetherSince = System.currentTimeMillis()
        startListenHost()
        viewModelScope.launch(Dispatchers.IO) { YinheImClient.sendListenAccept(peer) }
        // 被邀请方首次轮询全量应用对方状态（以邀请者播放为准）
        followPeerLoop(peer, applyFirst = true)
    }

    /**
     * 轮询跟随对方播放状态。applyFirst=true 时首次全量应用（被邀请方加入，听邀请者的）；
     * applyFirst=false 时首次只登记版本基线（邀请者接纳对方，保留自己当前播放），之后双方平等。
     */
    private fun followPeerLoop(peer: String, applyFirst: Boolean) {
        listenFollowJob?.cancel()
        var first = applyFirst
        listenFollowJob = viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            while (isActive) {
                runCatching { api.getListenState(peer, "Bearer $t") }.onSuccess { state ->
                    if (!state.active || state.track == null) {
                        withContext(Dispatchers.Main) {
                            if (listenPartner != null) listenFeedback = "$peer 已退出一起听"
                            exitListenMode()
                        }
                        return@launch
                    }
                    // 一个邀请只认一人：对方已与其他人同听 → 来晚失效
                    if (state.partner != null && state.partner != myName()) {
                        withContext(Dispatchers.Main) {
                            listenFeedback = "来晚了，对方已在和其他人一起听"
                            exitListenMode()
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        val now = System.currentTimeMillis()
                        if (first) {
                            first = false
                            listenSwitchGuardUntil = now + 3_500
                            if (applyFirst) {
                                // 被邀请方加入：首次全量应用对方状态（听邀请者正在播的）
                                lastAppliedPeerRev = state.rev
                                applyPeerState(state)
                            } else {
                                // 邀请方接纳：只登记基线，保留自己当前播放
                                lastAppliedPeerRev = state.rev
                            }
                            return@withContext
                        }
                        // 领导选举：rev 高的一方为领导；平局时邀请方为领导。
                        // 领导永不向跟随者纠漂/应用，跟随者单向对齐——从根上消除双方互按旧快照互相追赶。
                        val iAmLeader = state.rev > listenRev || (state.rev == listenRev && listenAmInviter)
                        if (iAmLeader) return@withContext
                        if (state.rev != lastAppliedPeerRev) {
                            lastAppliedPeerRev = state.rev
                            applyPeerState(state)
                            listenSwitchGuardUntil = now + 3_500
                        } else if (now >= listenSwitchGuardUntil && preparingTrack == null && now >= driftCooldownUntil) {
                            // 同版本：只做宽松进度纠漂（阈值放大到 6s，冷却 8s）
                            val sameTrack = state.track?.toUiTrack()?.id == currentTrack.id
                            if (sameTrack && state.isPlaying && isPlaying) {
                                // 补偿不用本地时钟对表（state.ts 是服务器时间，手机时钟偏差会算错）：
                                // 上报间隔 5s，取中值估算对方当前位置
                                val peerPos = state.positionMs + 2_500
                                val diff = peerPos - playbackPositionMs
                                if (kotlin.math.abs(diff) > 6_000) {
                                    applyingListenState = true
                                    try {
                                        seekTo(peerPos)
                                    } finally {
                                        applyingListenState = false
                                    }
                                    driftCooldownUntil = now + 8_000
                                }
                            }
                        }
                    }
                }
                delay(3_000)
            }
        }
    }

    /** 全量应用对方状态（切歌/播放暂停/进度）；期间副作用不上报，防回声 */
    private fun applyPeerState(state: com.example.helloworld.data.remote.ListenStateResponse) {
        val uiTrack = state.track?.toUiTrack() ?: return
        applyingListenState = true
        try {
            if (uiTrack.id != currentTrack.id) {
                playTrackInternal(uiTrack, listOf(uiTrack))
            }
            if (state.isPlaying != isPlaying) togglePlayPause()
            // 进度补偿与纠漂同规则：固定估算上报间隔中值，不用本地时钟对表
            val peerPos = state.positionMs + if (state.isPlaying) 2_500 else 0
            if (kotlin.math.abs(peerPos - playbackPositionMs) > 3_000) {
                seekTo(peerPos)
            }
        } finally {
            applyingListenState = false
        }
    }

    private fun reportListenStateNow() {
        if (!listenHosting || applyingListenState) return
        val track = currentTrack
        val payload = track.apiTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching {
                api.reportListenState(
                    "Bearer $t",
                    com.example.helloworld.data.remote.ListenStateRequest(
                        payload, playbackPositionMs, isPlaying, listenRev, listenPartner
                    )
                )
            }
        }
    }

    // ===================== 宠物（好友对共养，机制见 docs/pet-design.md） =====================

    /** 对方发来的宠物动作（聊天页实时播放动画）；本地点击也复用同一 Flow 播放 */
    private val _petActions = MutableSharedFlow<PetActionEvent>(extraBufferCapacity = 32)
    val petActions: SharedFlow<PetActionEvent> = _petActions

    /** friend -> 宠物对概况（任务/音符/等级共享） */
    val pets = MutableStateFlow<Map<String, PetPairDto>>(emptyMap())

    /** 聊天页宠物拖动位置（好友 -> 相对聊天区左上角的偏移），切换聊天保留 */
    val petPositions = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Offset>()

    fun petOf(friend: String): PetPairDto? = pets.value[friend]

    fun refreshPets() {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petPairs("Bearer $t") }
                .onSuccess { resp -> pets.value = resp.pairs.associateBy { it.friend } }
            // 素材清单：服务器新增角色时客户端自动可下载，无需发版
            runCatching { com.example.helloworld.ui.components.PetContentStore.refresh() }
        }
    }

    /** 仅回拉宠物数据、不碰素材清单：聊天等高频埋点后用，避免每条消息都请求 manifest */
    fun refreshPetsLight() {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petPairs("Bearer $t") }
                .onSuccess { resp -> pets.value = resp.pairs.associateBy { it.friend } }
        }
    }

    fun refreshPet(friend: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petPair(friend, "Bearer $t") }
                .onSuccess { p -> pets.value = pets.value + (friend to p) }
                .onFailure {
                    // 404 = 与对方还未领养，清掉本地缓存
                    if (it is HttpException && it.code() == 404) {
                        pets.value = pets.value - friend
                    }
                }
        }
    }

    /** 发起领养邀请：本地留一条邀请消息 + WS 信令 */
    fun inviteAdopt(peer: String) {
        viewModelScope.launch(Dispatchers.IO) {
            imMessageDao.insert(
                com.example.helloworld.data.local.ImMessageEntity(
                    peer = peer,
                    outgoing = true,
                    msgType = "pet_invite",
                    text = "邀请你一起领养宠物",
                    ts = System.currentTimeMillis()
                )
            )
            YinheImClient.sendPetInvite(peer)
        }
    }

    /** 同意领养：发 accept 信令（服务端建 pair 并回执邀请方），随后拉详情 */
    fun acceptAdopt(peer: String) {
        viewModelScope.launch(Dispatchers.IO) {
            YinheImClient.sendPetAccept(peer)
            delay(400)
            refreshPet(peer)
        }
    }

    /** 点击宠物：上报服务端（校验解锁/发音符奖励/推给对方实时播放） */
    fun petInteract(peer: String, action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petInteract("Bearer $t", PetInteractRequest(peer, action)) }
                .onSuccess { p -> pets.value = pets.value + (peer to p) }
        }
    }

    /** 投喂（5 音符 = 10 经验，聊天页气泡/宠物中心共用） */
    fun petFeed(peer: String, count: Int = 1, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petFeed("Bearer $t", PetFeedRequest(peer, count)) }
                .onSuccess { p ->
                    pets.value = pets.value + (peer to p)
                    val leveled = p.leveledUp ?: 0
                    val msg = if (leveled > 0) "升级啦！现在 Lv${p.level}" + 
                        (p.newActions?.takeIf { it.isNotEmpty() }?.let { "，解锁动作：${it.joinToString("、")}" } ?: "")
                    else null
                    withContext(Dispatchers.Main) { onResult(msg) }
                }
                .onFailure { withContext(Dispatchers.Main) { onResult(friendlyAuthError(it as Exception, register = false)) } }
        }
    }

    fun petSignin(peer: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petSignin("Bearer $t", PetFriendRequest(peer)) }
                .onSuccess { p -> pets.value = pets.value + (peer to p) }
        }
    }

    /** 领取已完成任务的音符奖励 */
    fun petClaim(peer: String, taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petClaim("Bearer $t", PetClaimRequest(peer, taskId)) }
                .onSuccess { p -> pets.value = pets.value + (peer to p) }
        }
    }

    /** 角色商店（价格/等级门槛），进精灵页时刷新 */
    val petShop = MutableStateFlow<List<PetShopItem>>(emptyList())
    /** 当前等级的动作货架（随机生成、固定） */
    val petShelf = MutableStateFlow<List<PetShelfItem>>(emptyList())
    /** 全量动作目录（用于名称展示/过滤） */
    val petActionCatalog = MutableStateFlow<List<PetActionDef>>(emptyList())
    /** 当前角色全部动作（含未解锁/等级锁），商店全量展示 */
    val petActionShop = MutableStateFlow<List<PetActionDef>>(emptyList())
    val petShopLevel = MutableStateFlow(1)

    fun refreshPetShop(peer: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petShop("Bearer $t", peer) }
                .onSuccess { resp ->
                    petShop.value = resp.species
                    petShelf.value = resp.shelf
                    petActionCatalog.value = resp.actions
                    petActionShop.value = resp.catalog
                    petShopLevel.value = resp.level
                }
        }
    }

    /** 音符兑换角色（校验价格 + 等级门槛） */
    fun petBuySpecies(peer: String, species: String, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petBuySpecies("Bearer $t", PetSpeciesRequest(peer, species)) }
                .onSuccess { p ->
                    pets.value = pets.value + (peer to p)
                    refreshPetShop(peer)
                    withContext(Dispatchers.Main) { onResult(null) }
                }
                .onFailure { withContext(Dispatchers.Main) { onResult(friendlyAuthError(it as Exception, register = false)) } }
        }
    }

    /** 购买当前货架上的动作 */
    fun petBuyAction(peer: String, actionId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petBuyAction("Bearer $t", PetBuyActionRequest(peer, actionId)) }
                .onSuccess { p ->
                    pets.value = pets.value + (peer to p)
                    refreshPetShop(peer)
                    withContext(Dispatchers.Main) { onResult(null) }
                }
                .onFailure { withContext(Dispatchers.Main) { onResult(friendlyAuthError(it as Exception, register = false)) } }
        }
    }

    /** 给共养宠物改名 */
    fun petRename(peer: String, name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petRename("Bearer $t", PetRenameRequest(peer, name)) }
                .onSuccess { p ->
                    pets.value = pets.value + (peer to p)
                    withContext(Dispatchers.Main) { onResult(null) }
                }
                .onFailure { withContext(Dispatchers.Main) { onResult(friendlyAuthError(it as Exception, register = false)) } }
        }
    }

    fun petSwitchSpecies(peer: String, species: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petSwitchSpecies("Bearer $t", PetSpeciesRequest(peer, species)) }
                .onSuccess { p ->
                    pets.value = pets.value + (peer to p)
                    refreshPetShop(peer)
                    // 悬浮球跟着换形象：服务已在跑的话，重启一次让它切物种
                    withContext(Dispatchers.Main) {
                        syncBubbleUnlocked(species)
                        if (bubbleEnabled) {
                            BubbleService.start(app, species)
                        }
                    }
                }
        }
    }

    /** 行为埋点：累加所有宠物对的当日任务进度（没宠物时服务端返回空 updated，不必本地预判） */
    fun reportPetEvent(type: String, count: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = AuthRepository.token() ?: return@launch
            runCatching { api.petEvent("Bearer $t", PetEventRequest(type, count)) }
                .onSuccess { resp ->
                    // 只有真的影响到了某个 pair 才回拉；高频埋点走轻量刷新，不重复拉素材清单
                    if (resp.updated.isNotEmpty()) refreshPetsLight()
                }
        }
    }
}

/** 宠物动作事件：from=触发方（自己点击为空播放时也用对方名占位），action=动作 id */
data class PetActionEvent(val from: String, val action: String)
