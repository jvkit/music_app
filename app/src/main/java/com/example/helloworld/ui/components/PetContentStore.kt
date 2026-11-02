package com.example.helloworld.ui.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.helloworld.data.remote.MusicApiClient
import com.example.helloworld.data.remote.PetCharacterDef
import com.example.helloworld.data.remote.PetClipDef
import com.example.helloworld.data.remote.PetPackDef
import com.example.helloworld.data.remote.PetRoleSet
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PetDownloadProgress(
    val character: String,
    val act: String,
    val fraction: Float,
)

/**
 * 宠物素材内容库（manifest v3）：
 * - 角色按 pack 拆包，客户端按需下载（首次只下 starter 所在的 core 包）
 * - manifest.version 变化会清空已下载标记（旧缓存失效）
 * - 下载进度通过 [progress] 暴露给 UI
 */
object PetContentStore {

    /** 内置 boy 清单：与 assets/pet/boy、服务端内置清单一致（离线兜底） */
    private val BOY = PetCharacterDef(
        name = "小河灵",
        fps = 24f,
        acts = emptyList(),
        builtin = true,
        starter = listOf("stand", "music", "heart", "neck", "hop", "stretch", "bounce"),
        roles = PetRoleSet(
            idle = listOf("stand"),
            music = listOf("music"),
            click = listOf("heart", "neck", "hop"),
        ),
        clips = mapOf(
            "stand" to PetClipDef(1, 33, 129),
            "music" to PetClipDef(3, 32, 76),
            "intopocket" to PetClipDef(1, 0, 33),
            "stretch" to PetClipDef(1, 129, 207),
            "hem" to PetClipDef(1, 207, 240),
            "bounce" to PetClipDef(2, 0, 58),
            "heart" to PetClipDef(2, 58, 120),
            "neck" to PetClipDef(2, 120, 187),
            "hop" to PetClipDef(2, 193, 221),
            "puton" to PetClipDef(3, 0, 32),
            "neckwear" to PetClipDef(3, 76, 100),
            "thumbs" to PetClipDef(3, 100, 124),
            "windmill" to PetClipDef(3, 124, 183),
            "windbounce" to PetClipDef(3, 183, 240),
        ),
    )

    var characters by mutableStateOf<Map<String, PetCharacterDef>>(mapOf("boy" to BOY))
        private set

    /** 商店动作目录（manifest.actions）：character -> 可售 clip 名集合 */
    private var actionsByCharacter: Map<String, Set<String>> = emptyMap()

    /** 该角色在商店出售的 clip 名：未购买时引擎不允许播放/下载 */
    fun sellableClips(characterId: String): Set<String> = actionsByCharacter[characterId].orEmpty()

    val progress = MutableStateFlow<PetDownloadProgress?>(null)

    /** 已下载解压完成的 "character/act" */
    private val readyActs = ConcurrentHashMap.newKeySet<String>()
    private val actMutexes = ConcurrentHashMap<String, Mutex>()
    /** refresh 并发保护：多处会同时触发拉清单，串行化避免 characters 反复替换 */
    private val refreshMutex = Mutex()
    private var refreshTried = false
    /** 已应用的清单版本：同版本不再重复替换 characters */
    private var appliedVersion = -1
    /** 正在刷新：并发调用直接复用，避免同一时刻多次拉取 */
    @Volatile
    private var refreshing = false
    private var prefs: android.content.SharedPreferences? = null
    private var contentVersion = 1

    /** 物种 -> 角色 id：manifest 里有对应角色就用，default 映射到 boy，否则 boy */
    fun characterIdFor(species: String): String {
        if (species == "default" || species.isBlank()) return "boy"
        return if (characters.containsKey(species)) species else "boy"
    }

    fun characterFor(species: String): PetCharacterDef {
        val id = characterIdFor(species)
        return characters[id] ?: characters["boy"] ?: BOY
    }

    fun isActReady(character: String, act: String): Boolean = "$character/$act" in readyActs

    /** 帧文件：仅当对应素材段已下载解压才返回（否则走 assets 兜底） */
    fun frameFile(context: Context, character: String, act: String, idx: Int): java.io.File? {
        if (!isActReady(character, act)) return null
        val dir = java.io.File(context.filesDir, "pet/$character/$act")
        val framed = java.io.File(dir, String.format(Locale.US, "frames/frame_%04d.webp", idx))
        if (framed.exists()) return framed
        val flat = java.io.File(dir, String.format(Locale.US, "frame_%04d.webp", idx))
        return if (flat.exists()) flat else null
    }

