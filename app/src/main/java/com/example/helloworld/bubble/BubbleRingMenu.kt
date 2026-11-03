package com.example.helloworld.bubble

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 悬浮球长按后展开的环形菜单。
 *
 * 以悬浮球位置为圆心，无论精灵贴在屏幕哪个角落都在「该处」展开；
 * 半径会按剩余空间收敛，保证所有按钮不出屏。
 *
 * 排布（用户定义）：中心=播放/暂停，左右=上一曲/下一曲，
 * 左上=识曲(手机内/系统内录)、右上=识曲(手机外/麦克风)、左下=收藏、右下=进入软件、正下=关闭悬浮球。
 */
data class RingItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color = Color.White,
    /** 相对圆心的极角（弧度）。0 = 正右，逆时针为正。 */
    val angle: Double,
    /** 相对基础半径的倍数，外圈图标用 1.0，正下关闭项用 0.82 略收 */
    val radiusScale: Float = 1f,
)

object BubbleRingItems {
    const val PLAY_PAUSE = "play_pause"
    const val PREV = "prev"
    const val NEXT = "next"
    const val RECOGNIZE_SYSTEM = "recognize_system"
    const val RECOGNIZE_MIC = "recognize_mic"
    const val FAVORITE = "favorite"
    const val OPEN_APP = "open_app"
    const val CLOSE = "close"

    private fun deg(d: Double) = d * PI / 180.0

    /** 从正右开始逆时针：右→右上→上→左上→左→左下→右下 */
    val items: List<RingItem> = listOf(
        RingItem(NEXT, "下一曲", Icons.Default.KeyboardArrowRight, angle = deg(0.0)),
        RingItem(RECOGNIZE_SYSTEM, "识曲·内", Icons.Default.Search, accent = Color(0xFF6EE7F9), angle = deg(50.0)),
        RingItem(RECOGNIZE_MIC, "识曲·外", Icons.Default.Search, accent = Color(0xFFFCD34D), angle = deg(130.0)),
        RingItem(PREV, "上一曲", Icons.Default.KeyboardArrowLeft, angle = deg(180.0)),
        RingItem(FAVORITE, "收藏", Icons.Default.Favorite, accent = Color(0xFFF87171), angle = deg(230.0)),
        RingItem(OPEN_APP, "进入软件", Icons.Default.Menu, angle = deg(310.0)),
        RingItem(CLOSE, "关闭", Icons.Default.Star, accent = Color(0xFF94A3B8), angle = deg(270.0), radiusScale = 0.84f),
    )
}

/**
 * 环形菜单内容。坐标系原点=圆心，绘制在**根布局左上角偏移 (centerX, centerY)** 处。
 *
 * @param centerX 圆心相对屏幕左边缘的像素
 * @param centerY 圆心相对屏幕上边缘的像素
 * @param isPlaying 中心按钮显示暂停还是播放
 * @param onAction 选中某项（BubbleRingItems 的 id）
 * @param onDismiss 点空白/取消
 */
@Composable
fun BubbleRingMenu(
    centerX: Float,
    centerY: Float,
    isPlaying: Boolean,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val screenH = with(density) { config.screenHeightDp.dp.toPx() }

    // 圆心距四条边的最小余量，决定能张多大
    val room = min(min(centerX, screenW - centerX), min(centerY, screenH - centerY))
    val baseRadius = with(density) { 110.dp.toPx() }.coerceAtMost((room - 8f).coerceAtLeast(90f))
    val itemSize = with(density) { 54.dp.toPx() }
    val centerSize = with(density) { 64.dp.toPx() }

    val appear = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }

    fun choose(id: String) {
        if (closing) return
        closing = true
        onAction(id)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 点空白处收起（底下这层也吃掉事件，避免穿透到桌面）
            .clickable(indication = null, interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()) {
                if (!closing) onDismiss()
            }
    ) {
        BubbleRingItems.items.forEach { item ->
            val r = baseRadius * item.radiusScale
            val px = centerX + (cos(item.angle) * r).toFloat() - itemSize / 2
            val py = centerY - (sin(item.angle) * r).toFloat() - itemSize / 2
            RingButton(
                item = item,
                sizePx = itemSize,
                scale = appear.value,
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(px.toInt(), py.toInt()) },
            ) { choose(item.id) }
        }
        // 中心：播放/暂停
        Box(
            modifier = Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        (centerX - centerSize / 2).toInt(),
                        (centerY - centerSize / 2).toInt()
                    )
                }
                .size(with(density) { 64.dp })
                .scale(appear.value)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF6D5BFF), Color(0xFF3B2FBF))
                    )
                )
                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable { choose(BubbleRingItems.PLAY_PAUSE) },
            contentAlignment = Alignment.Center
        ) {
            if (isPlaying) {
                // material-icons-core 没有 Pause，两条竖杠手绘
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(width = 7.dp, height = 26.dp)
                                .background(Color.White, RoundedCornerShape(2.dp))
                        )
                    }
                }
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun RingButton(
    item: RingItem,
    sizePx: Float,
    scale: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val sizeDp = with(LocalDensity.current) { sizePx.toDp() }
    Box(modifier = modifier.size(sizeDp).scale(scale)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .size(sizeDp)
                .clip(CircleShape)
                .background(Color(0xE6202230))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .clickable { onClick() }
                .padding(2.dp)
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = item.accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                item.label,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
