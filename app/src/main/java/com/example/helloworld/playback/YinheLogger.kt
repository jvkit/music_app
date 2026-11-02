package com.example.helloworld.playback

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 音河运行日志：环形缓冲 + 按音源统计成功/失败。
 *
 * 设计目的：多音源换源逻辑对外不可见，用户容易「前端看着正常、实际大量
 * 可用歌曲被静默跳过」。这里把每次解析/搜索/换源的成败都记下来，
 * 并在「我的 → 运行日志」页可查看、可复制，保证过程可审计。
 */
object YinheLogger {
    const val MAX_LINES = 300

    data class SourceStat(
        var attempts: Int = 0,
        var ok: Int = 0,
        var failed: Int = 0
    ) {
        val successRate: Int
            get() = if (attempts == 0) 100 else ok * 100 / attempts
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    private val _stats = MutableStateFlow<Map<String, SourceStat>>(emptyMap())
    val stats: StateFlow<Map<String, SourceStat>> = _stats

    @Synchronized
    fun log(category: String, message: String) {
        val line = "[${timeFormat.format(Date())}] $category | $message"
        _lines.value = (_lines.value + line).takeLast(MAX_LINES)
    }

    /** 记录一次某音源的解析/播放尝试结果 */
    @Synchronized
    fun record(source: String, ok: Boolean, detail: String = "") {
        val current = _stats.value.toMutableMap()
        val stat = current.getOrPut(source) { SourceStat() }
        stat.attempts += 1
        if (ok) stat.ok += 1 else stat.failed += 1
        _stats.value = current
        val mark = if (ok) "成功" else "失败"
        log(display(source), "$mark ${if (detail.isNotBlank()) "($detail)" else ""}  成功率 ${stat.successRate}% (${stat.ok}/${stat.attempts})")
    }

    fun display(source: String): String =
        com.example.helloworld.data.SourceCatalog.displayName(source)

    @Synchronized
    fun clear() {
        _lines.value = emptyList()
        _stats.value = emptyMap()
    }
}