    /** 拉取素材清单（进宠物中心时调）；失败静默用内置 boy。并发调用串行化，避免反复替换 characters 导致画面闪切 */
    suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        try {
            refreshMutex.withLock {
                runCatching { MusicApiClient.service.petContentManifest() }
                    .onSuccess { m ->
                        val map = m.characters.filterValues { it.clips.isNotEmpty() }
                        if (map.isEmpty()) return@onSuccess
                        // 版本相同：什么都不动（不重建引擎、不清帧缓存、不触发重新下载）
                        if (m.version == appliedVersion) return@onSuccess
                        val firstLoad = appliedVersion < 0
                        appliedVersion = m.version
                        contentVersion = m.version
                        // 只有素材真的换了才清下载记录与帧缓存；首次加载保留磁盘上已有的解压结果
                        if (!firstLoad) {
                            readyActs.clear()
                            PetFrameCache.clear()
                        } else {
                            restoreReadyActs()
                        }
                        persist()
                        // 商店动作目录：该角色在售的 clip 名（未购买时引擎禁止播放/下载）
                        actionsByCharacter = m.actions
                            .filter { it.clip.isNotBlank() }
                            .groupBy { it.character }
                            .mapValues { (_, list) -> list.map { it.clip }.toSet() }
                        // 清单里标了 builtin 的角色用内置帧（boy 离线兜底）
                        characters = map.mapValues { (id, def) ->
                            if (id == "boy" && def.builtin) BOY else def
                        }
                    }
            }
        } finally {
            refreshing = false
            refreshTried = true
        }
    }

    suspend fun ensureRefresh() {
        if (!refreshTried) refresh()
    }

    /** 该 clip 属于哪个 pack */
    private fun packForClip(def: PetCharacterDef, clip: PetClipDef): PetPackDef? {
        val actName = "act${clip.act}"
        return def.packs.firstOrNull { actName in it.acts }
    }

    /** 确保某个 clip 可用：下载它所在 pack 的所有 act（builtin 直接可用） */
    suspend fun ensureClip(context: Context, character: String, clipName: String): Boolean {
        val def = characterFor(character)
        if (def.builtin) return true
        val clip = def.clips[clipName] ?: return false
        val pack = packForClip(def, clip) ?: return false
        for (act in pack.acts) {
            if (!ensureAct(context, character, act)) return false
        }
        return true
    }

    /** 确保角色的 starter 动作就绪（core 包），不再整角色全量下载 */
    suspend fun ensureCharacter(context: Context, character: String) {
        ensureRefresh()
        val def = characterFor(character)
        if (def.builtin) return
        val starter = def.starter.ifEmpty {
            (def.roles.idle + def.roles.music).distinct()
        }.ifEmpty { def.clips.keys.take(1) }
        for (clip in starter) {
            ensureClip(context, character, clip)
        }
    }

    /** 下载并解压某角色的一段动画（幂等、并发安全）。返回是否可用 */
    suspend fun ensureAct(context: Context, character: String, act: String): Boolean {
        val def = characterFor(character)
        if (def.builtin) return true
        val key = "$character/$act"
        if (key in readyActs) return true
        val mutex = actMutexes.getOrPut(key) { Mutex() }
        return mutex.withLock {
            if (key in readyActs) return@withLock true
            withContext(Dispatchers.IO) {
                runCatching {
                    val body = MusicApiClient.service.petContentFile(character, act)
                    val total = body.contentLength()
                    val dir = java.io.File(context.filesDir, "pet/$character/$act")
                    dir.deleteRecursively()
                    dir.mkdirs()
                    progress.value = PetDownloadProgress(character, act, 0f)
                    val counting = CountingInputStream(body.byteStream().buffered()) { read ->
                        if (total > 0) {
                            progress.value = PetDownloadProgress(
                                character, act, (read.toFloat() / total).coerceIn(0f, 1f)
                            )
                        }
                    }
                    ZipInputStream(counting).use { zin ->
                        var entry = zin.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val out = java.io.File(dir, entry.name)
                                out.parentFile?.mkdirs()
                                out.outputStream().use { zin.copyTo(it) }
                            }
                            zin.closeEntry()
                            entry = zin.nextEntry
                        }
                    }
                    val ok = dir.walkTopDown().any { it.extension == "webp" }
                    if (ok) {
                        readyActs.add(key)
                        persist()
                    }
                    ok
                }.also {
                    progress.value = null
                }.getOrDefault(false)
            }
        }
    }

    /** 清理某个角色的本地素材（版本切换/用户手动清理） */
    fun clearCharacter(context: Context, character: String) {
        val keys = readyActs.filter { it.startsWith("$character/") }
        keys.forEach { readyActs.remove(it) }
        java.io.File(context.filesDir, "pet/$character").deleteRecursively()
        persist()
    }

    private const val PREFS = "yinhe_pet_content"

    fun init(context: Context) {
        runCatching {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs = p
            contentVersion = p.getInt("version", 1)
            restoreReadyActs()
        }
    }

    /** 从 SharedPreferences 恢复"已解压可用"的素材段记录 */
    private fun restoreReadyActs() {
        runCatching {
            val p = prefs ?: return
            val saved = p.getStringSet("ready_acts", emptySet()) ?: emptySet()
            readyActs.addAll(saved)
        }
    }

    private fun persist() {
        runCatching {
            val p = prefs ?: return
            p.edit()
                .putStringSet("ready_acts", readyActs.toSet())
                .putInt("version", contentVersion)
                .apply()
        }
    }

    private class CountingInputStream(
        delegate: InputStream,
        private val onRead: (Long) -> Unit,
    ) : FilterInputStream(delegate) {
        private var count = 0L
        override fun read(): Int {
            val b = super.read()
            if (b >= 0) { count++; onRead(count) }
            return b
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) { count += n; onRead(count) }
            return n
        }
    }
}
