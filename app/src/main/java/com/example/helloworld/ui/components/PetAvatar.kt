package com.example.helloworld.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.example.helloworld.data.remote.PetClipDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.roundToInt

/**
 * 宠物精灵：照抄素材包 musicpet.html 的演示逻辑（帧区间为"原子动作"）：
 * - 待机 = 在 stand / music(听歌中) 区间内游走（wander：变速+换向+撞边回弹）
 * - 互动 = 行为池随机（动作序列或单动作，部分带倒收回）
 * - 不镜像、不裁剪、整画布渲染（与网站版一致）
 *
 * 素材来源：优先 PetContentStore 的磁盘缓存（服务器下发，新增角色免发版），
 * 未下载时回退打包 assets/pet/boy（离线兜底）。动作表/帧率由服务器 manifest 决定。
 */
@Composable
fun PetAvatar(
    species: String,
    trigger: Long,
    size: Dp,
    musicPlaying: Boolean = false,
    animate: Boolean = false
) {
    val context = LocalContext.current
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var loadedKey by remember { mutableStateOf<String?>(null) }
    var decodedReq by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val characterId = PetContentStore.characterIdFor(species)
    val character = PetContentStore.characterFor(species)

    // 首次可见：恢复下载记录 + 确保该角色全部动画段就绪（后台下载，不阻塞渲染）
    LaunchedEffect(characterId) {
        PetContentStore.init(context)
        PetContentStore.ensureCharacter(context, characterId)
    }

    if (!animate) {
        // 静态预览：stand 首帧
        LaunchedEffect(size, characterId) {
            val key = "static-$size-$characterId"
            if (loadedKey != key) {
                loadedKey = key
                frame = decodeFrame(context, characterId, character, character.clips["stand"]!!.act, character.clips["stand"]!!.start)
                decodedReq = null
            }
        }
    } else {
        val engine = remember(characterId) { PetEngine(character.clips, character.fps) }
        // 外部触发（点击/投喂/对方云逗宠）：trigger 递增一次 = 做一次随机互动
        LaunchedEffect(trigger) {
            if (trigger > 0) engine.tap()
        }
        LaunchedEffect(musicPlaying) {
            engine.idleIsMusic = musicPlaying
        }
        // 主循环：帧时钟推进引擎 + 按需解码当前帧
        LaunchedEffect(engine, characterId) {
            val am = context.assets
            var lastNanos = 0L
            while (isActive) {
                val nanos = awaitFrame()
                if (lastNanos != 0L) {
                    val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
                    engine.advance(dt)
                }
                lastNanos = nanos
                val req = engine.frameRequest
                if (req != null && req != decodedReq) {
                    val bmp = withContext(Dispatchers.IO) { decodeFrame(context, characterId, character, req.first, req.second) }
                    if (bmp != null) {
                        decodedReq = req
                        frame = bmp
                    }
                }
            }
        }
    }

    val bmp = frame
    // 等价网站 drawImage(im,0,0,W,H)：每帧强制绘制到同一矩形，不经过 ContentScale/对齐计算
    androidx.compose.foundation.Canvas(
        modifier = Modifier.height(size).width(size * 3 / 4)
    ) {
        val b = bmp
        if (b != null && !b.isRecycled) {
            drawImage(
                image = b.asImageBitmap(),
                dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                dstSize = androidx.compose.ui.unit.IntSize(this.size.width.roundToInt(), this.size.height.roundToInt())
            )
        }
    }
}

/**
 * 帧解码：磁盘缓存（服务器下发的素材）优先，assets 内置 boy 兜底。
 * 全分辨率独立解码（不复用位图，帧不可变，渲染无撕裂）。
 */
