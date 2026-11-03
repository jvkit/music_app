package com.example.helloworld.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 悬浮球旁边的识曲小窗。
 *
 * 为什么在这儿：内录要识别的是**别的 App** 正在放的声音，用户全程不在音河里，
 * 所以结果必须浮在桌面上贴着精灵出现，而不是回 App 才看得到。
 *
 * 三种形态：录制中（进度）/ 识别中（转圈）/ 出结果（歌名，点一下回 App 播）。
 */
@Composable
fun BubbleRecognizeCard(
    phase: String,
    progress: Float,
    seconds: Int,
    title: String,
    artist: String,
    error: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF21C1B24))
            .clickable { if (phase == "done") onOpen() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (phase) {
            "recording" -> {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF87171))
                )
                Column(modifier = Modifier.width(150.dp)) {
                    Text(
                        "正在听… ${(progress * seconds).toInt()}s",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFF87171),
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }
            }
            "uploading" -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Text("正在识别…", color = Color.White, fontSize = 12.sp)
            }
            else -> {
                if (title.isNotBlank()) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.widthIn(max = 170.dp)) {
                        Text(
                            title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (artist.isBlank()) "点击播放" else "$artist · 点击播放",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Column(modifier = Modifier.widthIn(max = 170.dp)) {
                        Text(
                            "没识别出来",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            error.ifBlank { "换个段落再试" },
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Icon(
            Icons.Default.Close,
            contentDescription = "关闭",
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier
                .size(16.dp)
                .clickable { onDismiss() }
        )
    }
}
