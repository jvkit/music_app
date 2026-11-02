package com.example.helloworld.data.remote

import com.example.helloworld.data.local.SettingsRepository
import com.google.gson.annotations.SerializedName
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface MusicApiService {
    @retrofit2.http.GET("library")
    suspend fun getLibrary(): LibraryDataResponse

    @retrofit2.http.GET("server_library")
    suspend fun getServerLibrary(): ServerLibraryResponse

    @retrofit2.http.GET("search")
    suspend fun search(
        @retrofit2.http.Query("query") query: String,
        @retrofit2.http.Query("source") source: String = "netease",
        @retrofit2.http.Query("limit") limit: Int = 20,
        @retrofit2.http.Query("offset") offset: Int = 0
    ): SearchResponse

    @retrofit2.http.POST("preview")
    suspend fun preview(@retrofit2.http.Body request: PreviewRequest): PreviewResponse

    @retrofit2.http.POST("auth/register")
    suspend fun register(@retrofit2.http.Body request: AuthRequest): AuthResponse

    @retrofit2.http.POST("auth/login")
    suspend fun login(@retrofit2.http.Body request: AuthRequest): AuthResponse

    @retrofit2.http.GET("me")
    suspend fun me(@retrofit2.http.Header("Authorization") authorization: String): MeResponse

    @retrofit2.http.PATCH("auth/me")
    suspend fun rename(
        @retrofit2.http.Body request: RenameRequest,
        @retrofit2.http.Header("Authorization") authorization: String
    ): AuthResponse

    @retrofit2.http.POST("auth/password")
    suspend fun changePassword(
        @retrofit2.http.Body request: PasswordChangeRequest,
        @retrofit2.http.Header("Authorization") authorization: String
    ): AuthResponse

    @retrofit2.http.POST("auth/avatar")
    suspend fun uploadAvatar(
        @retrofit2.http.Body request: AvatarUploadRequest,
        @retrofit2.http.Header("Authorization") authorization: String
    ): AvatarUploadResponse

    @retrofit2.http.GET("auth/avatar_presets")
    suspend fun avatarPresets(): AvatarPresetsResponse

    @retrofit2.http.POST("auth/avatar/preset")
    suspend fun useAvatarPreset(
        @retrofit2.http.Body request: AvatarPresetRequest,
        @retrofit2.http.Header("Authorization") authorization: String
    ): AvatarUploadResponse

    @retrofit2.http.GET("me/library")
    suspend fun getMyLibrary(@retrofit2.http.Header("Authorization") authorization: String): UserLibraryResponse

    @retrofit2.http.PUT("me/library")
    suspend fun putMyLibrary(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body data: UserLibraryRequest
    ): PutLibraryResponse

    @retrofit2.http.POST("lyrics")
    suspend fun lyrics(@retrofit2.http.Body request: LyricsRequest): LyricsResponse

    @retrofit2.http.POST("plays")
    suspend fun recordPlay(@retrofit2.http.Body request: PlayRecordRequest): PlayRecordResponse

    // ===================== IM =====================

    @retrofit2.http.GET("im/lookup")
    suspend fun lookup(
        @retrofit2.http.Query("q") q: String,
        @retrofit2.http.Header("Authorization") authorization: String
    ): LookupResponse

    @retrofit2.http.GET("im/friends")
    suspend fun friends(
        @retrofit2.http.Header("Authorization") authorization: String
    ): FriendsResponse

    @retrofit2.http.POST("im/friends")
    suspend fun addFriend(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body request: FriendRequest
    ): OkResponse

    @retrofit2.http.DELETE("im/friends/{username}")
    suspend fun removeFriend(
        @retrofit2.http.Path("username") username: String,
        @retrofit2.http.Header("Authorization") authorization: String
    ): OkResponse

    @retrofit2.http.GET("im/inbox")
    suspend fun inbox(
        @retrofit2.http.Header("Authorization") authorization: String
    ): InboxResponse

    @retrofit2.http.POST("im/listen/state")
    suspend fun reportListenState(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body request: ListenStateRequest
    ): OkResponse

    @retrofit2.http.GET("im/listen/{username}")
    suspend fun getListenState(
        @retrofit2.http.Path("username") username: String,
        @retrofit2.http.Header("Authorization") authorization: String
    ): ListenStateResponse

    @retrofit2.http.POST("share")
    suspend fun createShare(
        @retrofit2.http.Body payload: Map<String, @JvmSuppressWildcards Any?>
    ): ShareCreateResponse

    @retrofit2.http.GET("share")
    suspend fun getShare(
        @retrofit2.http.Query("code") code: String
    ): ShareGetResponse

    @retrofit2.http.POST("download")
    suspend fun download(@retrofit2.http.Body request: DownloadRequest): DownloadSubmitResponse

    @retrofit2.http.GET("download/progress")
    suspend fun downloadProgress(
        @retrofit2.http.Query("task_id") taskId: String
    ): DownloadProgressResponse

    @retrofit2.http.GET("announcement")
    suspend fun announcement(): AnnouncementResponse

    @retrofit2.http.POST("feedback")
    suspend fun submitFeedback(
        @retrofit2.http.Body request: FeedbackRequest,
        @retrofit2.http.Header("Authorization") authorization: String
    ): OkResponse

    @retrofit2.http.POST("logs")
    suspend fun uploadLogs(
        @retrofit2.http.Body request: LogUploadRequest,
        @retrofit2.http.Header("Authorization") authorization: String
    ): OkResponse

    // ===================== 宠物 =====================

    @retrofit2.http.GET("pet/pairs")
    suspend fun petPairs(
        @retrofit2.http.Header("Authorization") authorization: String
    ): PetPairsResponse

    @retrofit2.http.GET("pet/pair/{friend}")
    suspend fun petPair(
        @retrofit2.http.Path("friend") friend: String,
        @retrofit2.http.Header("Authorization") authorization: String
    ): PetPairDto

    @retrofit2.http.POST("pet/signin")
    suspend fun petSignin(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body request: PetFriendRequest
    ): PetPairDto

    @retrofit2.http.POST("pet/claim")
    suspend fun petClaim(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body request: PetClaimRequest
    ): PetPairDto

    @retrofit2.http.POST("pet/feed")
    suspend fun petFeed(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body request: PetFeedRequest
    ): PetPairDto

    @retrofit2.http.POST("pet/interact")
    suspend fun petInteract(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body request: PetInteractRequest
    ): PetPairDto

    @retrofit2.http.POST("pet/buy_species")
    suspend fun petBuySpecies(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body request: PetSpeciesRequest
    ): PetPairDto

    @retrofit2.http.POST("pet/switch_species")
    suspend fun petSwitchSpecies(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body request: PetSpeciesRequest
    ): PetPairDto

    @retrofit2.http.PATCH("pet/name")
    suspend fun petRename(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body request: PetRenameRequest
    ): PetPairDto

    @retrofit2.http.POST("pet/buy_action")
    suspend fun petBuyAction(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body request: PetBuyActionRequest
    ): PetPairDto

    @retrofit2.http.POST("pet/event")
    suspend fun petEvent(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body request: PetEventRequest
    ): OkResponse

    @retrofit2.http.GET("pet/shop")
    suspend fun petShop(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Query("friend") friend: String? = null
    ): PetShopResponse

    // ===================== 宠物素材内容（服务器下发） =====================

    @retrofit2.http.GET("pet/content/manifest")
    suspend fun petContentManifest(): PetContentManifestResponse

    @retrofit2.http.Streaming
    @retrofit2.http.GET("pet/content/file/{character}/{act}")
    suspend fun petContentFile(
        @retrofit2.http.Path("character") character: String,
        @retrofit2.http.Path("act") act: String
    ): okhttp3.ResponseBody
}

