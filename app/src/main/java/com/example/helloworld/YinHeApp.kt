package com.example.helloworld

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.helloworld.data.local.SettingsRepository
import com.example.helloworld.data.model.VIRTUAL_FAVORITES_ID
import com.example.helloworld.data.model.VIRTUAL_LOCAL_ID
import com.example.helloworld.data.model.VirtualFavoritesPlaylist
import com.example.helloworld.data.model.VirtualLocalPlaylist
import com.example.helloworld.ui.auth.AuthScreen
import com.example.helloworld.ui.components.AmbientBackground
import com.example.helloworld.ui.components.LiquidBottomBar
import com.example.helloworld.ui.components.MiniPlayer
import com.example.helloworld.ui.home.HomeScreen
import com.example.helloworld.ui.library.MyScreen
import com.example.helloworld.ui.library.PlaylistDetailScreen
import com.example.helloworld.ui.player.NowPlayingScreen

@Composable
fun YinHeApp(viewModel: MainViewModel = viewModel()) {
    val auth = viewModel.authState
    when (auth) {
        MainViewModel.AuthState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        MainViewModel.AuthState.LoggedOut -> AuthScreen(
            busy = viewModel.authBusy,
            error = viewModel.authError,
            onLogin = viewModel::login,
            onRegister = viewModel::register
        )

        is MainViewModel.AuthState.LoggedIn -> YinHeMain(viewModel, auth.username)
    }
}

