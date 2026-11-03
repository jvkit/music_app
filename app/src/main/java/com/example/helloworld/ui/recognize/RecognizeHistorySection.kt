package com.example.helloworld.ui.recognize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import com.example.helloworld.data.remote.toUiTrack
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helloworld.MainViewModel
import com.example.helloworld.data.local.RecognizeHistoryEntity

/**
 * 识曲历史。
 *
 * 一次识别常常匹配到**多首**（同名的不同版本），所以每条记录可展开看全部，
 * 而不是只留第一首——播放页的队列也是这么来的。
 */
@Composable
fun RecognizeHistorySection(viewModel: MainViewModel) {
    val history by viewModel.recognizeHistory.collectAsState()
    if (history.isEmpty()) return

    var expandedId by remember { mutableStateOf<Long?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "识曲历史",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        Text(
            "清空",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { viewModel.clearRecognizeHistory() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.heightIn(max = 240.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(history, key = { it.id }) { item ->
            val tracks = remember(item.id) { viewModel.tracksOfHistory(item) }
            val expanded = expandedId == item.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                    .clickable { expandedId = if (expanded) null else item.id }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        val first = tracks.firstOrNull()
                        Text(
                            first?.title ?: "未识别到歌曲",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildString {
                                if (first != null && !first.artist.isNullOrBlank()) append(first.artist)
                                if (tracks.size > 1) append("  等 ${tracks.size} 首")
                                if (first == null && item.note.isNotBlank()) append(item.note)
                                append("  ·  ")
                                append(formatTime(item.ts))
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (tracks.size > 1) {
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (expanded && tracks.isNotEmpty()) {
                    tracks.forEach { api ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val ui = runCatching { api.toUiTrack() }.getOrNull() ?: return@clickable
                                    val all = tracks.mapNotNull { runCatching { it.toUiTrack() }.getOrNull() }
                                    viewModel.playTrack(ui, all)
                                }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.padding(start = 6.dp)) {
                                Text(
                                    api.title,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    api.artist.orEmpty(),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ts: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val now = java.util.Calendar.getInstance()
    val sameDay = cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
        cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
    val hm = String.format(
        java.util.Locale.US, "%02d:%02d",
        cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)
    )
    return if (sameDay) "今天 $hm" else String.format(
        java.util.Locale.US, "%02d-%02d %s",
        cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH), hm
    )
}

/** collectAsState 的小包装，避免各文件重复 import */
