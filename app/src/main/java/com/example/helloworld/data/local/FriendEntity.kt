package com.example.helloworld.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val username: String,
    val since: Long,
    val online: Boolean = false
)
