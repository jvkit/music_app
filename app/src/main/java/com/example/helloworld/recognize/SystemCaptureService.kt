package com.example.helloworld.recognize

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.helloworld.ui.recognize.AudioCaptures

/**
 * 系统内录取音的前台服务。
 *
 * **为什么必须要它**：Android 10+ 规定 `MediaProjectionManager.getMediaProjection()` 只能在
 * `foregroundServiceType="mediaProjection"` 的前台服务里调用，否则直接抛
 * `SecurityException: Media projections require a foreground service of type
 * ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`。
 * 之前把它放在 ViewModel 里调，在 Android 13/14+ 上必然失败——这才是"点了共享屏幕没后话"的根因。
 *
 * 流程：Activity 拿到授权 Intent → start 本服务 → 服务 startForeground 后建投影 + 录音 →
 * 录完把 WAV 通过 Broadcast 回传给 UI → 自己 stopSelf。
 */
class SystemCaptureService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 必须先 startForeground（带 mediaProjection 类型），才能建投影
        startForegroundWithType(intent)
        // 悬浮球小窗：进入"正在听"形态
        com.example.helloworld.bubble.BubbleBus.updateRecognize("recording", progress = 0f)

        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        val seconds = intent.getIntExtra(EXTRA_SECONDS, AudioCaptures.RECORD_SECONDS)

        if (data == null) {
            finish(ERROR_NO_DATA, "没有捕获授权数据")
            return START_NOT_STICKY
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = runCatching {
            manager.getMediaProjection(android.app.Activity.RESULT_OK, data)
        }.onFailure {
            logDiag("getMediaProjection 抛异常 ${it::class.simpleName}: ${it.message}")
        }.getOrNull()

        if (mp == null) {
            finish(ERROR_NO_PROJECTION, "没能建立音频捕获会话")
            return START_NOT_STICKY
        }
        logDiag("getMediaProjection 成功（前台服务内）")

        // Android 14+ 要求：拿到后立刻注册 Callback
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                logDiag("投影会话被系统停止")
            }
        }, handler)
        projection = mp

        Thread {
            val result = runCatching {
                AudioCaptures.recordSystem(mp, seconds) { p ->
                    // 真实录制进度：只更新数值，不触发小窗重建
                    com.example.helloworld.bubble.BubbleBus.updateRecognizeProgress(p)
                }
            }
            handler.post {
                val wav = result.getOrNull()
                when {
                    wav == null -> {
                        val why = result.exceptionOrNull()
                            ?.let { "${it::class.simpleName}: ${it.message}" }
                            ?: "AudioRecord 初始化失败"
                        logDiag("录音失败：$why")
                        finish(ERROR_RECORD_FAILED, why)
                    }
                    AudioCaptures.isSilent(wav) -> {
                        logDiag("录到全静音（源应用可能禁止捕获）")
                        finish(ERROR_SILENT, "录到的全是静音")
                    }
                    else -> {
                        logDiag("录音完成 ${wav.size} 字节")
                        finishWithAudio(wav)
                    }
                }
            }
        }.start()

        return START_NOT_STICKY
    }

    private fun startForegroundWithType(intent: Intent) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "听歌识曲", NotificationManager.IMPORTANCE_MIN)
                    .apply { description = "正在捕获系统音频用于识曲" }
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle("正在听歌识曲")
            .setContentText("正在采集系统声音…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun finishWithAudio(wav: ByteArray) {
        // 录完了，投影可以放掉；但服务要活到识别结束
        releaseProjection()
        notifyProgress("录完了，正在识别…")
        com.example.helloworld.bubble.BubbleBus.updateRecognize("uploading", progress = 1f)

        Thread {
            val result = runCatching {
                kotlinx.coroutines.runBlocking {
                    com.example.helloworld.data.remote.MusicApiClient.recognizeAudio(wav)
                }
            }
            val tracks = result.getOrNull()?.tracks.orEmpty()
            val first = tracks.firstOrNull()
            val title = if (first != null) "识别到：《${first.title}》" else "音河 · 没识别出来"
            logDiag("识别结束：${tracks.size} 首，首个=${first?.title ?: "无"}")

            // 完整曲目列表暂存在共享状态里：小窗点击要拿它去播放，
            // 走广播传 ApiTrack 又会撑爆 parcel，同进程直接给引用最省事。
            com.example.helloworld.bubble.BubbleBus.recognizeTracks = tracks

            val more = if (tracks.size > 1) "等 ${tracks.size} 首" else ""
            val text = when {
                first != null -> "${first.artist}$more（点这里回音河播放）"
                result.isFailure -> "识别失败：${result.exceptionOrNull()?.message?.take(60) ?: "网络异常"}"
                else -> "换个段落再试，或用麦克风识曲"
            }
            showResultNotification(this, title, text)
            com.example.helloworld.bubble.BubbleBus.updateRecognize(
                "done",
                progress = 1f,
                title = first?.title.orEmpty(),
                artist = if (first != null) "${first.artist}$more" else "",
                error = if (first == null) {
                    result.exceptionOrNull()?.message?.take(60) ?: "没听出来，换个段落再试"
                } else ""
            )
            // 把结果也广播回去：App 若还活着就更新界面
            val res = Intent(ACTION_RECOGNIZED).setPackage(packageName)
            if (first != null) res.putExtra(EXTRA_RESULT_TRACKS, tracks.size) 
            res.putExtra(EXTRA_RESULT_FIRST, first?.title.orEmpty())
            res.putExtra(EXTRA_RESULT_ARTIST, first?.artist.orEmpty())
            res.putExtra(EXTRA_RESULT_EMPTY, first == null)
            res.putExtra(EXTRA_RESULT_ERROR, result.exceptionOrNull()?.message.orEmpty())
            sendBroadcast(res)
            release()
        }.start()
    }

    private fun finish(errorCode: Int, message: String) {
        val text = when (errorCode) {
            ERROR_SILENT -> "没录到声音：对方应用可能禁止了内录，改用麦克风试试"
            ERROR_NO_PROJECTION -> "没能建立音频捕获：$message"
            else -> "录音失败：$message"
        }
        notifyProgress(text)
        com.example.helloworld.bubble.BubbleBus.updateRecognize("done", progress = 0f, error = text)
        val out = Intent(ACTION_RESULT).setPackage(packageName)
        out.putExtra(EXTRA_ERROR_CODE, errorCode)
        out.putExtra(EXTRA_ERROR_MESSAGE, message)
        sendBroadcast(out)
        release()
    }

    private fun notifyProgress(text: String) {
        runCatching {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
            manager.notify(
                NOTIFICATION_ID,
                builder
                    .setContentTitle("音河 · 听歌识曲")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setOngoing(false)
                    .setContentIntent(
                        android.app.PendingIntent.getActivity(
                            this, 0,
                            Intent(this, com.example.helloworld.MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    .build()
            )
        }
    }

    /** 只释放投影，服务继续活着（识别线程还要用） */
    private fun releaseProjection() {
        runCatching { projection?.stop() }
        projection = null
    }

    private fun release() {
        releaseProjection()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { projection?.stop() }
        projection = null
        super.onDestroy()
    }

    private fun logDiag(msg: String) {
        runCatching {
            val prefs = getSharedPreferences("yinhe_recognize_diag", Context.MODE_PRIVATE)
            val prev = prefs.getString("log", "").orEmpty()
            val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())
            prefs.edit().putString("log", (prev + "\n$ts [svc] $msg").takeLast(3000)).apply()
        }
    }

    companion object {
        private const val CHANNEL_ID = "yinhe_recognize"
        private const val NOTIFICATION_ID = 3022

        const val ACTION_START = "com.example.helloworld.recognize.CAPTURE_START"
        const val ACTION_RESULT = "com.example.helloworld.recognize.CAPTURE_RESULT"
        const val ACTION_RECOGNIZED = "com.example.helloworld.recognize.RECOGNIZED"
        const val EXTRA_RESULT_TRACKS = "result_tracks"
        const val EXTRA_RESULT_FIRST = "result_first"
        const val EXTRA_RESULT_ARTIST = "result_artist"
        const val EXTRA_RESULT_EMPTY = "result_empty"
        const val EXTRA_RESULT_ERROR = "result_error"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_AUDIO = "audio"
        const val EXTRA_ERROR_CODE = "error_code"
        const val EXTRA_ERROR_MESSAGE = "error_message"

        const val ERROR_NO_DATA = 1
        const val ERROR_NO_PROJECTION = 2
        const val ERROR_RECORD_FAILED = 3
        const val ERROR_SILENT = 4

        /**
         * 识别结束后的结果通知。
         * 用户在别的 App 里放歌（正是要识别的对象），不可能守着我们的界面，
         * 所以结果必须能从通知栏看到，点一下回 App。
         */
        fun showResultNotification(context: Context, title: String, text: String) {
            runCatching {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NOTIFICATION_ID)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "听歌识曲", NotificationManager.IMPORTANCE_DEFAULT)
                    )
                }
                val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder(context, CHANNEL_ID)
                } else {
                    @Suppress("DEPRECATION")
                    Notification.Builder(context)
                }
                manager.notify(
                    NOTIFICATION_ID + 1,
                    builder
                        .setContentTitle(title)
                        .setContentText(text)
                        .setStyle(Notification.BigTextStyle().bigText(text))
                        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                        .setAutoCancel(true)
                        .setContentIntent(
                            android.app.PendingIntent.getActivity(
                                context, 0,
                                Intent(context, com.example.helloworld.MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                        )
                        .build()
                )
            }
        }

        fun start(context: Context, resultData: Intent, seconds: Int = AudioCaptures.RECORD_SECONDS) {
            val intent = Intent(context, SystemCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_SECONDS, seconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
