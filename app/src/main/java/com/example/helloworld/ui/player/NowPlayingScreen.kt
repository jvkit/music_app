package com.example.helloworld.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.helloworld.MainViewModel
import com.example.helloworld.data.SourceCatalog
import com.example.helloworld.ui.components.AmbientBackground
import com.example.helloworld.ui.home.Track

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
fun NowPlayingScreen(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    isFavorite: Boolean,
    isCached: Boolean,
    playedSourceLabel: String?,
    repeatModeLabel: String,
    lyrics: MainViewModel.LyricsUiState,
    listenHosting: Boolean = false,
    listenPartner: String? = null,
    onStopListen: () -> Unit = {},
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSeek: (Long) -> Unit,
    sleepTimerActive: Boolean = false,
    sleepTimerLabel: String = "",
    onSleepTimer: (Int) -> Unit = {},
    onSleepTimerCancel: () -> Unit = {}
) {
    var showLyricsPanel by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showSleepTimer by remember { androidx.compose.runtime.mutableStateOf(false) }

    BackHandler(onBack = onClose)

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // 顶栏（避开状态栏，返回键可点）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                IconButton(onClick = { showSleepTimer = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "定时关闭",
                        tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(track.artist)
                            playedSourceLabel?.let {
                                if (it != SourceCatalog.displayName(track.source)) {
                                    append(" · 经").append(it)
                                }
                            }
                            if (isCached) append(" · 本地缓存")
                        },
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 顶栏右侧占位（收藏已移到下方控制区，保持标题居中）
                Box(modifier = Modifier.size(40.dp))
            }

            // 一起听指示条
            val listenLabel = when {
                listenPartner != null -> "正在跟随 $listenPartner 一起听"
                listenHosting -> "正在广播一起听"
                else -> null
            }
            listenLabel?.let { label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                    Text(
                        text = "退出",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onStopListen)
                    )
                }
            }

            // 黑胶 / 歌词 区
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { showLyricsPanel = !showLyricsPanel },
                contentAlignment = Alignment.Center
            ) {
                if (!showLyricsPanel) {
                    VinylDisc(track = track, isPlaying = isPlaying)
                } else {
                    LyricsPanel(lyrics = lyrics, positionMs = positionMs, onSeekLine = onSeek)
                }
                Text(
                    text = if (showLyricsPanel) "点击封面查看唱片" else "点击查看歌词",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // 进度
            var dragPosition by remember { mutableFloatStateOf(-1f) }
            val progressValue = if (durationMs > 0) {
                (dragPosition.takeIf { it >= 0f }
                    ?: (positionMs.toFloat() / durationMs)).coerceIn(0f, 1f)
            } else 0f
            Slider(
                value = progressValue,
                onValueChange = { dragPosition = it },
                onValueChangeFinished = {
                    if (durationMs > 0 && dragPosition >= 0f) {
                        onSeek((dragPosition * durationMs).toLong())
                    }
                    dragPosition = -1f
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatMs(if (dragPosition >= 0f && durationMs > 0) (dragPosition * durationMs).toLong() else positionMs),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Text(
                    text = formatMs(durationMs),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }

            // 控制区
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .clickable(onClick = onCycleRepeatMode)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = repeatModeLabel,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowLeft,
                        contentDescription = "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                    } else if (isPlaying) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            repeat(2) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 5.dp, height = 24.dp)
                                        .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "播放", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "下一首",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                // 收藏（放控制区，播放中随时可点）
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "取消收藏" else "收藏",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // 定时关闭面板（从底部浮出，盖在播放页之上）
        if (showSleepTimer) {
            SleepTimerPanel(
                active = sleepTimerActive,
                label = sleepTimerLabel,
                onPick = {
                    onSleepTimer(it)
                    showSleepTimer = false
                },
                onCancelTimer = {
                    onSleepTimerCancel()
                    showSleepTimer = false
                },
                onDismiss = { showSleepTimer = false }
            )
        }
    }
}

/**
 * 定时关闭面板：底部浮出，列出常用档位 + 「播完当前首」。
 * 已在计时中时显示剩余时间和取消按钮。
 */
@Composable
private fun SleepTimerPanel(
    active: Boolean,
    label: String,
    onPick: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(indication = null, interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()) {
                onDismiss()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF1B1B24))
                .clickable(indication = null, interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()) {}
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text(
                "定时关闭",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (active) label else "到时间自动暂停播放，伴你入睡",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            com.example.helloworld.playback.SleepTimer.PRESETS.forEach { minutes ->
                Text(
                    "$minutes 分钟后",
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(minutes) }
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                )
            }
            Text(
                "播完当前这首",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(0) }
                    .padding(vertical = 12.dp, horizontal = 4.dp)
            )
            if (active) {
                Text(
                    "取消定时",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCancelTimer() }
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                )
            }
        }
    }
}

