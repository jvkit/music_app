package com.example.helloworld.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 定时关闭（睡眠定时器）。
 *
 * 两种模式：
 * - 倒计时：N 分钟后暂停播放
 * - 播完当前首：切歌瞬间暂停（监听 currentMediaId 变化）
 *
 * 状态只活在内存里——重启 App 就失效，这是刻意的：睡眠定时器重启后还生效反而吓人。
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val onFire: () -> Unit
) {
    sealed interface State {
        data object Off : State
        /** 剩余毫秒 */
        data class Counting(val remainMs: Long) : State
        data object EndOfTrack : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state

    private var tickJob: Job? = null
    private var lastMediaId: String? = null
    private var watchingTrackEnd = false

    /** 设定 N 分钟后关闭（分钟数） */
    fun start(minutes: Int) {
        cancel()
        val totalMs = minutes * 60_000L
        val startedAt = System.currentTimeMillis()
        tickJob = scope.launch {
            while (true) {
                val remain = totalMs - (System.currentTimeMillis() - startedAt)
                if (remain <= 0) {
                    fire()
                    return@launch
                }
                _state.value = State.Counting(remain)
                delay(1_000)
            }
        }
    }

    /** 播完当前首再关 */
    fun startEndOfTrack(currentMediaId: String?) {
        cancel()
        lastMediaId = currentMediaId
        watchingTrackEnd = true
        _state.value = State.EndOfTrack
    }

    /** 当前歌曲切到下一首时调用；命中则触发关闭 */
    fun onMediaChanged(newMediaId: String?) {
        if (!watchingTrackEnd) return
        if (newMediaId == null || newMediaId == lastMediaId) return
        fire()
    }

    fun cancel() {
        tickJob?.cancel()
        tickJob = null
        watchingTrackEnd = false
        lastMediaId = null
        _state.value = State.Off
    }

    private fun fire() {
        tickJob?.cancel()
        tickJob = null
        watchingTrackEnd = false
        _state.value = State.Off
        onFire()
    }

    companion object {
        /** 常用档位（分钟）；0 代表「播完当前首」 */
        val PRESETS = listOf(15, 30, 45, 60, 90)
    }
}