private fun decodeFrame(
    context: android.content.Context,
    characterId: String,
    character: com.example.helloworld.data.remote.PetCharacterDef,
    act: Int,
    idx: Int
): Bitmap? {
    val f = PetContentStore.frameFile(context, characterId, "act$act", idx)
    if (f != null) {
        runCatching { return BitmapFactory.decodeFile(f.absolutePath) }
    }
    // 兜底：仅内置 boy 有打包素材
    val pattern = "pet/boy/inspect/act$act/frames/frame_%04d.webp"
    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    return runCatching {
        context.assets.open(String.format(pattern, idx)).use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}

/** 宠物引擎：待机游走 + 互动动作状态机（端口自 musicpet.html），clips/fps 由角色定义注入 */
private class PetEngine(
    private val clips: Map<String, PetClipDef>,
    private val fps: Float = 24f
) {
    var idleIsMusic = false
    var phase = "idle"
        private set
    var frameRequest: Pair<Int, Int>? = null
        private set

    private var ipos = 0f
    private var ivel = 20f
    private var iveldir = 1
    private var ivelT = 0f

    private var actKey: String? = null
    private var actIdx = 0f
    private var actDir = 1
    private var actEnd: (() -> Unit)? = null
    private var seq: List<String>? = null
    private var si = 0
    private val rnd = java.util.Random()

    private fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

    private fun hasClip(vararg keys: String) = keys.all { clips.containsKey(it) }

    fun advance(dt: Float) {
        if (phase == "idle") {
            val c = clips[if (idleIsMusic) "music" else "stand"] ?: clips.values.first()
            val n = c.end - c.start + 1
            wander(dt, n)
            frameRequest = c.act to c.start + ipos.roundToInt().coerceIn(0, n - 1)
        } else {
            val key = actKey
            val clip = key?.let { clips[it] }
            if (key == null || clip == null) { backIdle(); return }
            val n = clip.end - clip.start + 1
            actIdx += actDir * fps * dt
            val i = actIdx.roundToInt()
            when {
                actDir > 0 && i >= n - 1 -> {
                    frameRequest = clip.act to clip.end
                    val cb = actEnd; actEnd = null; cb?.invoke()
                    if (actEnd == null && phase == "action") backIdle()   // 安全兜底
                }
                actDir < 0 && i <= 0 -> {
                    frameRequest = clip.act to clip.start
                    val cb = actEnd; actEnd = null; cb?.invoke()
                    if (actEnd == null && phase == "action") backIdle()
                }
                else -> frameRequest = clip.act to clip.start + i.coerceIn(0, n - 1)
            }
        }
    }

    /** 待机游走：每 0.8~2.2s 换一次速度(16~26帧/s)、40% 概率换向，撞边回弹 */
    private fun wander(dt: Float, n: Int) {
        ivelT -= dt
        if (ivelT <= 0) {
            ivel = r(16f, 26f)
            if (rnd.nextFloat() < 0.4f) iveldir = -iveldir
            ivelT = r(0.8f, 2.2f)
        }
        ipos += iveldir * ivel * dt
        if (ipos <= 0) { ipos = 0f; iveldir = 1 }
        else if (ipos >= n - 1) { ipos = (n - 1).toFloat(); iveldir = -1 }
    }

    private fun playClip(key: String, dir: Int, cb: (() -> Unit)?) {
        phase = "action"
        actKey = key
        actDir = dir
        actIdx = if (dir > 0) 0f else (clips[key]!!.end - clips[key]!!.start).toFloat()
        actEnd = cb
    }

    private fun playSeq(keys: List<String>) {
        seq = keys
        si = 0
        playClip(keys[0], 1) { onSeqStep() }
    }

    private fun onSeqStep() {
        si++
        val s = seq
        if (s != null && si < s.size) playClip(s[si], 1) { onSeqStep() }
        else backIdle()
    }

    private fun playAtomic(key: String, dir: Int, ret: Boolean) {
        seq = null
        if (ret) playClip(key, dir) { playClip(key, -dir) { backIdle() } }
        else playClip(key, dir) { backIdle() }
    }

    private fun backIdle() {
        seq = null
        phase = "idle"
        ipos = 0f
        ivel = r(16f, 26f)
        iveldir = 1
        ivelT = 0f
    }

    /** 互动行为池：只收录 clips 齐全的行为（未来角色可只实现部分动作） */
    private val behaviors: List<() -> Unit> = buildList {
        if (hasClip("heart", "neck", "hop")) add({ playSeq(listOf("heart", "neck", "hop")) })
        if (hasClip("windmill", "windbounce")) add({ playSeq(listOf("windmill", "windbounce")) })
        if (hasClip("stretch", "hem")) add({ playSeq(listOf("stretch", "hem")) })
        if (hasClip("stretch")) add({ playAtomic("stretch", 1, false) })
        if (hasClip("thumbs")) add({ playAtomic("thumbs", 1, true) })
        if (hasClip("neckwear")) add({ playAtomic("neckwear", 1, true) })
        if (hasClip("intopocket")) {
            add({ playAtomic("intopocket", 1, false) })
            add({ playAtomic("intopocket", -1, false) })
        }
        if (hasClip("hop")) add({ playAtomic("hop", 1, false) })
        if (hasClip("bounce")) add({ playAtomic("bounce", 1, false) })
    }

    /** 点击互动：从可用行为池随机抽一个 */
    fun tap() {
        if (phase != "idle") return
        seq = null
        behaviors.randomOrNull()?.invoke()
    }
}

/** 宠物互动气泡文案池（与动作名解耦，随机展示；聊天页与精灵中心共用） */
val PET_BUBBLES = listOf(
    // 俏皮互动
    "摸摸头～", "蹭蹭你", "好耶！", "给你比个心", "转个圈圈", "眨眨眼",
    "哼，不理你了", "贴贴～", "抱抱你", "开心到转圈",
    // 小贴士
    "听歌记得休息眼睛哦", "耳机音量别太大啦", "深夜听歌别忘了喝水",
    "好歌要和朋友一起分享", "遇到喜欢的歌就收藏吧", "单曲循环也是一种幸福",
    "新歌单要不要建一个？", "下载下来，路上也能听",
    // 关心
    "今天过得怎么样", "累了吗，听首歌放松下", "有我在陪你听歌呀",
    "天气转凉记得添衣", "早点休息，别熬太晚", "有什么心事可以跟我说",
    // 诗意
    "此曲只应天上有", "歌声里藏着星星", "把烦恼调成静音",
    "耳机分你一半，世界也分你一半", "旋律是时间的礼物", "让这首歌陪你走一段路",
    "音乐响起，晚风都温柔了", "此刻刚好，歌也刚好"
)

/** 宠物点击用的无反馈 interactionSource（ripple 由调用方自行决定） */
@Composable
fun rememberNoRippleSource() = remember { MutableInteractionSource() }

/** 物种显示名（与 pet_api.SPECIES_SHOP 对应） */
fun petSpeciesName(species: String): String = when (species) {
    "miao" -> "喵球"
    "wang" -> "旺仔"
    "tiao" -> "跳跳"
    else -> PetContentStore.characterFor(species).name.ifBlank { "小河灵" }
}

/** 任务/动作显示名（与 pet_api.TASK_TEMPLATES / ACTIONS_POOL 对应） */
fun petTaskName(type: String): String = when (type) {
    "play_songs" -> "听歌"
    "search_songs" -> "搜索"
    "like_songs" -> "点赞"
    "listen_together" -> "一起听"
    "chat_msgs" -> "互发消息"
    "share_tracks" -> "分享歌曲"
    else -> type
}

fun petActionName(action: String): String = when (action) {
    "idle" -> "待机"
    "jump" -> "跳跃"
    "spin" -> "旋转"
    "dance" -> "跳舞"
    "sleep" -> "睡觉"
    "happy" -> "开心"
    "eat" -> "进食"
    "angry" -> "生气"
    "love" -> "比心"
    else -> action
}
