package com.example.helloworld.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.PopupMenu
import com.example.helloworld.MainActivity
import com.example.helloworld.ui.components.PetAnimationEngine
import com.example.helloworld.ui.components.PetContentStore
import com.example.helloworld.ui.components.PetFrameCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 悬浮球小精灵（前台服务 + 系统悬浮窗）。
 *
 * - 精灵贴图复用宠物系统的 manifest 素材（PetContentStore/PetFrameCache），
 *   动画由纯 Kotlin 的 PetAnimationEngine 驱动，不依赖 Compose。
 * - 单击 = 播放/暂停；拖动 = 移动位置；长按 = 弹出菜单
 *   （识曲·麦克风 / 识曲·系统声音 / 播放暂停 / 上一首 / 下一首 / 收起）。
 * - 识曲菜单会拉起 MainActivity 并把 mode 经 BubbleBus 交给 UI 层。
 */
class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var species: String = "default"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var frameJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        BubbleBus.isEnabled = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        species = intent?.getStringExtra(EXTRA_SPECIES) ?: species
        startForeground(NOTIFICATION_ID, buildNotification())
        if (bubbleView == null) showBubble()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        frameJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        BubbleBus.isEnabled = false
        super.onDestroy()
    }

    // ===================== 悬浮窗 =====================

    private fun showBubble() {
        val density = resources.displayMetrics.density
        val size = (72 * density).toInt()
        val view = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(4, 4, 4, 4)
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            size, size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - (16 * density).toInt()
            y = resources.displayMetrics.heightPixels / 3
        }
        attachTouch(view, params)
        runCatching { windowManager.addView(view, params) }
        bubbleView = view
        layoutParams = params
        startFrameLoop(view)
    }

    private fun attachTouch(view: ImageView, params: WindowManager.LayoutParams) {
        val touchSlop = (8 * resources.displayMetrics.density).toInt()
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longPressFired = false

        val longPressRunnable = Runnable {
            longPressFired = true
            showMenu(view)
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    longPressFired = false
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        params.x = (startX + dx).toInt()
                        params.y = (startY + dy).toInt()
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (!dragging && !longPressFired) {
                        BubbleBus.send(BubbleBus.Command.PlayPause)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, MENU_RECOGNIZE_MIC, 1, "识曲 · 麦克风")
        menu.menu.add(0, MENU_RECOGNIZE_SYSTEM, 2, "识曲 · 系统声音")
        menu.menu.add(0, MENU_PLAY_PAUSE, 3, "播放 / 暂停")
        menu.menu.add(0, MENU_PREV, 4, "上一首")
        menu.menu.add(0, MENU_NEXT, 5, "下一首")
        menu.menu.add(0, MENU_CLOSE, 6, "收起悬浮球")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_PLAY_PAUSE -> BubbleBus.send(BubbleBus.Command.PlayPause)
                MENU_PREV -> BubbleBus.send(BubbleBus.Command.Previous)
                MENU_NEXT -> BubbleBus.send(BubbleBus.Command.Next)
                MENU_RECOGNIZE_MIC -> launchRecognize("mic")
                MENU_RECOGNIZE_SYSTEM -> launchRecognize("system")
                MENU_CLOSE -> BubbleBus.send(BubbleBus.Command.Close)
            }
            true
        }
        menu.show()
    }

    private fun launchRecognize(mode: String) {
        BubbleBus.requestRecognize(mode)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_RECOGNIZE_MODE, mode)
        }
        runCatching { startActivity(intent) }
    }

    // ===================== 精灵帧循环 =====================

    private fun startFrameLoop(view: ImageView) {
        frameJob?.cancel()
        frameJob = scope.launch {
            val characterId = PetContentStore.characterIdFor(species)
            PetContentStore.init(applicationContext)
            runCatching { PetContentStore.ensureCharacter(applicationContext, characterId) }
            val character = PetContentStore.characterFor(species)
            val engine = PetAnimationEngine(
                clips = character.clips,
                roles = character.roles,
                behaviors = character.behaviors,
                defaultFps = character.fps,
                starter = character.starter.toSet(),
                unlocked = emptySet()
            )
            var lastNanos = 0L
            var lastReq: Pair<Int, Int>? = null
            while (true) {
                val now = System.nanoTime()
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                    engine.advance(dt)
                }
                lastNanos = now
                val req = engine.frameRequest
                if (req != null && req != lastReq) {
                    lastReq = req
                    decodeFrame(characterId, req.first, req.second)?.let { bmp ->
                        if (!bmp.isRecycled) view.setImageBitmap(bmp)
                    }
                }
                delay(33)
            }
        }
    }

    private fun decodeFrame(characterId: String, act: Int, idx: Int): Bitmap? {
        val key = "$characterId/$act/$idx"
        PetFrameCache.get(key)?.let { return it }
        val file = PetContentStore.frameFile(applicationContext, characterId, "act$act", idx)
        if (file != null) {
            runCatching {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) PetFrameCache.put(key, bmp)
                return bmp
            }
        }
        return null
    }

    // ===================== 通知 =====================

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮球",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "音河小精灵悬浮在桌面上" }
            manager.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("音河小精灵")
            .setContentText("悬浮球运行中，点按悬浮球可播放/暂停，长按打开菜单")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "yinhe_bubble"
        private const val NOTIFICATION_ID = 3021
        private const val LONG_PRESS_MS = 500L

        private const val MENU_RECOGNIZE_MIC = 1
        private const val MENU_RECOGNIZE_SYSTEM = 2
        private const val MENU_PLAY_PAUSE = 3
        private const val MENU_PREV = 4
        private const val MENU_NEXT = 5
        private const val MENU_CLOSE = 6

        const val ACTION_START = "com.example.helloworld.bubble.START"
        const val ACTION_STOP = "com.example.helloworld.bubble.STOP"
        const val EXTRA_SPECIES = "bubble_species"
        const val EXTRA_RECOGNIZE_MODE = "recognize_mode"

        fun start(context: Context, species: String) {
            val intent = Intent(context, BubbleService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SPECIES, species)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }
}
