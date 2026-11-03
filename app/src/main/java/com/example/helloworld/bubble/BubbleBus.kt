package com.example.helloworld.bubble

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 悬浮球 ↔ 应用 的双向通道。
 * - 菜单命令（播放/识曲…）经 commands 流发给 MainViewModel 处理；
 * - 识曲请求同时触发 requestRecognize（应用可能已在后台，靠流唤醒 UI 并拉起前台）。
 */
object BubbleBus {
    enum class Command { PlayPause, Next, Previous, RecognizeMic, RecognizeSystem, Close }

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 16)
    val commands = _commands.asSharedFlow()

    private val _recognizeRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val recognizeRequests = _recognizeRequests.asSharedFlow()

    /**
     * 冷启动兜底：Activity 在 Compose 订阅前收到 intent 时，把模式存在这里，
     * 由 YinHeApp 首次组合时消费（SharedFlow 无 replay，不能只靠 emit）。
     */
    @Volatile
    private var pendingRecognize: String? = null

    fun send(command: Command) {
        _commands.tryEmit(command)
    }

    fun requestRecognize(mode: String) {
        pendingRecognize = mode
        _recognizeRequests.tryEmit(mode)
    }

    fun takePendingRecognize(): String? {
        val mode = pendingRecognize
        pendingRecognize = null
        return mode
    }

    @Volatile
    var isEnabled: Boolean = false
}
