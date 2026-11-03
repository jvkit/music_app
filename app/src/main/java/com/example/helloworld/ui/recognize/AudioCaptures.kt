package com.example.helloworld.ui.recognize

import android.annotation.TargetApi
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.SystemClock
import java.io.ByteArrayOutputStream

/**
 * 听歌识曲的双路音频采集：
 *
 * - 麦克风（recordMic）：听设备外部的声音，AudioRecord 直采。
 * - 系统内录（recordSystem）：API 29+ 的 AudioPlaybackCapture，捕获设备上正在播放的音频
 *   （媒体/游戏等 usage；部分 App 会声明禁止被捕获，属预期）。
 *
 * 两路统一输出 44.1kHz 单声道 16bit 的 WAV 字节；服务端 ffmpeg 会降采样到 8kHz 出指纹。
 * 录音时长固定 11 秒——服务端指纹窗口是「第 4 秒起的 6 秒」，需要有余量。
 */
object AudioCaptures {
    const val SAMPLE_RATE = 44100
    const val RECORD_SECONDS = 11
    private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private const val BYTES_PER_SEC = SAMPLE_RATE * 2 // 单声道 16bit

    fun wrapWav(pcm: ByteArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val total = pcm.size
        val out = ByteArrayOutputStream(total + 44)
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        fun le16(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
        }
        out.write("RIFF".toByteArray()); le32(36 + total); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16); le16(1); le16(1)
        le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
        out.write("data".toByteArray()); le32(total); out.write(pcm)
        return out.toByteArray()
    }

    fun canCaptureSystemAudio(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** 麦克风录音（阻塞执行，请放 Dispatchers.IO）；失败返回 null */
    fun recordMic(seconds: Int = RECORD_SECONDS, onTick: ((Float) -> Unit)? = null): ByteArray? {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) return null
        val bufferSize = maxOf(minBuf * 2, BYTES_PER_SEC / 2)
        val record = try {
            @Suppress("MissingPermission")
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize
            )
        } catch (e: Exception) {
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        return try {
            record.startRecording()
            readLoop(record, seconds, bufferSize, onTick)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    /**
     * 判断一段 WAV 是否接近全静音（跳过 44 字节头，采样绝对值都很小即算静音）。
     * 用途：源应用设了 ALLOW_CAPTURE_BY_NONE 时内录"成功"但全是 0，需要给用户明确提示。
     */
    fun isSilent(wav: ByteArray, threshold: Int = 64): Boolean {
        if (wav.size <= 44) return true
        var peak = 0
        var i = 44
        // 每 400 字节采一个 16bit 样本，够判断了
        while (i + 1 < wav.size) {
            val lo = wav[i].toInt() and 0xFF
            val hi = wav[i + 1].toInt()
            val sample = ((hi shl 8) or lo)
            val abs = kotlin.math.abs(sample)
            if (abs > peak) peak = abs
            if (peak >= threshold) return false
            i += 400
        }
        return true
    }

    /** 系统内录（API 29+，阻塞执行，请放 Dispatchers.IO）；失败返回 null */
    @TargetApi(Build.VERSION_CODES.Q)
    fun recordSystem(
        projection: MediaProjection,
        seconds: Int = RECORD_SECONDS,
        onTick: ((Float) -> Unit)? = null
    ): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) throw IllegalStateException("AudioRecord 缓冲区不可用（minBuf=$minBuf）")
        val bufferSize = maxOf(minBuf * 2, BYTES_PER_SEC / 2)
        val record = try {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL)
                .build()
            @Suppress("MissingPermission")
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
        } catch (e: Exception) {
            // 不要吞掉：调用方要能把原因展示给用户（Android 14+ 未注册 Callback 之类）
            throw IllegalStateException("构建 AudioRecord 失败：${e::class.simpleName} ${e.message}", e)
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord 未初始化（state=${record.state}）")
        }
        return try {
            record.startRecording()
            readLoop(record, seconds, bufferSize, onTick)
        } catch (e: Exception) {
            throw IllegalStateException("录音中断：${e::class.simpleName} ${e.message}", e)
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    private fun readLoop(
        record: AudioRecord,
        seconds: Int,
        bufferSize: Int,
        onTick: ((Float) -> Unit)?
    ): ByteArray {
        val total = seconds * BYTES_PER_SEC
        val buffer = ByteArray(bufferSize)
        val out = ByteArrayOutputStream(total)
        var read = 0
        val startedAt = SystemClock.elapsedRealtime()
        while (read < total) {
            val n = record.read(buffer, 0, minOf(buffer.size, total - read))
            if (n <= 0) break
            out.write(buffer, 0, n)
            read += n
            val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000f
            onTick?.invoke((elapsed / seconds).coerceIn(0f, 1f))
        }
        return wrapWav(out.toByteArray())
    }
}
