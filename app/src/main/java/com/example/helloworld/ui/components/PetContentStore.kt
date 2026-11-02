package com.example.helloworld.ui.components

import android.content.Context
import com.example.helloworld.data.remote.MusicApiClient
import com.example.helloworld.data.remote.PetCharacterDef
import com.example.helloworld.data.remote.PetClipDef
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 宠物素材内容库：角色/动画段由服务器 manifest 下发，客户端按需下载 zip 解压到
 * filesDir/pet/<character>/<act>/，之后 PetAvatar 优先读磁盘、打包 assets 只做离线兜底。
 * 新增/更新角色 = 服务端放 zip + 改 manifest.json，客户端无需发版。
 */
object PetContentStore {

    /** 内置 boy 清单：与 assets/pet/boy、服务端内置清单完全一致（离线兜底） */
    private val BOY = PetCharacterDef(
        name = "小河灵",
        fps = 24f,
        acts = listOf("act1", "act2", "act3"),
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
        )
    )

    @Volatile
    var characters: Map<String, PetCharacterDef> = mapOf("boy" to BOY)
        private set

    /** 已下载解压完成的 "character/act" */
    private val readyActs = ConcurrentHashMap.newKeySet<String>()
    private val actMutexes = ConcurrentHashMap<String, Mutex>()
    private var refreshTried = false

    /** 物种 -> 角色 id：manifest 里有对应角色就用，否则用 boy */
    fun characterIdFor(species: String): String =
        if (characters.containsKey(species)) species else "boy"

    fun characterFor(species: String): PetCharacterDef =
        characters[species] ?: characters["boy"] ?: BOY

    fun isActReady(character: String, act: String): Boolean = "$character/$act" in readyActs

    /** 帧文件：仅当对应素材段已下载解压才返回（否则走 assets 兜底） */
    fun frameFile(context: Context, character: String, act: String, idx: Int): File? {
        if (!isActReady(character, act)) return null
        val dir = File(context.filesDir, "pet/$character/$act")
        val framed = File(dir, String.format(Locale.US, "frames/frame_%04d.webp", idx))
        if (framed.exists()) return framed
        val flat = File(dir, String.format(Locale.US, "frame_%04d.webp", idx))
        return if (flat.exists()) flat else null
    }

    /** 拉取素材清单（登录/进宠物中心时调一次）；失败静默用内置 boy */
    suspend fun refresh() {
        refreshTried = true
        runCatching { MusicApiClient.service.petContentManifest() }
            .onSuccess { m ->
                val map = m.characters.filterValues { it.clips.isNotEmpty() }
                if (map.isNotEmpty()) characters = map
            }
    }

    suspend fun ensureRefresh() {
        if (!refreshTried) refresh()
    }

    /** 下载并解压某角色的一段动画（幂等、并发安全）。返回是否可用 */
    suspend fun ensureAct(context: Context, character: String, act: String): Boolean {
        val key = "$character/$act"
        if (key in readyActs) return true
        val mutex = actMutexes.getOrPut(key) { Mutex() }
        return mutex.withLock {
            if (key in readyActs) return@withLock true
            withContext(Dispatchers.IO) {
                runCatching {
                    val body = MusicApiClient.service.petContentFile(character, act)
                    val dir = File(context.filesDir, "pet/$character/$act")
                    dir.deleteRecursively()
                    dir.mkdirs()
                    ZipInputStream(body.byteStream().buffered()).use { zin ->
                        var entry = zin.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val out = File(dir, entry.name)
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
                        persist(context)
                    }
                    ok
                }.getOrDefault(false)
            }
        }
    }

    /** 宠物可见时触发：确保该角色全部动画段就绪 */
    suspend fun ensureCharacter(context: Context, character: String) {
        ensureRefresh()
        val def = characterFor(character)
        for (act in def.acts) {
            ensureAct(context, character, act)
        }
    }

    private const val PREFS = "yinhe_pet_content"

    fun init(context: Context) {
        runCatching {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet("ready_acts", emptySet()) ?: emptySet()
            readyActs.addAll(saved)
        }
    }

    private fun persist(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet("ready_acts", readyActs.toSet()).apply()
        }
    }
}