/** 黑胶唱片：外圈纹槽 + 封面圆盘；匀速无限旋转，暂停即停、恢复无缝续走 */
@Composable
private fun VinylDisc(track: Track, isPlaying: Boolean) {
    val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
    // 12 秒一圈线性匀速：一圈接一圈（每圈目标 = 当前角度 + 360°），
    // 暂停时 LaunchedEffect 取消协程、animateTo 中断即停，恢复后从停住角度继续——无跳变。
    androidx.compose.runtime.LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 12_000,
                        easing = androidx.compose.animation.core.LinearEasing
                    )
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .size(300.dp)
            .graphicsLayer { rotationZ = rotation.value % 360f },
        contentAlignment = Alignment.Center
    ) {
        // 唱片底
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(0xFF14101D))
        )
        // 纹槽
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(CircleShape)
                .background(Color(0xFF1B1626))
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(Color(0xFF14101D))
        )
        // 封面
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(track.colors)),
            contentAlignment = Alignment.Center
        ) {
            track.coverUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "${track.title}封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: Text(
                text = track.title.take(1),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 56.sp,
                fontWeight = FontWeight.Black
            )
        }
        // 中心轴
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2440))
        )
    }
}

@Composable
private fun LyricsPanel(
    lyrics: MainViewModel.LyricsUiState,
    positionMs: Long,
    onSeekLine: (Long) -> Unit
) {
    when (lyrics) {
        is MainViewModel.LyricsUiState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        is MainViewModel.LyricsUiState.Available -> {
            val positionSec = positionMs / 1000.0
            // 当前句 = 最后一句开始时间 ≤ 播放位置的行
            val currentIndex = lyrics.lines.indexOfLast { it.time != null && it.time <= positionSec }
                .takeIf { it >= 0 } ?: -1
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            // 用户滚动/点句后暂停自动跟随，给操作留出时间（大厂同款交互）
            var userHoldUntil by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
            val userScrolling = listState.isScrollInProgress
            androidx.compose.runtime.LaunchedEffect(userScrolling) {
                if (userScrolling) userHoldUntil = System.currentTimeMillis() + 4_000
            }
            androidx.compose.runtime.LaunchedEffect(currentIndex) {
                val now = System.currentTimeMillis()
                if (currentIndex >= 0 && !userScrolling && now >= userHoldUntil) {
                    val info = listState.layoutInfo
                    val target = currentIndex - (info.visibleItemsInfo.size / 2)
                    listState.animateScrollToItem(target.coerceIn(0, lyrics.lines.lastIndex))
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(lyrics.lines.size) { index ->
                    val line = lyrics.lines[index]
                    val isCurrent = index == currentIndex
                    val isPast = index < currentIndex
                    // 行内进度：按本行与下一行的时间差线性均分（无逐字标签时的业界通用做法）
                    val lineProgress: Float = when {
                        line.time == null -> 0f
                        isPast -> 1f
                        !isCurrent -> 0f
                        else -> {
                            val nextTime = lyrics.lines.getOrNull(index + 1)?.time
                            val dur = if (nextTime != null && nextTime > line.time) (nextTime - line.time).toFloat() else 4f
                            ((positionSec - line.time).toFloat() / dur).coerceIn(0f, 1f)
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = line.time != null) {
                                line.time?.let { onSeekLine((it * 1000).toLong()) }
                                userHoldUntil = System.currentTimeMillis() + 4_000
                            }
                            .padding(horizontal = 24.dp, vertical = 7.dp)
                    ) {
                        LyricLineText(
                            text = line.text,
                            isCurrent = isCurrent,
                            progress = lineProgress,
                            primary = MaterialTheme.colorScheme.primary
                        )
                        line.translation?.takeIf { it.isNotBlank() }?.let { trans ->
                            Text(
                                text = trans,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                else Color.White.copy(alpha = 0.38f),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        MainViewModel.LyricsUiState.None -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "♪",
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    fontSize = 44.sp
                )
            }
            Text(
                text = "这首曲子还没有歌词",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 22.dp)
            )
            Text(
                text = "但旋律会替你说话",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * 歌词行渲染：整行高亮当前句（逐字跳动已按反馈移除——行级时间戳和实际演唱对不齐，
 * 字效反而出戏）。已唱完的句子常亮，未唱的暗色，当前句主色加粗。
 */
@Composable
private fun LyricLineText(
    text: String,
    isCurrent: Boolean,
    progress: Float,
    primary: Color
) {
    val sung = primary
    val unsung = Color.White.copy(alpha = 0.45f)
    if (!isCurrent) {
        Text(
            text = text,
            color = if (progress >= 1f) Color.White.copy(alpha = 0.95f) else unsung,
            fontSize = 15.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    Text(
        text = text,
        color = sung,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
