package com.example.helloworld.ui.home

import androidx.compose.ui.graphics.Color
import com.example.helloworld.data.remote.ApiTrack

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val colors: List<Color>,
    val durationSeconds: Int? = null,
    val source: String = "local",
    val coverUrl: String? = null,
    val apiTrack: ApiTrack? = null
)

val PlaceholderTrack = Track(
    id = "placeholder",
    title = "等待播放",
    artist = "音河音乐",
    duration = "--:--",
    colors = listOf(Color(0xFF2A2440), Color(0xFF5D3A88))
)
