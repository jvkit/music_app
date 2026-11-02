package com.example.helloworld.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helloworld.data.model.Playlist
import com.example.helloworld.ui.home.Track

@Composable
fun PlaylistEditorDialog(
    playlist: Playlist?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember(playlist?.id) { mutableStateOf(playlist?.name.orEmpty()) }
    var description by remember(playlist?.id) { mutableStateOf(playlist?.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (playlist == null) "新建歌单" else "编辑歌单") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("歌单名称") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    label = { Text("一句话描述（可选）") },
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description) },
                enabled = name.isNotBlank()
            ) {
                Text(if (playlist == null) "创建" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun DeletePlaylistDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除歌单？") },
        text = { Text("“${playlist.name}”将被删除，曲库中的歌曲不会受影响。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun TrackPickerDialog(
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onAdd: (Track) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到歌单") },
        text = {
            if (tracks.isEmpty()) {
                Text("曲库歌曲已经全部加入这个歌单了")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title)
                                Text(
                                    text = "${track.artist} · ${track.duration}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { onAdd(track) }) {
                                Icon(Icons.Default.Add, contentDescription = "添加 ${track.title}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    track: Track,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Playlist) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("把《${track.title}》加进歌单") },
        text = {
            if (playlists.isEmpty()) {
                Text("还没有自建歌单，先去「我的」页新建一个吧")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(playlist) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, maxLines = 1)
                                Text(
                                    text = "${playlist.trackIds.size} 首歌曲",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 多来源多选添加：网络（服务器曲库）/ 本地缓存 / 收藏 / 其他歌单。
 * 已在目标歌单里的歌曲不显示；歌单来源需先选一个源歌单。
 */
@Composable
fun MultiSourceTrackPickerDialog(
    serverTracks: List<Track>,
    localTracks: List<Track>,
    favoriteTracks: List<Track>,
    otherPlaylists: List<Playlist>,
    otherPlaylistTracks: Map<String, List<Track>>,
    excludeIds: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (List<Track>) -> Unit
) {
    var tab by remember { mutableStateOf("net") }
    var sourcePlaylistId by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    // 各来源候选（过滤已在目标歌单的）
    fun candidates(list: List<Track>) = list.filter { it.id !in excludeIds }

    val tabTracks: List<Track> = when (tab) {
        "net" -> candidates(serverTracks)
        "local" -> candidates(localTracks)
        "fav" -> candidates(favoriteTracks)
        "playlist" -> sourcePlaylistId
            ?.let { candidates(otherPlaylistTracks[it] ?: emptyList()) }
            ?: emptyList()
        else -> emptyList()
    }
    // 切换来源/歌单后用「当前候选 ∩ 已选」作为有效勾选（不直接改写 state，避免重组期副作用）
    val validIds = tabTracks.map { it.id }.toSet()
    val effectiveSelected = selectedIds intersect validIds

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加歌曲") },
        text = {
            Column {
                // 来源 tab
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("net" to "网络", "local" to "本地", "fav" to "收藏", "playlist" to "歌单").forEach { (id, label) ->
                        val active = tab == id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                )
                                .clickable {
                                    tab = id
                                    if (id != "playlist") sourcePlaylistId = null
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 歌单来源：先选源歌单
                if (tab == "playlist") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        otherPlaylists.forEach { p ->
                            val active = sourcePlaylistId == p.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    )
                                    .clickable { sourcePlaylistId = p.id }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = p.name,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (tabTracks.isEmpty()) {
                    Text(
                        text = when {
                            tab == "playlist" && sourcePlaylistId == null -> "先选一个来源歌单"
                            tab == "playlist" && (otherPlaylistTracks[sourcePlaylistId]?.isEmpty() != false) ->
                                "这个歌单还没有歌曲"
                            tab == "playlist" -> "这个歌单的歌曲都已加进来了"
                            tab == "local" && localTracks.isEmpty() -> "还没有本地缓存的歌曲，先听几首"
                            tab == "local" -> "本地歌曲都已加进来了"
                            tab == "fav" && favoriteTracks.isEmpty() -> "还没有收藏，去搜索页点爱心"
                            tab == "fav" -> "收藏的歌曲都已加进来了"
                            tab == "net" && serverTracks.isEmpty() -> "服务器曲库暂无歌曲"
                            else -> "曲库歌曲已全部加入这个歌单"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 330.dp)
                            .padding(top = 10.dp)
                    ) {
                        items(tabTracks, key = { it.id }) { track ->
                            val checked = track.id in effectiveSelected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (checked) selectedIds - track.id
                                        else selectedIds + track.id
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        selectedIds = if (checked) selectedIds - track.id
                                        else selectedIds + track.id
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, maxLines = 1, fontSize = 14.sp)
                                    Text(
                                        text = "${track.artist} · ${track.duration}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        val chosen = tabTracks.filter { it.id in effectiveSelected }
                        onAdd(chosen)
                        onDismiss()
                    },
                    enabled = effectiveSelected.isNotEmpty()
                ) {
                    Text(if (effectiveSelected.isEmpty()) "添加" else "添加 ${effectiveSelected.size} 首")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
