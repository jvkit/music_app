package com.example.helloworld.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xB8352946), Color(0x941B2839))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))
                ),
                shape = shape
            ),
        content = { content() }
    )
}

@Composable
fun LiquidBottomBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = listOf("首页", "发现", "精灵", "我的")
    val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Star, Icons.Default.Person)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xD9362946), Color(0xC9182435))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
    ) {
        val segmentWidth = maxWidth / labels.size
        val segmentWidthPx = with(density) { segmentWidth.toPx() }
        var dragging by remember { mutableStateOf(false) }
        var dragStartIndex by remember { mutableIntStateOf(selectedIndex) }
        var dragAmount by remember { mutableFloatStateOf(0f) }
        val animatedIndex by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f),
            label = "glassTab"
        )
        val displayIndex = if (dragging) {
            (dragStartIndex + dragAmount / segmentWidthPx).coerceIn(0f, 1f)
        } else {
            animatedIndex
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(segmentWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragStartIndex = selectedIndex
                            dragAmount = 0f
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dragAmount += amount
                        },
                        onDragEnd = {
                            val target = (dragStartIndex + dragAmount / segmentWidthPx)
                                .roundToInt()
                                .coerceIn(0, labels.lastIndex)
                            dragging = false
                            onSelected(target)
                        },
                        onDragCancel = { dragging = false }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((displayIndex * segmentWidthPx).roundToInt(), 0) }
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0x99FF55D8), Color(0x889E82FF))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            )
            Row(modifier = Modifier.fillMaxSize()) {
                labels.forEachIndexed { index, label ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (pressed) 0.86f else 1f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                        label = "tabPress"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onSelected(index) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icons[index],
                            contentDescription = label,
                            tint = if (selectedIndex == index) Color.White else Color(0xFFA99CB8),
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        )
                        Text(
                            text = label,
                            color = if (selectedIndex == index) {
                                Color.White
                            } else {
                                Color(0xFFA99CB8)
                            },
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
