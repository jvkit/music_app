package com.example.helloworld.ui.im

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helloworld.MainViewModel
import com.example.helloworld.data.local.ImMessageEntity
import com.example.helloworld.data.remote.ApiTrack
import com.example.helloworld.ui.components.AmbientBackground
import com.example.helloworld.ui.components.GlassSurface
import com.example.helloworld.ui.components.PET_BUBBLES
import com.example.helloworld.ui.components.PetAvatar
import com.example.helloworld.ui.components.TrackArtwork
import com.example.helloworld.ui.home.Track
import com.example.helloworld.data.remote.toUiTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** 好友列表：在线点、未读角标、点击进入聊天、添加好友 */
@Composable
fun FriendsScreen(viewModel: MainViewModel) {
    val friends by viewModel.friends.collectAsState()
    val unread by viewModel.unreadCounts.collectAsState()
    val connected by viewModel.imConnected.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::closeIm) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text(
                    text = "好友",
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                // 在线状态点
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (connected) Color(0xFF4ADE80) else Color(0xFF64748B))
                )
                Text(
                    text = if (connected) " 在线" else " 离线",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "添加好友", tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (friends.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有好友", color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "点右上角搜索用户名添加",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(friends, key = { it.username }) { friend ->
                        val count = unread[friend.username] ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .clickable { viewModel.openChat(friend.username) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 头像 + 在线点
                            Box {
                                com.example.helloworld.ui.components.UserAvatar(
                                    url = viewModel.avatarUrl(friend.username),
                                    name = friend.username,
                                    size = 40.dp
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (friend.online) Color(0xFF4ADE80) else Color(0xFF475569))
                                )
                            }
                            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(friend.username, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    if (friend.online) "在线" else "离线",
                                    color = Color.White.copy(alpha = if (friend.online) 0.7f else 0.4f),
                                    fontSize = 11.sp
                                )
                            }
                            if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text("$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            TextButton(onClick = { viewModel.removeFriend(friend.username) }) {
                                Text("删除", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddFriendDialog(
            onDismiss = { showAdd = false },
            onLookup = { q, cb -> viewModel.lookupUsers(q, cb) },
            onAdd = { name, cb -> viewModel.addFriend(name, cb) }
        )
    }
}

@Composable
private fun AddFriendDialog(
    onDismiss: () -> Unit,
    onLookup: (String, (List<String>) -> Unit) -> Unit,
    onAdd: (String, (Boolean, String) -> Unit) -> Unit
) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<String>?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加好友") },
        text = {
            Column {
                OutlinedTextField(
                    value = q,
                    onValueChange = {
                        q = it
                        hint = null
                        if (it.isBlank()) results = null else onLookup(it) { results = it }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索用户名") },
                    singleLine = true
                )
                hint?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                results?.let { list ->
                    if (list.isEmpty()) {
                        Text("没有匹配的用户", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            items(list) { name ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onAdd(name) { ok, msg -> hint = msg; if (ok) results = null }
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(name, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

/** 聊天页：灰底（融合透明宠物素材）+ 气泡 + 音乐卡片 + 宠物位 + 邀请一起听 */
@Composable
fun ChatScreen(viewModel: MainViewModel, peer: String) {
    val messages by viewModel.messagesFor(peer).collectAsState(initial = emptyList())
    val currentTrack = viewModel.currentTrack
    val pets by viewModel.pets.collectAsState()
    val pet = pets[peer]
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    // 宠物互动触发器：本地点击 / 对方 WS 云逗宠 各 +1 触发一次随机互动
    var petTrigger by remember { mutableLongStateOf(0L) }
    var petBubble by remember { mutableStateOf<String?>(null) }
    // 点击冷却：一次互动后 3s 内再点不生效（对齐网站 COOL≈2.6s，取整 3s）
    var lastPetTapMs by remember { mutableLongStateOf(0L) }
    // 气泡 2.2s 自动消（对齐网站版）
    LaunchedEffect(petBubble) {
        if (petBubble != null) {
            kotlinx.coroutines.delay(2_200)
            petBubble = null
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
        viewModel.refreshFriends()
    }
    // 对方云逗宠：动作实时同步到我方聊天页
    LaunchedEffect(peer) {
        viewModel.petActions.collect { event ->
            if (event.from == peer && event.action != "eat") {
                petTrigger++
            }
        }
    }

    // 宠物可拖区测量：顶栏下沿 ~ 输入栏上沿（相对聊天根）
    var rootBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var topBarBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var inputBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val petSizePx = with(density) { 88.dp.toPx() }
    val areaWidth = rootBounds.width
    val areaTop = (topBarBounds.bottom - rootBounds.top).coerceAtLeast(0f)
    val areaBottom = (inputBounds.top - rootBounds.top).takeIf { it > areaTop } ?: (rootBounds.bottom - rootBounds.top)
    val areaHeight = (areaBottom - areaTop).coerceAtLeast(petSizePx)
    val measured = inputBounds != androidx.compose.ui.geometry.Rect.Zero
    val defaultPos = androidx.compose.ui.geometry.Offset(
        x = (areaWidth - petSizePx - with(density) { 12.dp.toPx() }).coerceAtLeast(0f),
        y = (areaTop + areaHeight - petSizePx - with(density) { 8.dp.toPx() }).coerceAtLeast(areaTop)
    )
    val petPosition = viewModel.petPositions[peer] ?: defaultPos

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF8A8F98), Color(0xFF6E747E))))
            .onGloballyPositioned { rootBounds = it.boundsInRoot() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .onGloballyPositioned { topBarBounds = it.boundsInRoot() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::openFriends) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                com.example.helloworld.ui.components.UserAvatar(
                    url = viewModel.avatarUrl(peer),
                    name = peer,
                    size = 32.dp
                )
                Text(
                    peer,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
                TextButton(onClick = { viewModel.inviteListen(peer) }) {
                    Text("邀请一起听", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }

            // 消息列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    when (msg.msgType) {
                        "share_track" -> TrackCardMessage(
                            msg = msg,
                            onPlay = { track ->
                                track?.let { viewModel.playTrack(it, listOf(it)) }
                            }
                        )
                        "listen_invite" -> ListenInviteMessage(
                            msg = msg,
                            isPartner = viewModel.listenPartner == peer,
                            onAccept = { viewModel.followListen(peer, msg.ts) },
                            onStop = { viewModel.stopFollowListen() }
                        )
                        "share_playlist" -> SharedPlaylistMessage(
                            msg = msg,
                            onAdd = {
                                viewModel.addSharedPlaylist(msg.trackJson) { err ->
                                    // 简单反馈：失败时由重试按钮文案体现，成功直接落库
                                }
                            }
                        )
                        "pet_invite" -> PetInviteMessage(
                            msg = msg,
                            adopted = pet != null,
                            onAccept = { viewModel.acceptAdopt(peer) }
                        )
                        else -> ChatBubble(msg)
                    }
                }
            }

            // 宠物位：未领养显示无精灵提示（领养入口/合养中提示）
            if (pet == null) {
                val otherPet = pets.keys.firstOrNull { it != peer }
                NoPetHint(
                    hasOther = otherPet != null,
                    otherName = otherPet,
                    onInvite = { viewModel.inviteAdopt(peer) }
                )
            }

            // 输入栏
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .onGloballyPositioned { inputBounds = it.boundsInRoot() },
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 分享当前歌曲
                    IconButton(onClick = { viewModel.shareTrackTo(peer, currentTrack) }) {
                        Icon(Icons.Default.Favorite, contentDescription = "分享当前歌曲", tint = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("说点什么…", fontSize = 13.sp) },
                        maxLines = 3
                    )
                    IconButton(
                        onClick = {
                            viewModel.sendChat(peer, input)
                            input = ""
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        // 宠物悬浮层（已领养）：可拖动、点击互动；等输入栏测量完再出来，落在默认右下角
        if (pet != null && measured) {
            PetOverlay(
                pet = pet,
                trigger = petTrigger,
                musicPlaying = viewModel.isPlaying,
                bubbleText = petBubble,
                areaTop = areaTop,
                areaHeight = areaHeight,
                areaWidth = areaWidth,
                petSizePx = petSizePx,
                position = petPosition,
                onPositionChange = { viewModel.petPositions[peer] = it },
                onTap = {
                    // 3 秒冷却：连点不生效（动作播完+节奏可控）
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastPetTapMs >= 3_000) {
                        lastPetTapMs = nowMs
                        petTrigger++
                        viewModel.petInteract(peer, pet.actionsUnlocked.random())
                        petBubble = PET_BUBBLES.random()
                    }
                }
            )
        }
    }
}

/** 聊天页无精灵占位：未养过给邀请入口；已和别人合养则提示暂只支持一位 */
@Composable
private fun NoPetHint(hasOther: Boolean, otherName: String?, onInvite: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )
        if (hasOther && otherName != null) {
            Text(
                "  无精灵 · 已和 $otherName 合养（暂只支持一位）",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp
            )
        } else {
            Text(
                "  无精灵 · 去找好友一起养吧",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onInvite) {
                Text("邀请合养", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
    }
}

/** 聊天页宠物（已领养）：悬浮可拖动（限制在顶栏与输入栏之间），点击随机播动作 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PetOverlay(
    pet: com.example.helloworld.data.remote.PetPairDto,
    trigger: Long,
    musicPlaying: Boolean,
    bubbleText: String?,
    areaTop: Float,
    areaHeight: Float,
    areaWidth: Float,
    petSizePx: Float,
    position: androidx.compose.ui.geometry.Offset,
    onPositionChange: (androidx.compose.ui.geometry.Offset) -> Unit,
    onTap: () -> Unit
) {
    // 手势回调与位置用 rememberUpdatedState 包一层，避免 pointerInput 协程拿到过期闭包
    val currentOnTap by androidx.compose.runtime.rememberUpdatedState(onTap)
    val currentOnPositionChange by androidx.compose.runtime.rememberUpdatedState(onPositionChange)
    val currentPosition by androidx.compose.runtime.rememberUpdatedState(position)

    // 拖动中只改渲染层（graphicsLayer），布局位置不变 → 坐标系稳定，跟手 1:1；
    // 松手时一次性提交布局位置
    var dragVisual by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var dragBase by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset {
                androidx.compose.ui.unit.IntOffset(
                    position.x.toInt(),
                    position.y.toInt()
                )
            }
            .graphicsLayer {
                translationX = dragVisual.x
                translationY = dragVisual.y
            }
            // clickable 先声明（外层）、drag 后声明（内层先收事件）：
            // 拖动被内层 consume 后 clickable 自动取消 tap，二者共存；
            // indication=null 去掉点击的方框/涟漪视觉反馈
            .clickable(
                interactionSource = com.example.helloworld.ui.components.rememberNoRippleSource(),
                indication = null,
                onClick = { currentOnTap() }
            )
            .pointerInput(areaTop, areaHeight, areaWidth) {
                detectDragGestures(
                    onDragStart = {
                        dragBase = currentPosition
                        dragVisual = androidx.compose.ui.geometry.Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragVisual += dragAmount
                    },
                    onDragEnd = {
                        val target = dragBase + dragVisual
                        val nx = target.x.coerceIn(0f, (areaWidth - petSizePx).coerceAtLeast(0f))
                        val ny = target.y.coerceIn(areaTop, (areaTop + areaHeight - petSizePx).coerceAtLeast(areaTop))
                        currentOnPositionChange(androidx.compose.ui.geometry.Offset(nx, ny))
                        dragVisual = androidx.compose.ui.geometry.Offset.Zero
                    },
                    onDragCancel = {
                        dragVisual = androidx.compose.ui.geometry.Offset.Zero
                    }
                )
            }
    ) {
        // 头顶徽标：等级 + 火花
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text("Lv${pet.level}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (pet.flameDays > 0) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF97316).copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text("火花 x${pet.flameDays}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // 精灵 + 悬浮气泡：外层 Box 固定为精灵尺寸，气泡只溢出绘制、不参与测量，
        // 否则气泡出现时 wrap-content 列宽突变，精灵会被重新居中而水平瞬移
        Box(
            modifier = Modifier.width(88.dp * 3 / 4).height(88.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            PetAvatar(species = pet.species, trigger = trigger, size = 88.dp, musicPlaying = musicPlaying, animate = true)
            bubbleText?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-42).dp)
                        .widthIn(max = 170.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        it,
                        color = Color(0xFF334155),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 12.sp,
                        style = TextStyle(
                            lineHeightStyle = LineHeightStyle(
                                LineHeightStyle.Alignment.Center,
                                LineHeightStyle.Trim.Both
                            )
                        )
                    )
                }
            }
        }
    }
}

/** 领养邀请消息：对方发的可同意，同意后双方解锁宠物 */
@Composable
private fun PetInviteMessage(
    msg: ImMessageEntity,
    adopted: Boolean,
    onAccept: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.outgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(12.dp)
        ) {
            Text(msg.text.ifBlank { "邀请你一起领养宠物" }, color = Color.White, fontSize = 13.sp)
            if (!msg.outgoing && !adopted) {
                TextButton(onClick = onAccept) {
                    Text("同意领养", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            } else if (adopted) {
                Text("宠物已解锁", color = Color(0xFF4ADE80), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ImMessageEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.outgoing) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp, 18.dp, if (msg.outgoing) 4.dp else 18.dp, if (msg.outgoing) 18.dp else 4.dp))
                .background(
                    if (msg.outgoing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(
                text = msg.text,
                color = if (msg.outgoing) Color.White else Color.White.copy(alpha = 0.92f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun TrackCardMessage(msg: ImMessageEntity, onPlay: (Track?) -> Unit) {
    val track = remember(msg.trackJson) {
        if (msg.trackJson.isBlank()) null
        else runCatching {
            com.google.gson.Gson().fromJson(msg.trackJson, ApiTrack::class.java)
        }.getOrNull()?.toUiTrack()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.outgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .clickable { onPlay(track) }
                .padding(10.dp)
                .width(240.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                track?.let { TrackArtwork(track = it, modifier = Modifier.size(42.dp), cornerSize = 12, showLabel = false) }
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        track?.title ?: "未知歌曲",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track?.artist ?: "",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("点击播放", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ListenInviteMessage(
    msg: ImMessageEntity,
    isPartner: Boolean,
    onAccept: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.outgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(12.dp)
        ) {
            Text(msg.text.ifBlank { "邀请你一起听" }, color = Color.White, fontSize = 13.sp)
            if (!msg.outgoing && !isPartner) {
                TextButton(onClick = onAccept) {
                    Text("加入一起听", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            } else if (isPartner) {
                TextButton(onClick = onStop) {
                    Text("退出一起听", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
    }
}

/** 歌单分享卡片：展示歌单名 + 曲数，点「添加」整单入自己的歌单 */
@Composable
private fun SharedPlaylistMessage(msg: ImMessageEntity, onAdd: () -> Unit) {
    val code = msg.trackJson
    var trackCount by remember(code) { mutableStateOf<Int?>(null) }
    var state by remember(code) { mutableStateOf(0) } // 0 空闲 1 添加中 2 成功 3 失败
    LaunchedEffect(code) {
        if (code.isBlank()) return@LaunchedEffect
        runCatching {
            com.example.helloworld.data.remote.MusicApiClient.service.getShare(code)
        }.onSuccess { trackCount = it.playlist?.tracks?.size ?: 0 }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.outgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(12.dp)
        ) {
            Text(
                "分享歌单" + if (msg.text.isNotBlank()) "《${msg.text}》" else "",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp
            )
            Text(
                msg.text.ifBlank { "一个歌单" },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            trackCount?.let {
                Text("共 $it 首", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            if (state == 2) {
                Text("已添加到你的歌单", color = Color(0xFF4ADE80), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            } else {
                TextButton(
                    enabled = code.isNotBlank() && state != 1,
                    onClick = {
                        state = 1
                        onAdd()
                        state = 2
                    }
                ) {
                    Text(
                        if (state == 1) "添加中…" else "添加",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
