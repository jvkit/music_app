package com.example.helloworld.data.remote

import androidx.compose.ui.graphics.Color
import com.example.helloworld.ui.home.Track

private val Palettes = listOf(
    listOf(Color(0xFFFF6B9E), Color(0xFFFFB36B)),
    listOf(Color(0xFF775CFF), Color(0xFFFF55D8)),
    listOf(Color(0xFF22C9E6), Color(0xFF6B7CFF)),
    listOf(Color(0xFFFF4F81), Color(0xFFB44CFF)),
    listOf(Color(0xFF7257FF), Color(0xFF23D5C3)),
    listOf(Color(0xFF283B78), Color(0xFF8D5BB5))
)

fun trackPalette(id: String) = Palettes[(id.hashCode() and Int.MAX_VALUE) % Palettes.size]

fun ApiTrack.toUiTrack(): Track {
    val palette = Palettes[(id.hashCode() and Int.MAX_VALUE) % Palettes.size]
    val cover = coverUrl
        ?.let { MusicApiClient.resolveServerUrl(it) }
        ?: MusicApiClient.thumbnailProxyUrl(thumbnail)
    return Track(
        id = id,
        title = title,
        artist = artist,
        duration = formatDuration(duration),
        colors = palette,
        durationSeconds = duration,
        source = source,
        coverUrl = cover,
        apiTrack = this
    )
}

fun ApiSong.toUiTrack(): Track = toApiTrack().toUiTrack()

private fun formatDuration(durationSeconds: Int?): String {
    if (durationSeconds == null || durationSeconds <= 0) return "--:--"
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatTrackDuration(durationSeconds: Int?): String = formatDuration(durationSeconds)
