package com.example.helloworld.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helloworld.data.SourceCatalog
import com.example.helloworld.data.model.Playlist
import com.example.helloworld.ui.components.TrackArtwork
import com.example.helloworld.ui.library.AddToPlaylistDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun HomeScreen(
    libraryItems: List<Track>,
    libraryLoading: Boolean,
    libraryError: String?,
    searchItems: List<Track>,
    searchLoading: Boolean,
    searchError: String?,
    currentTrack: Track,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    favorites: Set<String>,
    playlists: List<Playlist> = emptyList(),
    cacheStatus: Map<String, String> = emptyMap(),
    cacheProgress: Map<String, Pair<Long, Long>> = emptyMap(),
    selectedSource: String = SourceCatalog.AUTO,
    onSelectSource: (String) -> Unit = {},
    myAvatarUrl: String? = null,
    username: String? = null,
    heroTrack: Track? = null,
    heroLyrics: List<String> = emptyList(),
    todayPlays: Map<String, Int> = emptyMap(),
    onSearch: (String) -> Unit,
    onRetryLibrary: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddTrackToPlaylist: (String, Track) -> Unit = { _, _ -> }
) {
    var query by remember { mutableStateOf("") }
    var addTarget by remember { mutableStateOf<Track?>(null) }
    var showAllLibrary by remember { mutableStateOf(false) }
    val searching = query.isNotBlank()
    val displayedTracks = if (searching) searchItems else libraryItems

    // 服务器曲库排序：有封面绝对优先 > 今日播放次数 > 相同随机（先 shuffle 再稳定排序）
    val sortedLibrary = remember(libraryItems, todayPlays) {
        libraryItems
            .shuffled()
            .sortedWith(
                compareByDescending<Track> { !it.coverUrl.isNullOrBlank() }
                    .thenByDescending { todayPlays[it.id] ?: 0 }
            )
    }
    val visibleLibrary = if (showAllLibrary) sortedLibrary else sortedLibrary.take(50)

    // 关键词或音源变化都重新搜索（400ms 防抖，切源立即生效）；回车由 SearchField onSubmit 立即触发
    LaunchedEffect(query, selectedSource) {
        if (query.isBlank()) return@LaunchedEffect
        delay(400)
        onSearch(query)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 188.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { HomeHeader(username = username, myAvatarUrl = myAvatarUrl) }
        item {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onSubmit = { if (query.isNotBlank()) onSearch(query) }
            )
        }
        item { SourceFilterRow(selected = selectedSource, onSelect = onSelectSource) }
        if (!searching) {
            item {
                HeroCard(
                    heroTrack = heroTrack,
                    heroLyrics = heroLyrics,
                    enabled = libraryItems.isNotEmpty(),
                    onPlay = {
                        val target = heroTrack ?: libraryItems.firstOrNull()
                        target?.let { onPlayTrack(it, if (heroTrack != null) listOf(heroTrack) + sortedLibrary else sortedLibrary) }
                    }
                )
            }
            item {
                SectionTitle(
                    title = "服务器音乐库",
                    subtitle = if (libraryLoading) "LOADING" else ""
                )
            }
            if (libraryLoading && libraryItems.isEmpty()) {
                item { LoadingCard(text = "正在连接音河服务器…") }
            } else if (libraryError != null && libraryItems.isEmpty()) {
                item {
                    ErrorCard(
                        title = "暂时无法连接服务器",
                        message = libraryError,
                        onRetry = onRetryLibrary
                    )
                }
            } else if (libraryItems.isEmpty()) {
                item {
                    EmptyLibraryCard()
                }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(sortedLibrary.take(4)) { track ->
                            RecommendationCard(track = track, onClick = { onPlayTrack(track, sortedLibrary) })
                        }
                    }
                }
                itemsIndexed(visibleLibrary, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        rank = index + 1,
                        isCurrent = currentTrack.id == track.id,
                        isPlaying = isPlaying && currentTrack.id == track.id,
                        isBuffering = isBuffering && currentTrack.id == track.id,
                        isFavorite = track.id in favorites,
                        cacheState = cacheStatus[track.id],
                        cacheBytes = cacheProgress[track.id],
                        onFavorite = { onToggleFavorite(track) },
                        onAddToPlaylist = { addTarget = track },
                        onClick = { onPlayTrack(track, sortedLibrary) },
                        modifier = Modifier.animateItem()
                    )
                }
                if (!showAllLibrary && sortedLibrary.size > 50) {
                    item {
                        Text(
                            text = "查看更多（还有 ${sortedLibrary.size - 50} 首）",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showAllLibrary = true }
                                .padding(vertical = 14.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item {
                SectionTitle(
                    title = "搜索结果",
                    subtitle = if (searchLoading) "SEARCHING" else "${displayedTracks.size} TRACKS"
                )
            }
            if (searchError != null) {
                item {
                    ErrorCard(title = "搜索失败", message = searchError, onRetry = { onSearch(query) })
                }
            } else if (searchLoading && displayedTracks.isEmpty()) {
                item { LoadingCard(text = "正在全网找歌…") }
            } else if (displayedTracks.isEmpty()) {
                item { EmptySearchResult() }
            } else {
                itemsIndexed(displayedTracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        rank = index + 1,
                        isCurrent = currentTrack.id == track.id,
                        isPlaying = isPlaying && currentTrack.id == track.id,
                        isBuffering = isBuffering && currentTrack.id == track.id,
                        isFavorite = track.id in favorites,
                        cacheState = cacheStatus[track.id],
                        cacheBytes = cacheProgress[track.id],
                        onFavorite = { onToggleFavorite(track) },
                        onAddToPlaylist = { addTarget = track },
                        onClick = { onPlayTrack(track, displayedTracks) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    addTarget?.let { target ->
        AddToPlaylistDialog(
            track = target,
            playlists = playlists,
            onDismiss = { addTarget = null },
            onSelect = { playlist ->
                onAddTrackToPlaylist(playlist.id, target)
                addTarget = null
            }
        )
    }
}

@Composable
private fun HomeHeader(username: String?, myAvatarUrl: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "音河音乐",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Text(
                text = "YINHE MUSIC · 搜索、播放与收藏",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        com.example.helloworld.ui.components.UserAvatar(
            url = myAvatarUrl,
            name = username,
            size = 46.dp
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSubmit: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("搜索歌曲、歌手，试试拼音或中文") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "清空搜索")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
    )
}

@Composable
private fun HeroCard(
    heroTrack: Track?,
    heroLyrics: List<String>,
    enabled: Boolean,
    onPlay: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "heroGradient")
    val shift by transition.animateFloat(
        initialValue = -120f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(tween(6500), RepeatMode.Reverse),
        label = "shift"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(206.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF704BFF), Color(0xFFE747B8), Color(0xFFFF8B78)),
                    start = Offset(shift, 0f),
                    end = Offset(620f + shift, 720f)
                )
            )
            .clickable(enabled = enabled || heroTrack != null, onClick = onPlay)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 46.dp, y = (-54).dp)
                .size(190.dp)
                .background(Color.White.copy(alpha = 0.13f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 18.dp, y = 44.dp)
                .size(132.dp)
                .background(Color.White.copy(alpha = 0.09f), CircleShape)
        )

        if (heroTrack != null) {
            // 今日推荐：封面 + 元信息 + 歌词轮换动效
            var lyricIndex by remember(heroTrack.id) { mutableStateOf(0) }
            LaunchedEffect(heroLyrics, heroTrack.id) {
                if (heroLyrics.isEmpty()) return@LaunchedEffect
                while (isActive) {
                    delay(2600)
                    lyricIndex = (lyricIndex + 1) % heroLyrics.size
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                heroTrack.coverUrl?.let { cover ->
                    coil.compose.AsyncImage(
                        model = cover,
                        contentDescription = heroTrack.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                    )
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = "今日推荐",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.2.sp
                    )
                    Text(
                        text = heroTrack.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = heroTrack.artist,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (heroLyrics.isNotEmpty()) {
                        androidx.compose.animation.Crossfade(
                            targetState = lyricIndex,
                            animationSpec = tween(600),
                            label = "heroLyric"
                        ) { idx ->
                            Text(
                                text = heroLyrics.getOrElse(idx) { "" },
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "YINHE SERVER",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp
            )
            Column {
                Text(
                    text = "今天想听什么",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = if (enabled) "从服务器歌单，开始今天的循环" else "搜索一首歌，让音河替你找到它",
                    color = Color.White.copy(alpha = 0.84f),
                    fontSize = 14.sp
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (enabled) 1f else 0.5f))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF8A42D5),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (enabled) "播放服务器第一首" else "等待搜索结果",
                    color = Color(0xFF7132BC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SourceFilterRow(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(SourceCatalog.AUTO to "自动") +
        SourceCatalog.all.map { it.id to it.displayName }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.first }) { (id, label) ->
            val isSelected = id == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    )
                    .clickable { onSelect(id) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp
            )
        }
    }
}

@Composable
private fun RecommendationCard(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(154.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        TrackArtwork(
            track = track,
            modifier = Modifier.size(154.dp),
            cornerSize = 24,
            showLabel = true
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = track.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(text = track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun TrackRow(
    track: Track,
    rank: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    isFavorite: Boolean,
    cacheState: String?,
    cacheBytes: Pair<Long, Long>? = null,
    onFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val favoriteScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "favorite"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            // 点击后立刻给反馈：当前曲缓冲中显转圈，播放中显声纹条
            isCurrent && isBuffering -> CircularProgressIndicator(
                modifier = Modifier.width(30.dp).size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            isCurrent && isPlaying -> Row(
                modifier = Modifier.width(30.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 1.dp)
                            .size(width = 3.dp, height = (8 + (i % 2) * 5).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            else -> Text(
                text = "%02d".format(rank),
                modifier = Modifier.width(30.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        TrackArtwork(track = track, modifier = Modifier.size(54.dp), cornerSize = 16, showLabel = false)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = track.title,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cacheState == "done") {
                    HomeCacheChip(text = "已缓存")
                } else if (cacheState == "downloading") {
                    val percent = cacheBytes?.let { (cached, total) ->
                        if (total > 0) "${cached * 100 / total}%" else null
                    }
                    // 当前正在播放 = 缓存随播放增长；否则是上次听到一半的部分缓存
                    val label = if (isCurrent) {
                        "下载中" + (percent?.let { " $it" } ?: "")
                    } else {
                        "已缓存" + (percent?.let { " $it" } ?: "")
                    }
                    HomeCacheChip(text = label)
                }
                Text(
                    text = buildString {
                        append(SourceCatalog.displayName(track.source))
                        append(" · ")
                        if (isPlaying) append("正在播放 · ")
                        append(track.artist)
                        append(" · ")
                        append(track.duration)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "加入歌单 ${track.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    scaleX = favoriteScale
                    scaleY = favoriteScale
                }
            )
        }
    }
}

@Composable
private fun HomeCacheChip(text: String) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LoadingCard(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorCard(title: String, message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun EmptyLibraryCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "服务器音乐库还是空的", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "在上方搜索喜欢的歌，点击即可试听",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EmptySearchResult() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "没有找到同频的声音", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "换个歌名、歌手，或试试拼音搜索",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}
