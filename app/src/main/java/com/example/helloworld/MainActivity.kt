package com.example.helloworld

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.example.helloworld.bubble.BubbleBus
import com.example.helloworld.bubble.BubbleService
import com.example.helloworld.ui.theme.YinHeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeRecognizeIntent(intent)
        setContent {
            YinHeTheme {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                YinHeApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeRecognizeIntent(intent)
    }

    /** 悬浮球「识曲」菜单拉起应用时，把模式交给 BubbleBus（冷启动时由 YinHeApp 首帧消费） */
    private fun consumeRecognizeIntent(intent: Intent?) {
        val mode = intent?.getStringExtra(BubbleService.EXTRA_RECOGNIZE_MODE) ?: return
        intent.removeExtra(BubbleService.EXTRA_RECOGNIZE_MODE)
        BubbleBus.requestRecognize(mode)
    }
}
