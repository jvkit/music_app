package com.example.helloworld.data.remote

import com.google.gson.annotations.SerializedName

data class ApiTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Int? = null,
    val source: String,
    @SerializedName("source_url") val sourceUrl: String? = null,
    val thumbnail: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    val lyrics: String? = null,
    val extra: Map<String, @JvmSuppressWildcards Any?> = emptyMap()
)

data class ApiSong(
    val id: String,
    val title: String,
    val artist: String,
    val source: String,
    @SerializedName("source_url") val sourceUrl: String? = null,
    val duration: Int? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    val storage: String? = null,
    val thumbnail: String? = null,
    @SerializedName("cover_path") val coverPath: String? = null,
    @SerializedName("play_count") val playCount: Int? = null,
    val extra: Map<String, @JvmSuppressWildcards Any?> = emptyMap()
) {
    fun toApiTrack(): ApiTrack = ApiTrack(
        id = id,
        title = title,
        artist = artist,
        duration = duration,
        source = source,
        sourceUrl = sourceUrl,
        thumbnail = thumbnail,
        coverUrl = if (coverPath != null) "api/local/cover/$id" else null,
        extra = extra
    )
}

data class SearchResponse(
    val tracks: List<ApiTrack>,
    val offset: Int,
    val limit: Int
)

data class LibraryDataResponse(
    val version: Int,
    val playlists: Map<String, ApiPlaylist>,
    val songs: Map<String, ApiSong>,
    @SerializedName("today_plays") val todayPlays: Map<String, Int> = emptyMap()
)

/** 歌单分享：/api/share 的 type=playlist 载荷 */
data class SharedPlaylistPayload(
    val name: String = "",
    val tracks: List<ApiTrack> = emptyList()
)

data class ShareCreateResponse(val code: String = "")

data class ShareGetResponse(
    val type: String = "track",
    val playlist: SharedPlaylistPayload? = null,
    val track: ApiTrack? = null
)

data class ApiPlaylist(
    val id: String,
    val name: String
)

data class PreviewRequest(
    val track: ApiTrack,
    @SerializedName("media_type") val mediaType: String = "audio",
    val stream: Boolean = true
)

data class PreviewResponse(
    @SerializedName("stream_url") val streamUrl: String,
    @SerializedName("media_type") val mediaType: String? = null,
    val track: ApiTrack? = null,
    val streamed: Boolean? = null
)

data class DownloadRequest(
    val track: ApiTrack,
    @SerializedName("media_type") val mediaType: String = "audio"
)

data class DownloadSubmitResponse(
    @SerializedName("task_id") val taskId: String? = null,
    val status: String = "pending",
    @SerializedName("from_cache") val fromCache: Boolean = false
)

data class DownloadProgressResponse(
    @SerializedName("task_id") val taskId: String = "",
    val status: String = "pending",
    val progress: Int = 0,
    val error: String? = null
)
