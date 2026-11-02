package com.example.helloworld.ui.components

import com.example.helloworld.data.remote.PetBehaviors
import com.example.helloworld.data.remote.PetClipDef
import com.example.helloworld.data.remote.PetRoleSet
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 通用宠物动画状态机（纯 Kotlin，不依赖 Compose）。
 *
 * 只依赖 manifest 的语义角色 / 行为池 / 已解锁动作，不再写死任何 clip 名：
 * idle / music / click / random categories / sleep / events。
 */
class PetAnimationEngine(
    private val clips: Map<String, PetClipDef>,
    private val roles: PetRoleSet,
    private val behaviors: PetBehaviors,
    private val defaultFps: Float,
    private val starter: Set<String>,
    private val unlocked: Set<String>,
    /** 商店在售的动作 id：未购买时一律不允许播放（防止被 roles 引用后绕过购买限制） */
    private val sellable: Set<String> = emptySet(),
    private val rnd: Random = Random.Default,
) {
    enum class Phase { IDLE, ACTION, SLEEP }

    var phase: Phase = Phase.IDLE
        private set
    var frameRequest: Pair<Int, Int>? = null
        private set
    var facing: Int = 1
        private set

    private var idleMusic = false
    private var idleSeconds = 0f
    private var nextRandomAt = randomInterval()
    private var sleepStage = 0

    private var clipName: String? = null
    private var framePos = 0f
    private var frameDir = 1
    private var looping = true
    private var pingpong = false
    private var onFinish: (() -> Unit)? = null

    init {
        beginIdle()
    }

    fun setMusic(playing: Boolean) {
        if (idleMusic == playing) return
        idleMusic = playing
        if (phase == Phase.IDLE) beginIdle()
    }

    /** 用户点击/云逗宠 */
    fun tap() {
        when (phase) {
            Phase.SLEEP -> wake()
            Phase.IDLE -> {
                idleSeconds = 0f
                val pool = roles.click.filter(::allowed)
                if (pool.isNotEmpty()) {
                    playOnce(pool.random(rnd))
                } else {
                    playRandomCategory()
                }
            }
            Phase.ACTION -> Unit // 动作中不打断
        }
    }

    /** 外部事件：balance / whisper / workStatus / celebrate / error ... */
    fun triggerEvent(kind: String, index: Int? = null) {
        val list = roles.events[kind]?.filter(::allowed)
        if (list.isNullOrEmpty()) return
        val name = if (index != null && index in list.indices) list[index] else list.random(rnd)
        playOnce(name)
    }

    /** 强制播放某个已解锁动作（聊天动作联动 / 调试） */
    fun playAction(actionId: String): Boolean {
        val name = resolveClip(actionId) ?: return false
        if (!allowed(name)) return false
        playOnce(name)
        return true
    }

    fun advance(dt: Float) {
        when (phase) {
            Phase.SLEEP -> advanceSleep(dt)
            Phase.ACTION -> advanceAction(dt)
            Phase.IDLE -> advanceIdle(dt)
        }
    }

    // ===================== idle =====================

    private fun advanceIdle(dt: Float) {
        idleSeconds += dt
        if (idleSeconds >= 60f) {
            if (startSleep()) return
        }
        if (!idleMusic && idleSeconds >= nextRandomAt) {
            if (playRandomCategory()) return
        }
        val name = idleClipName() ?: return
        if (clipName != name) {
            clipName = name
            framePos = 0f
            frameDir = 1
            looping = true
            pingpong = false
        }
        stepFrame(dt)
    }

    private fun beginIdle() {
        phase = Phase.IDLE
        idleSeconds = 0f
        nextRandomAt = randomInterval()
        clipName = null
        onFinish = null
    }

    private fun returnIdle() {
        beginIdle()
    }

    private fun idleClipName(): String? {
        val preferred = if (idleMusic) roles.music else roles.idle
        return preferred.firstOrNull(::allowed)
            ?: roles.idle.firstOrNull(::allowed)
            ?: clips.keys.firstOrNull()
    }

    private fun playRandomCategory(): Boolean {
        val cats = behaviors.categories.mapNotNull { cat ->
            val actions = cat.actions.filter(::allowed)
            if (actions.isEmpty()) null else cat to actions
        }
        if (cats.isEmpty()) return false
        val total = cats.sumOf { it.first.weight.coerceAtLeast(1) }
        var roll = rnd.nextInt(total.coerceAtLeast(1))
        val chosen = cats.firstOrNull { cat ->
            roll -= cat.first.weight.coerceAtLeast(1)
            roll < 0
        }?.second ?: cats.first().second
        val name = chosen.random(rnd)
        nextRandomAt = randomInterval()
        playOnce(name)
        return true
    }

    private fun randomInterval(): Float = 8f + rnd.nextFloat() * 12f

    // ===================== action =====================

    private fun playOnce(name: String) {
        val c = clips[name] ?: return
        phase = Phase.ACTION
        clipName = name
        framePos = if (c.mode == "reverse") (c.end - c.start).toFloat() else 0f
        frameDir = if (c.mode == "reverse") -1 else 1
        looping = false
        pingpong = c.mode == "pingpong"
        onFinish = { returnIdle() }
    }

    /** 图鉴预览：循环播放某个动作（不回到待机），忽略已解锁限制（由调用方保证） */
    fun playLoop(name: String): Boolean {
        val c = clips[name] ?: return false
        phase = Phase.ACTION
        clipName = name
        framePos = if (c.mode == "reverse") (c.end - c.start).toFloat() else 0f
        frameDir = if (c.mode == "reverse") -1 else 1
        looping = true
        pingpong = c.mode == "pingpong"
        onFinish = { playLoop(name) }
        return true
    }

    private fun advanceAction(dt: Float) {
        val name = clipName
        val c = name?.let { clips[it] }
        if (c == null) { returnIdle(); return }
        val n = (c.end - c.start + 1).coerceAtLeast(1)
        val fps = c.fps ?: defaultFps
        framePos += frameDir * fps * dt
        when {
            pingpong && frameDir > 0 && framePos >= n - 1 -> {
                framePos = (n - 1).toFloat()
                frameDir = -1
            }
            pingpong && frameDir < 0 && framePos <= 0 -> {
                framePos = 0f
                finishAction()
                return
            }
            !pingpong && frameDir > 0 && framePos >= n - 1 -> {
                framePos = (n - 1).toFloat()
                finishAction()
                return
            }
            !pingpong && frameDir < 0 && framePos <= 0 -> {
                framePos = 0f
                finishAction()
                return
            }
        }
        frameRequest = c.act to (c.start + framePos.roundToInt().coerceIn(0, n - 1))
    }

    private fun finishAction() {
        val cb = onFinish
        onFinish = null
        if (cb != null) cb() else returnIdle()
    }

    private fun stepFrame(dt: Float) {
        val name = clipName ?: return
        val c = clips[name] ?: return
        val n = (c.end - c.start + 1).coerceAtLeast(1)
        val fps = c.fps ?: defaultFps
        framePos += frameDir * fps * dt
        if (looping) {
            if (framePos >= n) framePos %= n
            if (framePos < 0) framePos = (framePos % n + n) % n
        } else if (framePos >= n - 1) {
            framePos = (n - 1).toFloat()
        }
        frameRequest = c.act to (c.start + framePos.roundToInt().coerceIn(0, n - 1))
    }

    // ===================== sleep =====================

    private fun startSleep(): Boolean {
        val start = roles.sleepStart.filter(::allowed)
        val loop = roles.sleepLoop.filter(::allowed)
        if (start.isEmpty() && loop.isEmpty()) return false
        phase = Phase.SLEEP
        sleepStage = 0
        idleSeconds = 0f
        if (start.isNotEmpty()) {
            playSleepClip(start.random(rnd), next = { beginSleepLoop() })
        } else {
            beginSleepLoop()
        }
        return true
    }

    private fun beginSleepLoop() {
        val loop = roles.sleepLoop.filter(::allowed)
        if (loop.isEmpty()) { wake(); return }
        phase = Phase.SLEEP
        sleepStage = 1
        clipName = loop.random(rnd)
        framePos = 0f
        frameDir = 1
        looping = true
        pingpong = false
        stepFrame(0f)
    }

    private fun advanceSleep(dt: Float) {
        if (sleepStage == 0) {
            // 入睡过渡动作按 ACTION 规则推进
            val before = phase
            phase = Phase.ACTION
            advanceAction(dt)
            if (phase == Phase.IDLE) { // 过渡结束回调已切回 idle 的兜底
                phase = before
            }
        } else {
            stepFrame(dt)
        }
    }

    private fun wake() {
        val end = roles.sleepEnd.filter(::allowed)
        if (end.isEmpty()) { returnIdle(); return }
        phase = Phase.ACTION
        sleepStage = 0
        playOnce(end.random(rnd))
    }

    private fun playSleepClip(name: String, next: () -> Unit) {
        val c = clips[name] ?: return
        phase = Phase.SLEEP
        clipName = name
        framePos = 0f
        frameDir = 1
        looping = false
        pingpong = c.mode == "pingpong"
        onFinish = next
    }

    // ===================== helpers =====================

    /**
     * 该 clip 是否允许播放（同时也是"是否允许触发下载"的准绳）。
     *
     * 规则：starter + 已购买解锁的才放行；角色槽位（idle/music/drag/sleep 等）里的 clip
     * 只有在"不属于任何可售卖动作"时才免费——避免商店里卖的动作因为被写进 roles
     * 就绕过购买限制、被随机池触发并下载。
     */
    private fun allowed(name: String): Boolean {
        if (name !in clips) return false
        if (name in starter || name in unlocked) return true
        // 可售卖动作必须买过才能播
        if (name in sellable) return false
        return name in roles.idle || name in roles.music || name in roles.click ||
            name in roles.sleepStart || name in roles.sleepLoop || name in roles.sleepEnd ||
            name in roles.move || name in roles.turn || name in roles.drag ||
            roles.events.values.any { name in it }
    }

    private fun resolveClip(actionId: String): String? {
        if (actionId in clips) return actionId
        return null
    }
}
