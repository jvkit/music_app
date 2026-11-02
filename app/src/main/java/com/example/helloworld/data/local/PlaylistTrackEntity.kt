package com.example.helloworld.data.local

import androidx.room.Entity

/**
 * 歌单曲目行：position 参与主键，移动排序即重写 position。
 * trackJson 为完整 ApiTrack 元数据（Gson JSON），换设备登录后仅凭它
 * 即可重新解析播放；旧版迁移数据可能为空（无法跨设备重播，仅占位展示）。
 */
@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "position"])
data class PlaylistTrackEntity(
    val playlistId: String,
    val position: Int,
    val trackId: String,
    val trackJson: String
)
