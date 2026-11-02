package com.example.helloworld.playback

data class AsyncListState<T>(
    val loading: Boolean = false,
    val items: List<T> = emptyList(),
    val error: String? = null
)
