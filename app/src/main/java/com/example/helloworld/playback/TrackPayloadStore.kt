package com.example.helloworld.playback

import com.example.helloworld.data.remote.ApiTrack
import java.util.concurrent.ConcurrentHashMap

object TrackPayloadStore {
    private val payloads = ConcurrentHashMap<String, ApiTrack>()
    private val resolvedUrls = ConcurrentHashMap<String, String>()

    fun put(trackId: String, payload: ApiTrack?) {
        if (payload != null) payloads[trackId] = payload
    }

    fun putAll(tracks: Map<String, ApiTrack?>) {
        tracks.forEach { (id, payload) -> put(id, payload) }
    }

    fun get(trackId: String): ApiTrack? = payloads[trackId]

    fun resolvedUrl(trackId: String): String? = resolvedUrls[trackId]

    fun cacheResolvedUrl(trackId: String, url: String) {
        resolvedUrls[trackId] = url
    }

    fun clearResolved(trackId: String) {
        resolvedUrls.remove(trackId)
    }

    /** 实际播放使用的音源（换源后可能与原始 source 不同），供 UI 展示 */
    private val playedSources = ConcurrentHashMap<String, String>()

    fun markPlayedSource(trackId: String, source: String) {
        playedSources[trackId] = source
    }

    fun playedSource(trackId: String): String? = playedSources[trackId]

    fun removePlayedSource(trackId: String) {
        playedSources.remove(trackId)
    }

    /** 疑似不完整的流地址（试听剪辑/截断文件），换源时不再选中 */
    private val blockedUrls = ConcurrentHashMap<String, MutableSet<String>>()

    fun blockUrl(trackId: String, url: String) {
        blockedUrls.getOrPut(trackId) { java.util.concurrent.CopyOnWriteArraySet() }.add(url)
    }

    fun isBlocked(trackId: String, url: String): Boolean = blockedUrls[trackId]?.contains(url) == true

    fun clearBlocked(trackId: String) {
        blockedUrls.remove(trackId)
    }
}
