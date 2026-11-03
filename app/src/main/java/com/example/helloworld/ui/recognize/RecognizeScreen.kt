package com.example.helloworld.ui.recognize

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.helloworld.MainViewModel
import com.example.helloworld.ui.home.Track

/**
 * 听歌识曲页：麦克风 / 系统内录双音源，结果列表可直接播放。
 * 底部附「悬浮球」开关（需要系统悬浮窗权限，未授权时跳系统设置申请）。
 */
@Composable
fun RecognizeScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (granted) viewModel.startMicRecognize()
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startSystemRecognize(result.data)
        } else {
            viewModel.resetRecognize()
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayAllowed = Settings.canDrawOverlays(context)
    }

    fun requestMic() {
        if (micGranted) viewModel.startMicRecognize()
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun requestSystem() {
        if (!AudioCaptures.canCaptureSystemAudio()) return
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    // 悬浮球带模式进入：自动开录
    LaunchedEffect(Unit) {
        when (viewModel.consumeRecognizeMode()) {
            "mic" -> requestMic()
            "system" -> requestSystem()
        }
    }

    BackHandler { viewModel.closeRecognize() }

    val phase = viewModel.recognizePhase

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "听歌识曲",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "让音河听 11 秒，告诉你这是什么歌",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            }
            IconButton(onClick = { viewModel.closeRecognize() }) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }

        Spacer(Modifier.height(18.dp))

        when (phase) {
            "recording", "uploading" -> RecordingCard(
                progress = viewModel.recognizeProgress,
                uploading = phase == "uploading",
                onCancel = { viewModel.resetRecognize() }
            )
            else -> SourceCards(
                systemSupported = AudioCaptures.canCaptureSystemAudio(),
                onMic = ::requestMic,
                onSystem = ::requestSystem
            )
        }

        // 结果 / 错误
        if (phase == "done") {
            Spacer(Modifier.height(14.dp))
            val error = viewModel.recognizeError
            if (error != null) {
                Text(
                    error,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row {
                    ResultActionButton("再来一次") { viewModel.resetRecognize() }
                }
            } else if (viewModel.recognizeResults.isNotEmpty()) {
                Text(
                    "识别结果 · 点击播放",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(viewModel.recognizeResults, key = { it.id }) { track ->
                        ResultRow(track) { viewModel.playTrack(track, viewModel.recognizeResults) }
                    }
                    item {
                        Row(modifier = Modifier.padding(top = 10.dp)) {
                            ResultActionButton("再来一次") { viewModel.resetRecognize() }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f, fill = false))

        // 悬浮球开关
        BubbleToggle(
            allowed = overlayAllowed,
            enabled = viewModel.bubbleEnabled,
            onRequestPermission = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                runCatching { overlayLauncher.launch(intent) }
            },
            onToggle = { viewModel.setBubbleEnabled(it) }
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SourceCards(systemSupported: Boolean, onMic: () -> Unit, onSystem: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SourceCard(
            title = "麦克风识曲",
            subtitle = "听设备外面的声音：音箱、别人手机正在放的歌",
            onClick = onMic
        )
        SourceCard(
            title = if (systemSupported) "系统声音识曲" else "系统声音识曲（需 Android 10+）",
            subtitle = "捕获本机正在播放的音频，无需外放",
            enabled = systemSupported,
            onClick = onSystem
        )
    }
}

@Composable
private fun SourceCard(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 0.06f else 0.03f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.35f)
        )
        Text(
            subtitle,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun RecordingCard(progress: Float, uploading: Boolean, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (uploading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(10.dp))
            Text("正在生成指纹并识别…", fontSize = 13.sp)
        } else {
            Text("正在聆听…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
            )
            Spacer(Modifier.height(8.dp))
            Text("${(progress * 100).toInt()}%", fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        ResultActionButton(if (uploading) "重新开始" else "取消") { onCancel() }
    }
}

@Composable
private fun ResultRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artist,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "播放",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ResultActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun BubbleToggle(
    allowed: Boolean,
    enabled: Boolean,
    onRequestPermission: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("桌面悬浮球", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (allowed) "小精灵常驻桌面：单击播放/暂停，长按菜单含识曲" else "需要授予「显示在其他应用上层」权限",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
        Spacer(Modifier.width(10.dp))
        if (allowed) {
            Switch(checked = enabled, onCheckedChange = onToggle)
        } else {
            ResultActionButton("去授权") { onRequestPermission() }
        }
    }
}
