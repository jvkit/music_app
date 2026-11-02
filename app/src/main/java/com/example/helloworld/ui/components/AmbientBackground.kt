package com.example.helloworld.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AmbientBackground() {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = -40f,
        targetValue = 70f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0913))
    ) {
        Box(
            modifier = Modifier
                .offset(x = (-90).dp, y = (60 + drift).dp)
                .size(330.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF802C78).copy(alpha = 0.42f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .offset(x = (150 - drift).dp, y = 330.dp)
                .size(390.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF293E89).copy(alpha = 0.32f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .offset(x = 40.dp, y = (690 - drift).dp)
                .size(300.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF17616C).copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )
    }
}
