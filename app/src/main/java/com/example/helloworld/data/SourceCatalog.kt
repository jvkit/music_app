package com.example.helloworld.data

/** 音源目录：id、展示名、自动搜索与换源优先级 */
object SourceCatalog {
    data class Source(val id: String, val displayName: String)

    /** 顺序即优先级（越小越优先）；状态依据服务端 /api/web_sources（2026-10 音源诊断） */
    val all: List<Source> = listOf(
        Source("netease", "网易"),
        Source("web_liumingye", "音河搜索"),
        Source("web_fangpi", "放屁网"),
        Source("web_jbsou", "JB搜"),
        Source("web_tonzhon", "铜钟"),
        Source("web_gequbao", "歌曲宝"),
        Source("web_netease_fe_mm", "FeM网易"),
        Source("web_qqmp3", "QQMP3"),
        Source("bilibili", "B站"),
        Source("youtube", "YouTube"),
        Source("soundcloud", "SoundCloud"),
    )

    /** 「自动」模式并行搜索的音源：优先直链+聚合的 normal 源，unstable 留手动 */
    val autoSearch: List<String> = listOf(
        "netease",
        "web_liumingye",
        "web_fangpi",
        "web_jbsou",
        "web_tonzhon",
    )

    const val AUTO = "auto"

    fun displayName(sourceId: String): String =
        all.find { it.id == sourceId }?.displayName ?: sourceId

    /** 换源顺序：优先聚合源（音河搜索），其次直链源，unstable 殿后；排除已失败的原始源 */
    fun fallbackOrder(originalSource: String): List<String> =
        listOf(
            "web_liumingye",
            "web_fangpi",
            "web_jbsou",
            "web_tonzhon",
            "web_gequbao",
            "web_netease_fe_mm",
            "web_qqmp3",
            "bilibili",
            "netease",
        ).filter { it != originalSource }
}
