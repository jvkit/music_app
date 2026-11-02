package com.example.helloworld.data.model

data class Playlist(
    val id: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val trackIds: List<String>
)

const val VIRTUAL_FAVORITES_ID = "virtual:favorites"
const val VIRTUAL_LOCAL_ID = "virtual:local"

val VirtualFavoritesPlaylist = Playlist(
    id = VIRTUAL_FAVORITES_ID,
    name = "我的收藏",
    description = "点过爱心的歌，都住在这里",
    createdAt = 0L,
    trackIds = emptyList()
)

val VirtualLocalPlaylist = Playlist(
    id = VIRTUAL_LOCAL_ID,
    name = "本地歌曲",
    description = "边听边存，没网也能放",
    createdAt = 0L,
    trackIds = emptyList()
)
