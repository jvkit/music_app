package com.example.helloworld.ui.library

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 设置里的权限自检。
 *
 * 悬浮球要「显示在其他应用上层」，识曲要麦克风，部分机型还要关掉电池优化才不会被杀，
 * 这些都是**系统设置页**授权的，装完默认不给。这里把状态摆出来，缺的点了直接跳对应系统页。
 */
private data class PermItem(
    val title: String,
    val why: String,
    val granted: Boolean,
    val request: () -> Unit,
)

@Composable
fun PermissionSection(
    context: Context,
    bubbleEnabled: Boolean = false,
    onToggleBubble: (Boolean) -> Unit = {}
) {
    // 从系统设置页返回后要重新检查，用一个自增计数触发重算
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDetail by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val items = remember(refresh) { buildPermItems(context) { refresh++ } }
    val missing = items.filterNot { it.granted }

    // 悬浮球总开关：放这里而不是藏在识曲页（识曲页本身要从悬浮球菜单进，那是死循环）
    val overlayGranted = items.firstOrNull()?.granted == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("桌面悬浮球", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                if (!overlayGranted) "先开启「悬浮在其他应用上层」权限"
                else if (bubbleEnabled) "已常驻桌面：单击互动，长按打开环形菜单"
                else "悬浮球已关闭，开启后小精灵常驻桌面",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        androidx.compose.material3.Switch(
            checked = bubbleEnabled && overlayGranted,
            enabled = overlayGranted,
            onCheckedChange = onToggleBubble
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { showDetail = !showDetail }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("权限检查", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                if (missing.isEmpty()) "全部已授予 ✓"
                else "还有 ${missing.size} 项没开：${missing.joinToString("、") { it.title }}",
                fontSize = 11.sp,
                color = if (missing.isEmpty()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            if (showDetail) "收起" else "展开",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showDetail) {
        Column(modifier = Modifier.padding(top = 6.dp)) {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (item.granted) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            item.why,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.granted) {
                        Text("已开启", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(
                            "去开启",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { item.request() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun buildPermItems(context: Context, onChange: () -> Unit): List<PermItem> {
    val pkg = context.packageName
    val canOverlay = Settings.canDrawOverlays(context)
    val micGranted = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    val notifyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else true

    return listOf(
        PermItem(
            title = "悬浮在桌面上的小精灵",
            why = "需要「显示在其他应用上层」，否则悬浮球出不来",
            granted = canOverlay,
            request = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$pkg")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                onChange()
            }
        ),
        PermItem(
            title = "麦克风",
            why = "听歌识曲（手机外声音）要用，也用于语音输入",
            granted = micGranted,
            request = {
                // 运行时权限要从 Activity 申请，这里退而求其次跳应用详情页，用户手动开
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$pkg")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                onChange()
            }
        ),
        PermItem(
            title = "通知",
            why = "播放控制条和悬浮球的前台服务通知",
            granted = notifyGranted,
            request = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                onChange()
            }
        )
    )
}