data class AnnouncementResponse(
    val version: String = "0.1",
    val title: String = "欢迎使用",
    val notes: List<String> = emptyList(),
    @SerializedName("apk_url") val apkUrl: String = "",
    @SerializedName("apk_version_code") val apkVersionCode: Int = 0
)

data class LookupResponse(val users: List<String> = emptyList())
data class FriendDto(val username: String, val online: Boolean = false)
data class FriendsResponse(val friends: List<FriendDto> = emptyList())
data class FriendRequest(val username: String)
data class OkResponse(val ok: Boolean = true)
data class InboxResponse(val messages: List<ImMessageDto> = emptyList())

data class ImMessageDto(
    val type: String = "chat",
    val from: String = "",
    val to: String = "",
    val text: String? = null,
    val track: ApiTrack? = null,
    val note: String? = null,
    val action: String? = null,
    val ts: Long = 0
)

// ===================== 宠物 =====================

data class PetTaskDto(
    val id: String = "",
    val type: String = "",
    val target: Int = 0,
    val progress: Int = 0,
    val done: Boolean = false,
    val claimed: Boolean = false,
    @SerializedName("notes_reward") val notesReward: Int = 0
)

data class PetPairDto(
    val friend: String = "",
    val name: String = "",
    val species: String = "default",
    @SerializedName("species_owned") val speciesOwned: List<String> = listOf("default"),
    val level: Int = 1,
    val exp: Int = 0,
    @SerializedName("exp_need") val expNeed: Int = 50,
    val notes: Int = 0,
    @SerializedName("flame_days") val flameDays: Int = 0,
    @SerializedName("flame_multiplier") val flameMultiplier: Float = 1f,
    @SerializedName("actions_unlocked") val actionsUnlocked: List<String> = listOf("idle"),
    @SerializedName("action_unlocks") val actionUnlocks: Map<String, List<String>> = emptyMap(),
    @SerializedName("signin_today") val signinToday: Boolean = false,
    val tasks: List<PetTaskDto> = emptyList(),
    @SerializedName("feed_count") val feedCount: Int = 0,
    val reward: Int? = null,
    val already: Boolean? = null,
    @SerializedName("gained_exp") val gainedExp: Int? = null,
    @SerializedName("leveled_up") val leveledUp: Int? = null,
    @SerializedName("new_actions") val newActions: List<String>? = null,
    @SerializedName("notes_reward") val interactNotesReward: Int? = null
)

