package com.example.helloworld.ui.recognize

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helloworld.MainViewModel

/**
 * 识曲状态小窗（全局浮层，不占满屏）。
 *
 * 存在的理由：系统内录是"静默进行"的，用户听不到反馈，不知道
 * 现在录没在录、录到哪儿了、什么时候有结果。所以收成一个常驻小胶囊：
 * - recording：进度条 + 秒数
 * - uploading：转圈「正在识别」
 * - done：直接显示匹配到的歌名，点一下就能播
 * - 失败：一句话说明
 *
 * 只在**没打开全屏识曲页**时出现（那里已经有完整界面了）。
 */
@Composable
fun RecognizeStatusChip(viewModel: MainViewModel) {
    val phase = viewModel.recognizePhase
    val visible = phase != "idle"
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { with(density) { -it } },
        exit = slideOutVertically { with(density) { -it } }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF21C1B24))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (phase) {
                    "recording" -> {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFFF87171))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "正在听… ${(viewModel.recognizeProgress * AudioCaptures.RECORD_SECONDS).toInt()}s",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            LinearProgressIndicator(
                                progress = { viewModel.recognizeProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFFF87171),
                                trackColor = Color.White.copy(alpha = 0.15f)
                            )
                        }
                    }
                    "uploading" -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Text(
                            "正在识别…",
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    "done" -> {
                        val first = viewModel.recognizeResults.firstOrNull()
                        val err = viewModel.recognizeError
                        if (first != null) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(18.dp)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        viewModel.playTrack(first, viewModel.recognizeResults)
                                        viewModel.resetRecognize()
                                    }
                            ) {
                                Text(
                                    first.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${first.artist} · 点击播放",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "没识别出来",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    err ?: "离音源近一点，或换个段落再试",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = { viewModel.resetRecognize() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