@Composable
private fun YinHeMain(viewModel: MainViewModel, username: String) {
    val playlists by viewModel.playlists.collectAsState()
    val favoriteTracks by viewModel.favoriteTracks.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val cachedTracks by viewModel.cachedTracks.collectAsState()
    val cacheStatus by viewModel.cacheStatus.collectAsState()
    val cacheProgress by viewModel.cacheProgress.collectAsState()
    val playlistTracksMap by viewModel.playlistTracksMap.collectAsState()
    val unreadTotal by viewModel.unreadCounts.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val repeatModeLabel by viewModel.repeatModeLabel.collectAsState()
    val destination = viewModel.selectedPlaylistId?.let { "detail:$it" } ?: "tab:${viewModel.selectedTab}"

    val context = LocalContext.current
    var lastBackMs by remember { mutableLongStateOf(0L) }

    // 根返回：非首页 tab 先回首页；首页双击退出（防误触）；详情页/播放页各自 BackHandler 逐层返回
    BackHandler(enabled = viewModel.selectedPlaylistId == null && !viewModel.showNowPlaying) {
        if (viewModel.selectedTab != 0) {
            viewModel.selectTab(0)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackMs < 2000) {
                (context as? Activity)?.finish()
            } else {
                lastBackMs = now
                Toast.makeText(context, "再按一次退出音河", Toast.LENGTH_SHORT).show()
            }
        }
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                AmbientBackground()
                AnimatedContent(
                    targetState = destination,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    transitionSpec = {
                        val targetDetail = targetState.startsWith("detail:")
                        val initialDetail = initialState.startsWith("detail:")
                        val forward = targetDetail || (!initialDetail && targetState > initialState)
                        if (forward) {
                            slideInHorizontally { it / 3 } + fadeIn() togetherWith
                                slideOutHorizontally { -it / 4 } + fadeOut()
                        } else {
                            slideInHorizontally { -it / 3 } + fadeIn() togetherWith
                                slideOutHorizontally { it / 4 } + fadeOut()
                        }
                    },
                    label = "pageTransition"
                ) { currentDestination ->
                    val playlistId = viewModel.selectedPlaylistId
                    if (currentDestination.startsWith("detail:") && playlistId != null) {
                        when (playlistId) {
                            VIRTUAL_FAVORITES_ID -> PlaylistDetailScreen(
                                playlist = VirtualFavoritesPlaylist,
                                externalTracks = favoriteTracks,
                                isVirtual = true,
                                virtualKind = "favorites",
                                favorites = favorites,
                                cacheStatus = cacheStatus,
                                cacheProgress = cacheProgress,
                                onBack = viewModel::closePlaylist,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayTrack = viewModel::playTrack
                            )

                            VIRTUAL_LOCAL_ID -> PlaylistDetailScreen(
                                playlist = VirtualLocalPlaylist,
                                externalTracks = cachedTracks,
                                isVirtual = true,
                                virtualKind = "local",
                                favorites = favorites,
                                cacheStatus = cacheStatus,
                                cacheProgress = cacheProgress,
                                onBack = viewModel::closePlaylist,
                                onRemoveCached = viewModel::removeCachedTrack,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayTrack = viewModel::playTrack
                            )

                            else -> playlists.find { it.id == playlistId }?.let { playlist ->
                                val detailTracks by viewModel.playlistTracks(playlist.id)
                                    .collectAsState(initial = emptyList())
                                PlaylistDetailScreen(
                                    playlist = playlist,
                                    serverTracks = viewModel.serverTracks,
                                    externalTracks = detailTracks,
                                    favorites = favorites,
                                    cacheStatus = cacheStatus,
                                    cacheProgress = cacheProgress,
                                    localTracks = cachedTracks,
                                    favoriteTracks = favoriteTracks,
                                    otherPlaylists = playlists,
                                    otherPlaylistTracks = playlistTracksMap,
                                    onBack = viewModel::closePlaylist,
                                    onUpdatePlaylist = viewModel::updatePlaylist,
                                    onDeletePlaylist = viewModel::deletePlaylist,
                                    onAddTracks = { chosen -> viewModel.addTracks(playlist.id, chosen) },
                                    onRemoveTrack = { viewModel.removeTrack(playlist.id, it) },
                                    onMoveTrack = { from, to -> viewModel.moveTrack(playlist.id, from, to) },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onPlayTrack = viewModel::playTrack,
                                    friends = friends,
                                    onShareToFriend = { friend ->
                                        viewModel.sharePlaylistTo(friend, playlist.id) { err ->
                                            err?.let { com.example.helloworld.playback.YinheLogger.log("分享", "歌单分享失败：$it") }
                                        }
                                    }
                                )
                            }
                        }
                    } else if (currentDestination == "tab:1") {
                        com.example.helloworld.ui.pet.PetScreen(viewModel)
                    } else if (currentDestination == "tab:2") {
                        MyScreen(
                            playlists = playlists,
                            favoriteCount = favoriteTracks.size,
                            cachedCount = cachedTracks.size,
                            username = username,
                            myAvatarUrl = username?.let { viewModel.avatarUrl(it) },
                            avatarPresets = viewModel.avatarPresets,
                            avatarPresetUrl = viewModel::avatarPresetUrl,
                            onPickAvatar = { uri, cb -> viewModel.uploadAvatar(context, uri, cb) },
                            onPickPreset = viewModel::chooseAvatarPreset,
                            onChangeUsername = viewModel::changeUsername,
                            onChangePassword = viewModel::changePassword,
                            onCheckUpdate = viewModel::checkUpdate,
                            onSubmitFeedback = viewModel::submitFeedback,
                            onUploadLogs = viewModel::uploadLogs,
                            onOpenFriends = viewModel::openFriends,
                            unreadTotal = unreadTotal.values.sum(),
                            onLogout = viewModel::logout,
                            onOpenPlaylist = viewModel::openPlaylist,
                            onOpenFavorites = { viewModel.openPlaylist(VIRTUAL_FAVORITES_ID) },
                            onOpenLocal = { viewModel.openPlaylist(VIRTUAL_LOCAL_ID) },
                            onCreatePlaylist = viewModel::createPlaylist,
                            onUpdatePlaylist = viewModel::updatePlaylist,
                            onDeletePlaylist = viewModel::deletePlaylist
                        )
                    } else {
                        HomeScreen(
                            libraryItems = viewModel.libraryState.items,
                            libraryLoading = viewModel.libraryState.loading,
                            libraryError = viewModel.libraryState.error,
                            searchItems = viewModel.searchState.items,
                            searchLoading = viewModel.searchState.loading,
                            searchError = viewModel.searchState.error,
                            currentTrack = viewModel.currentTrack,
                            isPlaying = viewModel.isPlaying,
                            isBuffering = viewModel.isBuffering,
                            favorites = favorites,
                            playlists = playlists,
                            cacheStatus = cacheStatus,
                            cacheProgress = cacheProgress,
                            selectedSource = viewModel.selectedSearchSource,
                            onSelectSource = viewModel::selectSearchSource,
                            myAvatarUrl = username?.let { viewModel.avatarUrl(it) },
                            username = username,
                            heroTrack = viewModel.heroTrack,
                            heroLyrics = viewModel.heroLyrics,
                            todayPlays = viewModel.todayPlays,
                            onSearch = viewModel::search,
                            onRetryLibrary = viewModel::refreshLibrary,
                            onPlayTrack = viewModel::playTrack,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onAddTrackToPlaylist = viewModel::addTrack
                        )
                    }
                }
            }

            // 精灵投喂页展开态：整屏只留角色，隐藏迷你播放器与底部导航
            if (!(viewModel.selectedTab == 1 && viewModel.petFullscreen)) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    MiniPlayer(
                        track = viewModel.currentTrack,
                        isPlaying = viewModel.isPlaying,
                        isBuffering = viewModel.isBuffering,
                        positionMs = viewModel.playbackPositionMs,
                        durationMs = viewModel.playbackDurationMs,
                        error = viewModel.playbackError,
                        isCached = cacheStatus[viewModel.currentTrack.id] == "done",
                        repeatModeLabel = repeatModeLabel,
                        playedSourceLabel = viewModel.currentPlaySource?.let {
                            com.example.helloworld.data.SourceCatalog.displayName(it)
                        },
                        onCycleRepeatMode = viewModel::cycleRepeatMode,
                        onPrevious = viewModel::seekToPrevious,
                        onNext = viewModel::seekToNext,
                        onOpenNowPlaying = viewModel::openNowPlaying,
                        onPlayPause = viewModel::togglePlayPause
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LiquidBottomBar(
                        selectedIndex = viewModel.selectedTab,
                        onSelected = viewModel::selectTab
                    )
                }
            }

            if (viewModel.showNowPlaying) {
                NowPlayingScreen(
                    track = viewModel.currentTrack,
                    isPlaying = viewModel.isPlaying,
                    isBuffering = viewModel.isBuffering,
                    positionMs = viewModel.playbackPositionMs,
                    durationMs = viewModel.playbackDurationMs,
                    isFavorite = viewModel.currentTrack.id in favorites,
                    isCached = cacheStatus[viewModel.currentTrack.id] == "done",
                    playedSourceLabel = viewModel.currentPlaySource?.let {
                        com.example.helloworld.data.SourceCatalog.displayName(it)
                    },
                    repeatModeLabel = repeatModeLabel,
                    lyrics = viewModel.nowPlayingLyrics,
                    listenHosting = viewModel.listenHosting,
                    listenPartner = viewModel.listenPartner,
                    onStopListen = {
                        if (viewModel.listenPartner != null) viewModel.stopFollowListen()
                        else viewModel.stopListenHost()
                    },
                    onClose = viewModel::closeNowPlaying,
                    onToggleFavorite = { viewModel.toggleFavorite(viewModel.currentTrack) },
                    onPrevious = viewModel::seekToPrevious,
                    onNext = viewModel::seekToNext,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onCycleRepeatMode = viewModel::cycleRepeatMode,
                    onSeek = viewModel::seekTo
                )
            }

            // IM 页面层级最高（好友/聊天）
            when (val screen = viewModel.imScreen) {
                "friends" -> com.example.helloworld.ui.im.FriendsScreen(viewModel)
                null -> Unit
                else -> if (screen.startsWith("chat:")) {
                    com.example.helloworld.ui.im.ChatScreen(viewModel, screen.removePrefix("chat:"))
                }
            }

            // 一起听事件反馈横幅（对方加入/退出/邀请过期），3.5s 自动消
            viewModel.listenFeedback?.let { fb ->
                androidx.compose.runtime.LaunchedEffect(fb) {
                    kotlinx.coroutines.delay(3_500)
                    viewModel.clearListenFeedback()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 150.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(fb, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            // 外网源首开：下载进度弹窗（可关闭，关闭后下载继续、完成自动播放）
            val prep = viewModel.preparingTrack
            if (prep != null && viewModel.prepareDialogVisible) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = viewModel::dismissPrepareDialog,
                    title = { Text("正在准备", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("《${prep.title}》首次播放需要先下载", fontSize = 13.sp)
                            Text(
                                "外网音源较慢，可能需要 10-60 秒，完成后自动播放",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                            val prepErr = viewModel.preparingError
                            if (prepErr != null) {
                                Text(
                                    "下载失败：$prepErr",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (viewModel.preparingProgress >= 0) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { viewModel.preparingProgress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "${viewModel.preparingProgress}%",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            } else {
                                androidx.compose.material3.LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    },
                    confirmButton = {
                        if (viewModel.preparingError != null) {
                            TextButton(onClick = { viewModel.retryPrepare() }) { Text("重试") }
                        } else {
                            TextButton(onClick = viewModel::dismissPrepareDialog) { Text("后台继续") }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissPrepareDialog) { Text("关闭") }
                    }
                )
            }

            // 启动公告（每个版本只弹一次，关闭后记录 seen_version；不强制更新）
            val ann = viewModel.announcement
            if (ann != null && !viewModel.announcementShown) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = viewModel::dismissAnnouncement,
                    title = {
                        Column {
                            Text(ann.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "v" + ann.version,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    },
                    text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(ann.notes) { note ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 7.dp, end = 8.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = note,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = viewModel::dismissAnnouncement) {
                            Text("知道了")
                        }
                    },
                    dismissButton = {
                        // 只有服务端最新包确实比本机新时才给下载按钮
                        if (ann.apkUrl.isNotBlank() && ann.apkVersionCode > com.example.helloworld.BuildConfig.VERSION_CODE) {
                            TextButton(onClick = {
                                viewModel.dismissAnnouncement()
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(ann.apkUrl)
                                )
                                context.startActivity(intent)
                            }) {
                                Text("去下载新版", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )
            }
        }
    }
}