data class PetPairsResponse(val pairs: List<PetPairDto> = emptyList())
data class PetFriendRequest(val friend: String)
data class PetClaimRequest(val friend: String, @SerializedName("task_id") val taskId: String)
data class PetFeedRequest(val friend: String, val count: Int = 1)
data class PetInteractRequest(val friend: String, val action: String)
data class PetSpeciesRequest(val friend: String, val species: String)
data class PetRenameRequest(val friend: String, val name: String)
data class PetBuyActionRequest(val friend: String, @SerializedName("action_id") val actionId: String)
data class PetEventRequest(val type: String, val count: Int = 1)

data class PetShopItem(
    val species: String = "",
    val name: String = "",
    val price: Int = 0,
    @SerializedName("level_required") val levelRequired: Int = 0
)

data class PetShelfItem(
    val action: String = "",
    val name: String = "",
    val category: String = "",
    val price: Int = 0,
    val pack: String = "",
    val sold: Boolean = false
)

data class PetActionDef(
    val id: String = "",
    val character: String = "",
    val name: String = "",
    val category: String = "",
    val clip: String = "",
    @SerializedName("min_level") val minLevel: Int = 1,
    val pack: String = "",
    /** 图鉴/全量商店用：是否已解锁、是否被等级门槛锁住、价格与是否在折扣货架 */
    val unlocked: Boolean = false,
    @SerializedName("locked_by_level") val lockedByLevel: Boolean = false,
    @SerializedName("on_shelf") val onShelf: Boolean = false,
    val price: Int = 0
)

data class PetShopResponse(
    val species: List<PetShopItem> = emptyList(),
    val levels: Map<String, Int> = emptyMap(),
    val friend: String? = null,
    val level: Int = 1,
    val notes: Int = 0,
    @SerializedName("species_current") val speciesCurrent: String? = null,
    val shelf: List<PetShelfItem> = emptyList(),
    @SerializedName("action_unlocks") val actionUnlocks: List<String> = emptyList(),
    val actions: List<PetActionDef> = emptyList(),
    /** 当前角色全部动作（含未解锁/等级锁），商店全量展示 */
    val catalog: List<PetActionDef> = emptyList(),
    @SerializedName("rotation_version") val rotationVersion: Int = 1
)

/** 宠物素材清单（/pet/content/manifest）：角色 -> 动画段定义，新增角色无需发版 */
data class PetClipDef(
    val act: Int = 1,
    val start: Int = 0,
    val end: Int = 0,
    val fps: Float? = null,
    val mode: String? = null
)

data class PetRoleSet(
    val idle: List<String> = emptyList(),
    val music: List<String> = emptyList(),
    val click: List<String> = emptyList(),
    val move: List<String> = emptyList(),
    val turn: List<String> = emptyList(),
    val drag: List<String> = emptyList(),
    @SerializedName("sleep_start") val sleepStart: List<String> = emptyList(),
    @SerializedName("sleep_loop") val sleepLoop: List<String> = emptyList(),
    @SerializedName("sleep_end") val sleepEnd: List<String> = emptyList(),
    val events: Map<String, List<String>> = emptyMap()
)

data class PetBehaviorCategory(
    val id: String = "",
    val weight: Int = 0,
    val actions: List<String> = emptyList()
)

data class PetBehaviors(
    val weights: Map<String, Int> = emptyMap(),
    val categories: List<PetBehaviorCategory> = emptyList()
)

data class PetPackDef(
    val id: String = "",
    val name: String = "",
    val acts: List<String> = emptyList(),
    val size: Long = 0,
    val sha256: String = "",
    val frames: Int = 0
)

data class PetCharacterDef(
    val name: String = "",
    val fps: Float = 24f,
    val acts: List<String> = emptyList(),
    val clips: Map<String, PetClipDef> = emptyMap(),
    val roles: PetRoleSet = PetRoleSet(),
    val starter: List<String> = emptyList(),
    val behaviors: PetBehaviors = PetBehaviors(),
    val builtin: Boolean = false,
    val packs: List<PetPackDef> = emptyList(),
    /** 该角色在画面中的位置/缩放修正（画布 360x480 基准，不用改素材帧） */
    val display: PetDisplayDef = PetDisplayDef()
)

