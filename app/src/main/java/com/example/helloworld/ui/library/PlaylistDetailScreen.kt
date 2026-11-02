package com.example.helloworld.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helloworld.data.model.Playlist
import com.example.helloworld.ui.components.TrackArtwork
import com.example.helloworld.ui.home.Track

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    serverTracks: List<Track> = emptyList(),
    externalTracks: List<Track>? = null,
    isVirtual: Boolean = false,
    virtualKind: String = "",
    favorites: Set<String> = emptySet(),
    cacheStatus: Map<String, String> = emptyMap(),
    cacheProgress: Map<String, Pair<Long, Long>> = emptyMap(),
    localTracks: List<Track> = emptyList(),
    favoriteTracks: List<Track> = emptyList(),
    otherPlaylists: List<Playlist> = emptyList(),
    otherPlaylistTracks: Map<String, List<Track>> = emptyMap(),
    onBack: () -> Unit,
    onUpdatePlaylist: (Playlist) -> Unit = {},
    onDeletePlaylist: (Playlist) -> Unit = {},
    onAddTrack: (Track) -> Unit = {},
    onAddTracks: (List<Track>) -> Unit = {},
    onRemoveTrack: (String) -> Unit = {},
    onRemoveCached: (String) -> Unit = {},
    onMoveTrack: (Int, Int) -> Unit = { _, _ -> },
    onToggleFavorite: (Track) -> Unit = {},
    onPlayTrack: (Track, List<Track>) -> Unit,
    friends: List<com.example.helloworld.data.local.FriendEntity> = emptyList(),
    onShareToFriend: (String) -> Unit = {}
) {
    var showEditor by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    val tracks = externalTracks
        ?: playlist.trackIds.mapNotNull { id -> serverTracks.find { it.id == id } }
    val availableTracks = serverTracks.filter { it.id !in tracks.map { t -> t.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 188.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = if (isVirtual) playlist.name else "歌单详情",
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!isVirtual) {
                    IconButton(onClick = { showShare = true }) {
                        Icon(Icons.Default.Share, contentDescription = "分享歌单")
                    }
                    IconButton(onClick = { showEditor = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑当前歌单")
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF5635A7), Color(0xFF9B3E83))
                        )
                    )
                    .padding(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        color = Color.White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = playlist.description.ifBlank { "给这个歌单写一句描述吧" },
                        color = Color.White.copy(alpha = 0.76f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = "${tracks.size} TRACKS · YINHE",
                        color = Color.White.copy(alpha = 0.84f),
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }
        }
        if (!isVirtual) {
            item {
                FilledTonalButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("添加歌曲")
                }
            }
        }
        if (tracks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (virtualKind) {
                            "favorites" -> "还没有收藏任何歌曲"
                            "local" -> "还没有本地歌曲"
                            else -> "这里还很安静"
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (virtualKind) {
                            "favorites" -> "在搜索页点一下爱心，就会收进来"
                            "local" -> "播过的歌会边听边存，存好的在这里"
                            else -> "点击“添加歌曲”，放进第一首喜欢的声音"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                PlaylistTrackRow(
                    track = track,
                    index = index,
                    showManageControls = !isVirtual,
                    showDeleteOnly = isVirtual && virtualKind == "local",
                    canMoveUp = index > 0,
                    canMoveDown = index < tracks.lastIndex,
                    isFavorite = track.id in favorites,
                    cacheState = cacheStatus[track.id],
                    cacheBytes = cacheProgress[track.id],
                    onMoveUp = { onMoveTrack(index, index - 1) },
                    onMoveDown = { onMoveTrack(index, index + 1) },
                    onRemove = { onRemoveTrack(track.id) },
                    onRemoveCached = { onRemoveCached(track.id) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onClick = { onPlayTrack(track, tracks) },
                    modifier = Modifier.animateItem()
                )
            }
        }
        if (!isVirtual) {
            item {
                Text(
                    text = "删除这个歌单",
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showDelete = true }
                        .padding(12.dp)
                )
            }
        }
    }

    if (showEditor) {
        PlaylistEditorDialog(
            playlist = playlist,
            onDismiss = { showEditor = false },
            onConfirm = { name, description ->
                onUpdatePlaylist(playlist.copy(name = name, description = description))
                showEditor = false
            }
        )
    }
    if (showDelete) {
        DeletePlaylistDialog(
            playlist = playlist,
            onDismiss = { showDelete = false },
            onConfirm = { onDeletePlaylist(playlist) }
        )
    }
    if (showShare) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showShare = false },
            title = { Text("分享歌单给好友", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                if (friends.isEmpty()) {
                    Text("还没有好友，先去「我的-好友」添加", fontSize = 13.sp)
                } else {
                    Column {
                        friends.forEach { friend ->
                            Text(
                                text = friend.username,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onShareToFriend(friend.username)
                                        showShare = false
                                    }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShare = false }) { Text("取消") }
            }
        )
    }
    if (showPicker) {
        MultiSourceTrackPickerDialog(
            serverTracks = serverTracks,
            localTracks = localTracks,
            favoriteTracks = favoriteTracks,
            otherPlaylists = otherPlaylists.filter { it.id != playlist.id },
            otherPlaylistTracks = otherPlaylistTracks,
            excludeIds = tracks.map { it.id }.toSet(),
            onDismiss = { showPicker = false },
            onAdd = { chosen ->
                if (chosen.isNotEmpty()) onAddTracks(chosen)
            }
        )
    }
}

@Composable
private fun PlaylistTrackRow(
    track: Track,
    index: Int,
    showManageControls: Boolean,
    showDeleteOnly: Boolean = false,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isFavorite: Boolean,
    cacheState: String?,
    cacheBytes: Pair<Long, Long>? = null,
    isCurrent: Boolean = false,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onRemoveCached: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(track = track, modifier = Modifier.size(50.dp), cornerSize = 15, showLabel = false)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(text = "${index + 1}. ${track.title}", fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cacheState == "done") {
                    CacheChip(text = "已缓存")
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
                    CacheChip(text = label)
                }
                Text(
                    text = track.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showManageControls) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移 ${track.title}")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移 ${track.title}")
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "从歌单移除 ${track.title}",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        } else if (showDeleteOnly) {
            // 本地歌单：从本地缓存删除（文件由缓存淘汰机制回收）
            IconButton(onClick = onRemoveCached, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "从本地删除 ${track.title}",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun CacheChip(text: String) {
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
