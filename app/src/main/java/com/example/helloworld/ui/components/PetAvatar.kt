package com.example.helloworld.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.example.helloworld.data.remote.PetCharacterDef
import com.example.helloworld.data.remote.PetDisplayDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.roundToInt

/**
 * 宠物精灵（v3）：manifest 语义角色驱动，支持 idle/music/点击/随机池/睡觉/事件动作。
 * 帧从 PetFrameCache 读取，下载由 PetContentStore 按 pack 懒加载。
 */
@Composable
fun PetAvatar(
    species: String,
    trigger: Long,
    size: Dp,
    musicPlaying: Boolean = false,
    animate: Boolean = false,
    unlockedActions: Set<String> = emptySet(),
    externalEvent: String? = null,
    externalEventSeq: Long = 0,
    eventIndex: Int? = null,
    /** 正在被拖拽：循环播放 roles.drag（无专属片段的角色回退到 move/turn） */
    dragging: Boolean = false,
    /**
     * 是否应用 manifest 的 display 位置/缩放修正。
     * 只给投喂页的大角色开：缩略图（商店/图鉴/列表）和聊天页小宠物按原始比例铺满即可，
     * 套用大角色的缩小参数会变得极小。
     */
    applyDisplay: Boolean = false,
    /** 图鉴预览：循环播放指定 clip（非空时忽略交互，持续循环） */
    loopClip: String? = null,
) {
    val context = LocalContext.current
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var loadedKey by remember { mutableStateOf<String?>(null) }
    var decodedReq by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // 按需下载状态：当前已请求下载的 act、下载协程
    var requestedAct by remember { mutableIntStateOf(0) }
    var actJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val characterId = PetContentStore.characterIdFor(species)
    val character = PetContentStore.characterFor(species)

    // 首次可见：只确保 starter 所需的包（core）
    LaunchedEffect(characterId) {
        PetContentStore.init(context)
        PetContentStore.ensureCharacter(context, characterId)
    }
    // 注意：已解锁的动作不再批量预下载——按需在真正播放到该 act 时才下载（见下方帧循环）

    if (!animate) {
        LaunchedEffect(size, characterId) {
            val key = "static-$size-$characterId"
            if (loadedKey != key) {
                loadedKey = key
                val stand = character.clips["stand"] ?: character.clips.values.firstOrNull()
                frame = stand?.let { decodeFrame(context, characterId, it.act, it.start) }
                decodedReq = null
            }
        }
    } else {
        val engine = remember(characterId, character, unlockedActions) {
            PetAnimationEngine(
                clips = character.clips,
                roles = character.roles,
                behaviors = character.behaviors,
                defaultFps = character.fps,
                starter = character.starter.toSet(),
                unlocked = unlockedActions,
                sellable = PetContentStore.sellableClips(characterId),
            )
        }
        // 图鉴预览：循环播放指定动作，退出预览时恢复待机
        LaunchedEffect(loopClip) {
            if (!loopClip.isNullOrBlank()) {
                engine.playLoop(loopClip)
            } else {
                engine.setMusic(musicPlaying)
            }
        }
        LaunchedEffect(trigger) {
            if (loopClip.isNullOrBlank() && trigger > 0) engine.tap()
        }
        LaunchedEffect(musicPlaying) {
            engine.setMusic(musicPlaying)
        }
        LaunchedEffect(externalEventSeq) {
            if (externalEventSeq > 0 && externalEvent != null) {
                if (!engine.playAction(externalEvent)) {
                    engine.triggerEvent(externalEvent, eventIndex)
                }
            }
        }
        // 拖拽：起手播 drag 片段，松手回待机
        LaunchedEffect(dragging) {
            if (dragging) {
                val dragClip = (character.roles.drag + character.roles.move + character.roles.turn)
                    .firstOrNull { it in character.clips }
                if (dragClip != null) engine.playAction(dragClip)
            } else {
                engine.setMusic(musicPlaying)
            }
        }
        // 主循环：时间轴推进 + 按需解码当前帧
        LaunchedEffect(engine, characterId) {
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
                    val bmp = withContext(Dispatchers.IO) { decodeFrame(context, characterId, req.first, req.second) }
                    if (bmp != null) {
                        decodedReq = req
                        frame = bmp
                    }
                }
                // 按需下载：当前播放的 act 未就绪时后台拉取（不预下载整角色，也不阻塞渲染）
                if (req != null && req.first != requestedAct) {
                    requestedAct = req.first
                    val act = "act$requestedAct"
                    if (!PetContentStore.isActReady(characterId, act)) {
                        actJob?.cancel()
                        actJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            PetContentStore.ensureAct(context, characterId, act)
                            // 下载解压完成后重新解码当前帧
                            withContext(Dispatchers.Main) { decodedReq = null }
                        }
                    }
                }
            }
        }
    }

    val bmp = frame
    androidx.compose.foundation.Canvas(
        modifier = Modifier.height(size).width(size * 3 / 4)
    ) {
        val b = bmp
        if (b != null && !b.isRecycled) {
            // display 只作用于投喂页大角色；缩略图/聊天页小宠物按原始比例铺满
            val d = if (applyDisplay) character.display else PetDisplayDef()
            val s = d.scale.coerceIn(0.3f, 3f)
            val w = this.size.width * s
            val h = this.size.height * s
            val dx = this.size.width * d.offsetX
            val dy = this.size.height * d.offsetY
            drawImage(
                image = b.asImageBitmap(),
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    ((this.size.width - w) / 2f + dx).roundToInt(),
                    ((this.size.height - h) / 2f + dy).roundToInt(),
                ),
                dstSize = androidx.compose.ui.unit.IntSize(w.roundToInt(), h.roundToInt())
            )
        }
    }
}

/** 帧 LRU 缓存：360x480 ARGB 单帧约 0.66MB，按内存预算淘汰 */
object PetFrameCache {
    private val maxBytes = minOf(64L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8)
    private val cache = object : LruCache<String, Bitmap>((maxBytes / 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bmp: Bitmap) { cache.put(key, bmp) }
    fun clear() = cache.evictAll()
}

/** 帧解码：磁盘缓存（服务器下发的素材）优先，assets 内置 boy 兜底。 */
private fun decodeFrame(
    context: android.content.Context,
    characterId: String,
    act: Int,
    idx: Int,
): Bitmap? {
    val key = "$characterId/$act/$idx"
    PetFrameCache.get(key)?.let { return it }

    val f = PetContentStore.frameFile(context, characterId, "act$act", idx)
    if (f != null) {
        runCatching {
            val bmp = BitmapFactory.decodeFile(f.absolutePath)
            if (bmp != null) PetFrameCache.put(key, bmp)
            return bmp
        }
    }
    // 兜底：仅内置 boy 有打包素材
    val pattern = "pet/boy/inspect/act$act/frames/frame_%04d.webp"
    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    return runCatching {
        context.assets.open(String.format(pattern, idx)).use {
            val bmp = BitmapFactory.decodeStream(it, null, opts)
            if (bmp != null) PetFrameCache.put(key, bmp)
            bmp
        }
    }.getOrNull()
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

/** 物种显示名（优先 manifest 角色名，旧版内置别名兜底） */
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
