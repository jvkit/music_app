package com.example.helloworld.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.helloworld.MainViewModel
import com.example.helloworld.data.remote.DiscoverPlaylistDto
import com.example.helloworld.data.remote.MusicApiClient
import com.example.helloworld.ui.components.TrackArtwork
import com.example.helloworld.ui.home.Track

/**
 * 发现页：私人 FM + 官方榜单 + 分类歌单墙 + 歌单详情
 * 数据全部来自服务端 /api/discover/ 系列接口（网易云公开元数据代理），播放走现有 playTrack 链路。
 */
@Composable
fun DiscoverScreen(viewModel: MainViewModel) {
    LaunchedEffect(Unit) { viewModel.ensureDiscoverLoaded() }
    BackHandler(enabled = viewModel.discoverDetail != null) { viewModel.closeDiscoverDetail() }

    val detail = viewModel.discoverDetail
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 188.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "发现音乐",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "榜单、歌单与私人 FM，货架是借的，好听是自己的",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                        .clickable { viewModel.openRecognize() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "识曲",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        if (detail != null) {
            DiscoverDetailContent(
                detail = detail,
                isCurrent = { id -> viewModel.currentTrack.id == id },
                onPlayAll = viewModel::playDiscoverAll,
                onPlayTrack = { track -> viewModel.playTrack(track, detail.tracks) },
                onBack = viewModel::closeDiscoverDetail
            )
        } else {
            item {
                FmCard(onClick = viewModel::playDiscoverFm)
            }

            item {
                Column {
                    SectionTitle("官方榜单")
                    if (viewModel.discoverToplists.loading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(viewModel.discoverToplists.items, key = { it.id }) { toplist ->
                                ToplistCard(toplist) { viewModel.openDiscoverPlaylist(toplist.id) }
                            }
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(viewModel.discoverCategories, key = { it.id }) { cat ->
                        CategoryChip(
                            label = cat.name,
                            selected = viewModel.discoverCat == cat.id,
                            onClick = { viewModel.selectDiscoverCat(cat.id) }
                        )
                    }
                }
            }

            item { SectionTitle("歌单") }

            val wall = viewModel.discoverWall
            when {
                wall.loading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(modifier = Modifier.size(26.dp)) }
                }

                wall.error != null -> item {
                    ErrorBox(message = wall.error ?: "", onRetry = viewModel::refreshDiscoverWall)
                }

                wall.items.isEmpty() -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("这个分类暂时没有歌单", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                }

                else -> items(wall.items.chunked(3), key = { it.joinToString("|") { p -> p.id.toString() } }) { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                modifier = Modifier.weight(1f)
                            ) { viewModel.openDiscoverPlaylist(playlist.id) }
                        }
                        repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color(0xFF2A1B45)
            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun FmCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "私人FM",
                tint = Color(0xFF2A1B45),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "私人 FM",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "随机来一首，看看命运递给你什么歌",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
    }
}

private fun coverUrl(cover: String?): String? = MusicApiClient.thumbnailProxyUrl(cover)

@Composable
private fun ToplistCard(toplist: DiscoverPlaylistDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = coverUrl(toplist.cover),
            contentDescription = toplist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            toplist.name,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
        toplist.updateFrequency?.let {
            Text(it, fontSize = 9.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun PlaylistCard(playlist: DiscoverPlaylistDto, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = coverUrl(playlist.cover),
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            playlist.playCount?.let { count ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(formatCount(count), fontSize = 10.sp, color = Color.White)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            playlist.name,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        playlist.trackCount?.let {
            Text(
                "$it 首",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

private fun formatCount(count: Long): String = when {
    count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000f)
    count >= 10_000 -> "${count / 10_000}万"
    else -> count.toString()
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .clickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text("重试", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * 歌单/榜单/FM 详情：扩展在 LazyListScope 上（item/items 直接挂进列表）。
 */
private fun LazyListScope.DiscoverDetailContent(
    detail: MainViewModel.DiscoverDetailUi,
    isCurrent: (String) -> Boolean,
    onPlayAll: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onBack: () -> Unit
) {
    if (detail.loading) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
        }
        return
    }

    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                detail.playlist?.name ?: "歌单",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }

    item {
        Row {
            AsyncImage(
                model = coverUrl(detail.playlist?.cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                detail.playlist?.creator?.let {
                    Text(
                        "by $it · ${detail.tracks.size} 首",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
                if (detail.playlist?.creator == null) {
                    Text(
                        "${detail.tracks.size} 首",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
                detail.playlist?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlayAll)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF2A1B45),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "播放全部",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2A1B45)
                    )
                }
            }
        }
    }

    items(detail.tracks, key = { it.id }) { track ->
        DiscoverTrackRow(
            track = track,
            isCurrent = isCurrent(track.id),
            onClick = { onPlayTrack(track) }
        )
    }
}

@Composable
private fun DiscoverTrackRow(track: Track, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(track = track, modifier = Modifier.size(50.dp), cornerSize = 12, showLabel = false)
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground
            )
            Text(
                track.artist,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            track.duration,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
        )
    }
}
