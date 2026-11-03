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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
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
class BubbleService : LifecycleService(), SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var bubbleView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var menuView: ComposeView? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var species: String = "default"

    /**
     * ComposeView 挂在 WindowManager 上时不走任何 Activity，必须手动喂 ViewTreeOwner。
     * Compose 在 onAttachedToWindow 里**强制**要求 SavedStateRegistryOwner，缺了直接抛
     * IllegalStateException 崩进程——所以这里自己实现一份。
     */
    private val savedStateRegistryController = androidx.savedstate.SavedStateRegistryController.create(this)
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    /** 中心按钮显示播放还是暂停：由 UI 侧回来（BubbleBus.playbackState） */
    private var isPlayingState: Boolean = false

    /** 帧循环里的动画引擎：点击时用来立刻播一次互动动作 */
    @Volatile
    private var engineRef: PetAnimationEngine? = null

    /** 引擎还没就绪时（素材下载中）用户点的那些击，就绪后补播一次 */
    @Volatile
    private var pendingTap: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var frameJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        BubbleBus.isEnabled = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        species = intent?.getStringExtra(EXTRA_SPECIES) ?: species
        startForeground(NOTIFICATION_ID, buildNotification())
        if (bubbleView == null) showBubble()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        hideMenu()
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
                        onBubbleTapped()
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

    /**
     * 单击悬浮球 = 和精灵互动：只播一次随机互动动作，**不动播放状态**。
     * 播放/暂停、上一首下一首全部收在长按的环形菜单里。
     *
     * 首次启动要等素材下载完引擎才就绪，这期间的点击先记下、就绪后补播一次，
     * 否则"刚开悬浮球时点了没反应"。
     */
    private fun onBubbleTapped() {
        val engine = engineRef
        if (engine != null) {
            engine.tap()
        } else {
            pendingTap = true
        }
    }

    /** 懒连接 MediaController（播放服务可能还没起，连一次常驻） */
    private var mediaController: androidx.media3.session.MediaController? = null

    private suspend fun connectController(): androidx.media3.session.MediaController? {
        mediaController?.let { return it }
        return runCatching {
            val token = androidx.media3.session.SessionToken(
                this,
                android.content.ComponentName(this, com.example.helloworld.playback.YinHeMediaService::class.java)
            )
            val future = androidx.media3.session.MediaController.Builder(this, token).buildAsync()
            val c = kotlinx.coroutines.suspendCancellableCoroutine<androidx.media3.session.MediaController?> { cont ->
                future.addListener({
                    val v = runCatching { future.get() }.getOrNull()
                    cont.resumeWith(Result.success(v))
                }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
            }
            mediaController = c
            c
        }.getOrNull()
    }

    private fun showMenu(anchor: View) {
        if (menuView != null) return
        isPlayingState = BubbleBus.isPlaying
        val params = layoutParams ?: return
        val density = resources.displayMetrics.density
        val size = (72 * density).toInt()
        // 圆心取悬浮球当前中心
        val cx = params.x + size / 2f
        val cy = params.y + size / 2f
            .coerceAtMost(resources.displayMetrics.heightPixels - size / 2f)
        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@BubbleService)
            setViewTreeSavedStateRegistryOwner(this@BubbleService)
            setContent {
                MaterialTheme {
                    BubbleRingMenu(
                        centerX = cx,
                        centerY = cy,
                        isPlaying = isPlayingState,
                        favorited = BubbleBus.isFavorited,
                        hasTrack = BubbleBus.hasTrack,
                        onAction = { id -> onRingAction(id) },
                        onDismiss = { hideMenu() }
                    )
                }
            }
        }
        runCatching { windowManager.addView(view, overlayParams) }
            .onFailure { hideMenu() }
        menuView = view
        menuParams = overlayParams
    }

    private fun hideMenu() {
        val view = menuView ?: return
        menuView = null
        runCatching { windowManager.removeViewImmediate(view) }
        menuParams = null
    }

    private fun onRingAction(id: String) {
        when (id) {
            // 播放控制直连播放器：桌面场景下 App 可能没活、BubbleBus 没有订阅者，
            // 发事件会被静默丢弃（“点了没反应”的老问题）
            BubbleRingItems.PLAY_PAUSE -> scope.launch {
                val c = connectController() ?: return@launch
                if (c.mediaItemCount == 0) return@launch
                if (c.isPlaying) c.pause() else c.play()
            }
            BubbleRingItems.PREV -> scope.launch {
                val c = connectController() ?: return@launch
                if (c.mediaItemCount == 0) return@launch
                if (c.currentMediaItemIndex == 0) {
                    c.seekTo(c.mediaItemCount - 1, 0L)
                } else {
                    c.seekToPreviousMediaItem()
                }
                c.prepare()
                c.playWhenReady = true
            }
            BubbleRingItems.NEXT -> scope.launch {
                val c = connectController() ?: return@launch
                if (c.mediaItemCount == 0) return@launch
                if (c.hasNextMediaItem() || c.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL) {
                    c.seekToNextMediaItem()
                    c.prepare()
                    c.playWhenReady = true
                }
            }
            BubbleRingItems.RECOGNIZE_MIC -> {
                BubbleBus.requestRecognize("mic")
                launchAppForRecognize("mic")
            }
            BubbleRingItems.RECOGNIZE_SYSTEM -> {
                BubbleBus.requestRecognize("system")
                launchAppForRecognize("system")
            }
            // 收藏要碰本地收藏库，只能交回 App 进程处理
            BubbleRingItems.FAVORITE -> BubbleBus.send(BubbleBus.Command.Favorite)
            BubbleRingItems.OPEN_APP -> launchApp()
            BubbleRingItems.CLOSE -> BubbleBus.send(BubbleBus.Command.Close)
        }
    }

    /** 进入软件：直接拉起 MainActivity（不能只发 bus 事件，UI 侧没有处理者时等于没效果） */
    private fun launchApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        runCatching { startActivity(intent) }
    }

    private fun launchAppForRecognize(mode: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
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
                unlocked = emptySet(),
                // 已购买的动作才允许播；和 App 内宠物页保持一致，否则会触发未购买素材的下载
                sellable = PetContentStore.sellableClips(characterId)
            )
            engineRef = engine
            // 引擎就绪前用户点过的，补播一次互动
            if (pendingTap) {
                pendingTap = false
                engine.tap()
            }
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
