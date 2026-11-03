package com.example.helloworld.bubble

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 悬浮球 ↔ 应用 的双向通道。
 * - 菜单命令（播放/识曲…）经 commands 流发给 MainViewModel 处理；
 * - 识曲请求同时触发 requestRecognize（应用可能已在后台，靠流唤醒 UI 并拉起前台）。
 */
object BubbleBus {
    enum class Command { PlayPause, Next, Previous, RecognizeMic, RecognizeSystem, Favorite, OpenApp, Close }

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 16)
    val commands = _commands.asSharedFlow()

    private val _recognizeRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val recognizeRequests = _recognizeRequests.asSharedFlow()

    /**
     * 播放状态回传（UI → 悬浮球）：环形菜单中心按钮据此显示暂停还是播放。
     * 悬浮球服务与 App 同进程，用普通 volatile 即可，不必走流。
     */
    @Volatile
    var isPlaying: Boolean = false

    /** 当前歌曲是否已收藏：环形菜单收藏项据此显示红心/空心 */
    @Volatile
    var isFavorited: Boolean = false

    /** 是否真的有歌在播（没有时收藏项置灰，点了没意义） */
    @Volatile
    var hasTrack: Boolean = false

    /** 当前角色已购买/已解锁的动作：悬浮球引擎靠它决定点击/随机能播哪些 */
    @Volatile
    var unlockedActions: Set<String> = emptySet()

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