/**
 * 角色显示修正：各角色素材在画布内的留白基准不一致，
 * 用这组参数在绘制时统一对齐，服务端改完即生效、无需发版。
 *
 * offsetX/offsetY 以「画布宽/高的百分比」计（-1..1），正数=右/下。
 */
data class PetDisplayDef(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    /** 只按 alpha 自动对齐脚底与水平中心（默认开），叠加在 offset 之前 */
    @SerializedName("auto_align") val autoAlign: Boolean = true
)

data class PetContentManifestResponse(
    val version: Int = 1,
    val characters: Map<String, PetCharacterDef> = emptyMap(),
    val actions: List<PetActionDef> = emptyList(),
    @SerializedName("shop_rotation_version") val shopRotationVersion: Int = 1
)

data class ListenStateRequest(
    val track: ApiTrack? = null,
    @SerializedName("position_ms") val positionMs: Long = 0,
    @SerializedName("is_playing") val isPlaying: Boolean = false,
    /** 状态版本号：本地播放操作递增，对方只在 rev 变化时全量应用（防回声） */
    val rev: Long = 0,
    /** 一起听对象；null=开放邀请中 */
    val partner: String? = null
)

data class ListenStateResponse(
    val active: Boolean = false,
    val from: String? = null,
    val track: ApiTrack? = null,
    @SerializedName("position_ms") val positionMs: Long = 0,
    @SerializedName("is_playing") val isPlaying: Boolean = false,
    val rev: Long = 0,
    val partner: String? = null,
    val ts: Long = 0
)

data class FeedbackRequest(val text: String)

data class LogUploadRequest(val lines: List<String>)

data class AuthRequest(val username: String, val password: String)
data class AuthResponse(val token: String, val username: String)
data class MeResponse(val username: String, val avatar: String? = null)

data class RenameRequest(val username: String)

data class PasswordChangeRequest(
    @SerializedName("old_password") val oldPassword: String,
    @SerializedName("new_password") val newPassword: String
)

data class AvatarUploadRequest(val data: String)

data class AvatarUploadResponse(val ok: Boolean = true, val avatar: String = "")

data class AvatarPresetRequest(val name: String)

data class AvatarPresetsResponse(val presets: List<String> = emptyList())

data class UserLibraryRequest(
    val playlists: List<PlaylistPayload>,
    val favorites: List<ApiTrack>
)

data class PlaylistPayload(
    val id: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val tracks: List<ApiTrack>
)

data class UserLibraryResponse(
    val playlists: List<PlaylistPayload> = emptyList(),
    val favorites: List<ApiTrack> = emptyList()
)

data class PutLibraryResponse(val ok: Boolean, val playlists: Int = 0, val favorites: Int = 0)

data class LyricsRequest(val track: ApiTrack)

data class LyricsLine(
    val time: Double? = null,
    val text: String = "",
    val translation: String? = null
)

data class PlayRecordRequest(
    @SerializedName("track_id") val trackId: String,
    val track: ApiTrack? = null
)

data class PlayRecordResponse(
    @SerializedName("track_id") val trackId: String,
    val count: Int = 0
)

data class LyricsResponse(
    @SerializedName("has_lyrics") val hasLyrics: Boolean = false,
    val lines: List<LyricsLine> = emptyList(),
    val source: String? = null
)

object MusicApiClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        // 外网源（YouTube/SoundCloud 走代理）搜索可能要 10-20s，超时放宽
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedService: MusicApiService? = null

    val service: MusicApiService
        get() {
            val baseUrl = SettingsRepository.baseUrl()
            cachedService?.takeIf { cachedBaseUrl == baseUrl }?.let { return it }
            return synchronized(this) {
                cachedService?.takeIf { cachedBaseUrl == baseUrl }
                    ?: Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(MusicApiService::class.java)
                        .also {
                            cachedService = it
                            cachedBaseUrl = baseUrl
                        }
            }
        }

    fun resolveServerUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("data:")) {
            return path
        }
        val origin = SettingsRepository.origin().removeSuffix("/")
        return if (path.startsWith("/")) origin + path else "$origin/$path"
    }

    fun thumbnailProxyUrl(thumbnail: String?): String? {
        if (thumbnail.isNullOrBlank()) return null
        if (thumbnail.startsWith("http://") || thumbnail.startsWith("https://")) {
            val encoded = java.net.URLEncoder.encode(thumbnail, "UTF-8")
            return resolveServerUrl("api/thumbnail?url=$encoded")
        }
        return resolveServerUrl(thumbnail)
    }
}
