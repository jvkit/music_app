package com.example.helloworld.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "im_messages")
data class ImMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 会话对端用户名（发件人或收件人） */
    val peer: String,
    /** true=我发的 */
    val outgoing: Boolean,
    /** chat / share_track / listen_invite */
    val msgType: String,
    val text: String = "",
    /** share_track 时的完整 ApiTrack JSON */
    val trackJson: String = "",
    val ts: Long,
    val read: Boolean = false
)
