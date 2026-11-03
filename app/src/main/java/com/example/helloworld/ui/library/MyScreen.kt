package com.example.helloworld.ui.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.helloworld.data.model.Playlist
import com.example.helloworld.ui.components.GlassSurface

@Composable
fun MyScreen(
    playlists: List<Playlist>,
    favoriteCount: Int,
    cachedCount: Int,
    username: String?,
    myAvatarUrl: String? = null,
    avatarPresets: List<String> = emptyList(),
    avatarPresetUrl: (String) -> String = { "" },
    onPickAvatar: (android.net.Uri, (String?) -> Unit) -> Unit = { _, _ -> },
    onPickPreset: (String, (String?) -> Unit) -> Unit = { _, _ -> },
    onChangeUsername: (String, (String?) -> Unit) -> Unit = { _, _ -> },
    onChangePassword: (String, String, (String?) -> Unit) -> Unit = { _, _, _ -> },
    onCheckUpdate: ((Boolean, String?) -> Unit) -> Unit = {},
    onSubmitFeedback: (String, (String?) -> Unit) -> Unit = { _, _ -> },
    onUploadLogs: ((String?) -> Unit) -> Unit = {},
    onOpenFriends: () -> Unit = {},
    unreadTotal: Int = 0,
    onLogout: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenLocal: () -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onUpdatePlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deleteCandidate by remember { mutableStateOf<Playlist?>(null) }
    var expandedMenuId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }

    // 用户中心：改名 / 改密 / 头像
    var showUserCenter by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    var userCenterBusy by remember { mutableStateOf(false) }
    // 系统 Photo Picker：无需存储权限、自动授权 uri（GetContent 在部分 ROM 上会被相册权限卡死，弃用）
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            userCenterBusy = true
            avatarError = null
            onPickAvatar(uri) { err ->
                userCenterBusy = false
                if (err == null) showAvatarPicker = false
                else avatarError = err
            }
        }
    }
    val launchAvatarPicker: () -> Unit = {
        avatarPicker.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 188.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "我的", fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = "把喜欢的声音，收进自己的河",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showUserCenter = true }
                            .padding(4.dp)
                    ) {
                        com.example.helloworld.ui.components.UserAvatar(
                            url = myAvatarUrl,
                            name = username,
                            size = 34.dp
                        )
                        Text(
                            text = username?.let { "账号 · $it" } ?: "未登录",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (unreadTotal > 0) "好友·$unreadTotal" else "好友",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(onClick = onOpenFriends)
                                .padding(top = 2.dp, end = 12.dp)
                        )
                        Text(
                            text = "退出",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(onClick = onLogout)
                                .padding(top = 2.dp)
                        )
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "服务器设置",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = playlists.size.toString(), label = "歌单")
                    StatItem(value = favoriteCount.toString(), label = "收藏")
                    StatItem(value = cachedCount.toString(), label = "已下载")
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.76f))
                    .clickable { showLogs = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "帮助音河变得更好", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "出问题时点这里把日志发给我们，排查快很多",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = "上报日志 ›",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        item {
            SectionHeader(title = "特别歌单", subtitle = "SPECIAL PLAYLISTS")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SpecialPlaylistCard(
                    title = "我的收藏",
                    subtitle = "$favoriteCount 首",
                    gradient = listOf(Color(0xFFFF5CA8), Color(0xFF774EFF)),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    onClick = onOpenFavorites,
                    modifier = Modifier.weight(1f)
                )
                SpecialPlaylistCard(
                    title = "本地歌曲",
                    subtitle = "$cachedCount 首",
                    gradient = listOf(Color(0xFF1A9DB5), Color(0xFF4261C9)),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    onClick = onOpenLocal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "我的歌单", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "PLAYLIST COLLECTION",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp
                    )
                }
                IconButton(
                    onClick = { showCreate = true },
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(16.dp)
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新建歌单", tint = Color.White)
                }
            }
        }
        if (playlists.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                        .padding(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "还没有自建歌单", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        text = "点右上角加号，建一个属于自己的歌单",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        } else {
            itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                PlaylistCard(
                    playlist = playlist,
                    index = index,
                    menuExpanded = expandedMenuId == playlist.id,
                    onMenuChange = { expandedMenuId = if (it) playlist.id else null },
                    onClick = { onOpenPlaylist(playlist.id) },
                    onEdit = {
                        expandedMenuId = null
                        editingPlaylist = playlist
                    },
                    onDelete = {
                        expandedMenuId = null
                        deleteCandidate = playlist
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }

    if (showCreate) {
        PlaylistEditorDialog(
            playlist = null,
            onDismiss = { showCreate = false },
            onConfirm = { name, description ->
                onCreatePlaylist(name, description)
                showCreate = false
            }
        )
    }
    editingPlaylist?.let { playlist ->
        PlaylistEditorDialog(
            playlist = playlist,
            onDismiss = { editingPlaylist = null },
            onConfirm = { name, description ->
                onUpdatePlaylist(playlist.copy(name = name, description = description))
                editingPlaylist = null
            }
        )
    }
    deleteCandidate?.let { playlist ->
        DeletePlaylistDialog(
            playlist = playlist,
            onDismiss = { deleteCandidate = null },
            onConfirm = {
                onDeletePlaylist(playlist)
                deleteCandidate = null
            }
        )
    }
    if (showSettings) {
        AboutDialog(
            onDismiss = { showSettings = false },
            onCheckUpdate = onCheckUpdate,
            onSubmitFeedback = onSubmitFeedback,
            onUploadLogs = onUploadLogs
        )
    }
    if (showLogs) {
        var uploading by remember { mutableStateOf(false) }
        var uploadResult by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showLogs = false },
            title = { Text("上报运行日志", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    uploadResult ?: "将最近的运行日志（换源/失败/搜索记录）发送给开发者用于排查问题，日志不含账号密码。",
                    fontSize = 13.sp,
                    color = if (uploadResult != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !uploading && uploadResult == null,
                    onClick = {
                        uploading = true
                        onUploadLogs { err ->
                            uploading = false
                            uploadResult = err ?: "已上报，感谢！"
                        }
                    }
                ) { Text(if (uploading) "上传中…" else "确定上报") }
            },
            dismissButton = {
                TextButton(onClick = { showLogs = false }) { Text("取消") }
            }
        )
    }

    // ===== 用户中心 =====
    if (showUserCenter) {
        AlertDialog(
            onDismissRequest = { showUserCenter = false },
            title = { Text("用户中心", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !userCenterBusy) { avatarError = null; showAvatarPicker = true }
                            .padding(vertical = 10.dp)
                    ) {
                        com.example.helloworld.ui.components.UserAvatar(
                            url = myAvatarUrl,
                            name = username,
                            size = 44.dp
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text("更换头像", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "从相册选择（自动压缩）",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "修改用户名",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showRename = true; renameError = null }
                            .padding(vertical = 12.dp)
                    )
                    Text(
                        text = "修改密码",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showPassword = true; passwordError = null }
                            .padding(vertical = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserCenter = false }) { Text("关闭") }
            }
        )
    }
    if (showAvatarPicker) {
        AlertDialog(
            onDismissRequest = { showAvatarPicker = false },
            title = { Text("更换头像", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    avatarError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        "选一个默认头像：",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    avatarPresets.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowItems.forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(58.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = !userCenterBusy) {
                                            userCenterBusy = true
                                            onPickPreset(preset) { err ->
                                                userCenterBusy = false
                                                if (err == null) showAvatarPicker = false
                                                else avatarError = err
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = avatarPresetUrl(preset),
                                        contentDescription = preset,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            }
                            repeat(4 - rowItems.size) { Spacer(Modifier.size(66.dp)) }
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !userCenterBusy) { launchAvatarPicker() }
                            .padding(vertical = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "上传",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text("从相册上传", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "自动压缩到 512px 内",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarPicker = false }) { Text("取消") }
            }
        )
    }
    if (showRename) {
        var newName by remember { mutableStateOf(username.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("修改用户名", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; renameError = null },
                        label = { Text("新用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    renameError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !userCenterBusy && newName.isNotBlank() && newName != username,
                    onClick = {
                        userCenterBusy = true
                        onChangeUsername(newName.trim()) { err ->
                            userCenterBusy = false
                            if (err == null) showRename = false else renameError = err
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            }
        )
    }
    if (showPassword) {
        var oldPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPassword = false },
            title = { Text("修改密码", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldPass,
                        onValueChange = { oldPass = it; passwordError = null },
                        label = { Text("旧密码") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it; passwordError = null },
                        label = { Text("新密码") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    passwordError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !userCenterBusy && oldPass.isNotBlank() && newPass.isNotBlank(),
                    onClick = {
                        userCenterBusy = true
                        onChangePassword(oldPass, newPass) { err ->
                            userCenterBusy = false
                            if (err == null) {
                                showPassword = false
                            } else {
                                passwordError = err
                            }
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPassword = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(text = title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 25.sp,
            fontWeight = FontWeight.Black
        )
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun SpecialPlaylistCard(
    title: String,
    subtitle: String,
    gradient: List<Color>,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.76f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    index: Int,
    menuExpanded: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palettes = listOf(
        listOf(Color(0xFF774EFF), Color(0xFFFF5CA8)),
        listOf(Color(0xFF1A9DB5), Color(0xFF5E6BFF)),
        listOf(Color(0xFFFF815C), Color(0xFFE84D91)),
        listOf(Color(0xFF4261C9), Color(0xFF23B89A))
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.76f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(palettes[index % palettes.size])),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = playlist.name.take(1),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = playlist.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.trackIds.size} 首歌曲",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
            Text(
                text = playlist.description.ifBlank { "还没有写下这个歌单的故事" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Box {
            IconButton(onClick = { onMenuChange(true) }) {
                Icon(Icons.Default.MoreVert, contentDescription = "歌单操作")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuChange(false) }) {
                DropdownMenuItem(
                    text = { Text("编辑歌单") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = onEdit
                )
                DropdownMenuItem(
                    text = { Text("删除歌单") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onCheckUpdate: ((Boolean, String?) -> Unit) -> Unit,
    onSubmitFeedback: (String, (String?) -> Unit) -> Unit,
    onUploadLogs: ((String?) -> Unit) -> Unit
) {
    var feedback by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var copyDone by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于与反馈", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "音河音乐 v" + com.example.helloworld.BuildConfig.VERSION_NAME,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "检查更新",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !busy) {
                                busy = true
                                copyDone = false
                                downloadUrl = null
                                status = "正在检查更新…"
                                onCheckUpdate { hasNew, apkUrl ->
                                    busy = false
                                    if (hasNew && !apkUrl.isNullOrBlank()) {
                                        downloadUrl = apkUrl
                                        status = "发现新版本！"
                                    } else if (hasNew) {
                                        status = "发现新版本，请联系开发者获取安装包"
                                    } else {
                                        status = "已是最新版本"
                                    }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                Text(
                    text = "音河音乐由个人开发者独立开发与维护，不是大厂出品，但每一行代码都奔着「找歌快、播得稳、收藏不丢」去做的。非常希望得到你的反馈——哪里难用、想加什么功能、发现了什么 bug，直接写下来提交，每条都会看，都会直接影响音河的下一步。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
                // 权限检查：缺哪个点哪个去申请（悬浮球/识曲都要系统授权，装完默认是不给的）
                PermissionSection(context)
                status?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                // 发现新版本：打开下载页（跳浏览器）+ 复制网址
                downloadUrl?.let { url ->
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "打开下载页",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Text(
                            text = if (copyDone) "已复制 ✓" else "复制网址",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (copyDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !copyDone) {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("下载地址", url))
                                    copyDone = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = url,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    placeholder = { Text("写点反馈…", fontSize = 12.sp) },
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && feedback.isNotBlank(),
                onClick = {
                    busy = true
                    onSubmitFeedback(feedback.trim()) { err ->
                        busy = false
                        status = err ?: "已收到反馈，谢谢你！"
                        if (err == null) feedback = ""
                    }
                }
            ) { Text("提交反馈") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}
