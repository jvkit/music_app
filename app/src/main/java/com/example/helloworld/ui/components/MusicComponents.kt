package com.example.helloworld.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.helloworld.ui.home.Track

@Composable
fun TrackArtwork(
    track: Track,
    modifier: Modifier,
    cornerSize: Int,
    showLabel: Boolean
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerSize.dp))
            .background(Brush.linearGradient(track.colors))
    ) {
        track.coverUrl?.let { coverUrl ->
            AsyncImage(
                model = coverUrl,
                contentDescription = "${track.title}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 22.dp, y = (-20).dp)
                .size(100.dp)
                .background(Color.White.copy(alpha = if (track.coverUrl == null) 0.16f else 0.08f), CircleShape)
        )
        if (track.coverUrl == null) {
            Text(
                text = track.title.trim().take(1).ifBlank { "♪" },
                modifier = Modifier.align(Alignment.Center),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = if (showLabel) 54.sp else 23.sp,
                fontWeight = FontWeight.Black
            )
        }
        if (showLabel) {
            Text(
                text = "YINHE SELECT",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放 ${track.title}",
                    tint = Color(0xFF40215E),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    error: String?,
    isCached: Boolean = false,
    repeatModeLabel: String = "顺序",
    playedSourceLabel: String? = null,
    onCycleRepeatMode: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val status = when {
        !error.isNullOrBlank() -> "播放失败"
        isBuffering -> "正在缓冲"
        isPlaying -> "正在播放"
        else -> "已暂停"
    }
    val statusLine = buildString {
        if (isCached) append("本地缓存 · ")
        append(status)
        if (playedSourceLabel != null &&
            playedSourceLabel != com.example.helloworld.data.SourceCatalog.displayName(track.source)
        ) {
            append(" · 经")
            append(playedSourceLabel)
        }
        append(" · ")
        append(track.artist)
    }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtwork(
                    track = track,
                    modifier = Modifier
                        .size(46.dp)
                        .clickable(onClick = onOpenNowPlaying),
                    cornerSize = 14,
                    showLabel = false
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenNowPlaying)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = track.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = error ?: statusLine,
                        color = if (error != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isPlaying && !isBuffering) {
                    PlayingBars()
                }
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .clickable(onClick = onCycleRepeatMode)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = repeatModeLabel,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "上一首",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    if (isPlaying) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(2) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 4.dp, height = 17.dp)
                                        .background(Color.White, RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = Color.White
                        )
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "下一首",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(
                            Brush.horizontalGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun PlayingBars() {
    val transition = rememberInfiniteTransition(label = "playingBars")
    Row(
        modifier = Modifier.padding(end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val height by transition.animateFloat(
                initialValue = 6f,
                targetValue = 18f,
                animationSpec = infiniteRepeatable(
                    animation = tween(420 + index * 130),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape)
            )
        }
    }
}
