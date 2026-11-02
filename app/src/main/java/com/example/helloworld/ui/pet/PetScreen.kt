package com.example.helloworld.ui.pet

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helloworld.MainViewModel
import com.example.helloworld.data.remote.PetPairDto
import com.example.helloworld.data.remote.PetShopItem
import com.example.helloworld.data.remote.PetShelfItem
import com.example.helloworld.data.remote.PetActionDef
import com.example.helloworld.data.remote.PetTaskDto
import com.example.helloworld.ui.components.AmbientBackground
import com.example.helloworld.ui.components.PetAvatar
import com.example.helloworld.ui.components.PetContentStore
import com.example.helloworld.ui.components.petActionName
import com.example.helloworld.ui.components.petSpeciesName
import com.example.helloworld.ui.components.petTaskName

private val ALL_ACTIONS = listOf("idle", "jump", "spin", "dance", "sleep", "happy", "eat", "angry", "love")

/** 精灵中心：好友对共养宠物的任务/投喂/角色/图鉴 */
@Composable
fun PetScreen(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pets by viewModel.pets.collectAsState()
    val shop by viewModel.petShop.collectAsState()
    val shelf by viewModel.petShelf.collectAsState()
    val actionCatalog by viewModel.petActionShop.collectAsState()
    var selectedFriend by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    // 投喂/互动触发器：+1 触发一次随机互动
    var feedTrigger by remember { mutableLongStateOf(0L) }
    var feedMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshPets()
        // 每次进宠物中心刷新素材清单，服务端新增角色/物种无需发版即可生效
        PetContentStore.init(context)
        PetContentStore.refresh()
    }
    // 默认选中第一只宠物；当前选中失效时回退
    val friend = selectedFriend?.takeIf { pets.containsKey(it) } ?: pets.keys.firstOrNull()
    val pair = friend?.let { pets[it] }
    LaunchedEffect(friend, pair?.level) {
        if (friend != null) viewModel.refreshPetShop(friend)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // 展开态：整屏只有角色（投喂页），标题/切换/tab 栏全部隐藏
            val fullscreen = viewModel.petFullscreen
            if (!fullscreen) {
                Text(
                    "精灵中心",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
            }

            if (pair == null || friend == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有宠物", color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "到好友聊天页邀请 TA 一起领养",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                return@Column
            }

            if (fullscreen) {
                FeedTab(
                    modifier = Modifier.weight(1f),
                    pair = pair,
                    friend = friend,
                    trigger = feedTrigger,
                    msg = feedMsg,
                    musicPlaying = viewModel.isPlaying,
                    viewModel = viewModel,
                    fullscreen = true,
                    onPrevSpecies = {
                        val owned = pair.speciesOwned
                        val idx = owned.indexOf(pair.species)
                        val next = owned[(idx - 1 + owned.size) % owned.size]
                        viewModel.petSwitchSpecies(friend, next)
                    },
                    onNextSpecies = {
                        val owned = pair.speciesOwned
                        val idx = owned.indexOf(pair.species)
                        val next = owned[(idx + 1) % owned.size]
                        viewModel.petSwitchSpecies(friend, next)
                    },
                    onFeed = {
                        if (pair.notes < 5) {
                            feedMsg = "音符不足（需要 5），去任务页赚音符吧"
                        } else {
                            feedMsg = null
                            feedTrigger++
                            viewModel.petFeed(friend, 1) { result ->
                                feedMsg = result ?: "投喂成功，+10 经验"
                            }
                        }
                    }
                )
                return@Column
            }

            // 多宠物切换（与不同好友各有一只）
            if (pets.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    pets.keys.forEach { name ->
                        val active = name == friend
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                )
                                .clickable { selectedFriend = name }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(name, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            // tab 切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(4.dp)
            ) {
                listOf("任务", "投喂", "角色", "图鉴").forEachIndexed { i, label ->
                    val active = tab == i
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { tab = i }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            // 大精灵只在「投喂」页（与投喂合一页）；任务/角色/图鉴恢复整页高度
            when (tab) {
                0 -> TasksTab(
                    modifier = Modifier.weight(1f),
                    pair = pair,
                    onSignin = { viewModel.petSignin(friend) },
                    onClaim = { taskId -> viewModel.petClaim(friend, taskId) }
                )
                1 -> FeedTab(
                    modifier = Modifier.weight(1f),
                    pair = pair,
                    friend = friend,
                    trigger = feedTrigger,
                    msg = feedMsg,
                    musicPlaying = viewModel.isPlaying,
                    viewModel = viewModel,
                    fullscreen = false,
                    onPrevSpecies = {
                        val owned = pair.speciesOwned
                        val idx = owned.indexOf(pair.species)
                        val next = owned[(idx - 1 + owned.size) % owned.size]
                        viewModel.petSwitchSpecies(friend, next)
                    },
                    onNextSpecies = {
                        val owned = pair.speciesOwned
                        val idx = owned.indexOf(pair.species)
                        val next = owned[(idx + 1) % owned.size]
                        viewModel.petSwitchSpecies(friend, next)
                    },
                    onFeed = {
                        if (pair.notes < 5) {
                            feedMsg = "音符不足（需要 5），去任务页赚音符吧"
                        } else {
                            feedMsg = null
                            feedTrigger++
                            viewModel.petFeed(friend, 1) { result ->
                                feedMsg = result ?: "投喂成功，+10 经验"
                            }
                        }
                    }
                )
                2 -> SpeciesTab(
                    modifier = Modifier.weight(1f),
                    pair = pair,
                    shop = shop,
                    onSwitch = { viewModel.petSwitchSpecies(friend, it) },
                    onBuy = { species, name ->
                        viewModel.petBuySpecies(friend, species) { err ->
                            feedMsg = err
                            viewModel.notifyHint(err ?: "兑换成功：$name！已自动换上")
                        }
                    }
                )
                3 -> ActionShopTab(
                    modifier = Modifier.weight(1f),
                    pair = pair,
                    shelf = shelf,
                    actionCatalog = actionCatalog,
                    onBuy = { actionId, name ->
                        viewModel.petBuyAction(friend, actionId) { err ->
                            feedMsg = err
                            viewModel.notifyHint(err ?: "解锁动作：$name！")
                        }
                    }
                )
                else -> ActionShopTab(
                    modifier = Modifier.weight(1f),
                    pair = pair,
                    shelf = shelf,
                    actionCatalog = actionCatalog,
                    onBuy = { actionId, name ->
                        viewModel.petBuyAction(friend, actionId) { err ->
                            feedMsg = err
                            viewModel.notifyHint(err ?: "解锁动作：$name！")
                        }
                    }
                )
            }
        }
    }
}


/** 超大精灵舞台：占满分配空间的极限大小；点精灵互动（3s 冷却+气泡），云逗宠实时同步，两侧切角色 */
@Composable
private fun BigPetStage(
    modifier: Modifier = Modifier,
    pair: PetPairDto,
    friend: String,
    trigger: Long,
    musicPlaying: Boolean,
    viewModel: MainViewModel,
    onPrevSpecies: () -> Unit,
    onNextSpecies: () -> Unit,
    onInteract: () -> Unit
) {
    var bubble by remember { mutableStateOf<String?>(null) }
    var tapTrigger by remember { mutableLongStateOf(0L) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    var externalAction by remember { mutableStateOf<String?>(null) }
    var externalSeq by remember { mutableLongStateOf(0L) }
    val unlockedActions = pair.actionUnlocks[pair.species].orEmpty().toSet()
    // 气泡 2.2s 自动消（同聊天页）
    LaunchedEffect(bubble) {
        if (bubble != null) {
            kotlinx.coroutines.delay(2_200)
            bubble = null
        }
    }
    // 对方云逗宠：动作实时同步到大精灵（eat=投喂已由 feedTrigger 触发，跳过）
    LaunchedEffect(friend) {
        viewModel.petActions.collect { event ->
            if (event.from == friend && event.action != "eat") {
                externalAction = event.action
                externalSeq++
            }
        }
    }
    // 投喂：播 bounce 作为反馈（所有角色 starter 都有 bounce）
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            externalAction = "bounce"
            externalSeq++
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 极限尺寸：PetAvatar 高=size、宽=size*3/4 → size 受舞台高度与宽度双重约束
        val size = minOf(maxHeight, maxWidth * 4 / 3)
        // 整页可点（精灵已经顶到极限尺寸，点舞台任意处=互动）；切角色箭头是子级 clickable，优先响应
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = com.example.helloworld.ui.components.rememberNoRippleSource(),
                    indication = null
                ) {
                    // 3 秒冷却：连点不生效（同聊天页节奏）
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastTapMs >= 3_000) {
                        lastTapMs = nowMs
                        tapTrigger++
                        bubble = com.example.helloworld.ui.components.PET_BUBBLES.random()
                        onInteract()
                    }
                }
        ) {
            if (pair.speciesOwned.size > 1) {
                IconButton(onClick = onPrevSpecies, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一个角色", tint = Color.White)
                }
                IconButton(onClick = onNextSpecies, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一个角色", tint = Color.White)
                }
            }
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                // 头顶徽标：等级 + 火花
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Lv${pair.level}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (pair.flameDays > 0) {
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF97316).copy(alpha = 0.85f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("火花 x${pair.flameDays}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                // 精灵 + 气泡：外层 Box 固定为精灵尺寸，气泡只溢出绘制不参与测量（防布局位移）
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(size * 3 / 4)
                        .height(size),
                    contentAlignment = Alignment.TopCenter
                ) {
                    PetAvatar(
                        species = pair.species,
                        trigger = tapTrigger,
                        size = size,
                        musicPlaying = musicPlaying,
                        animate = true,
                        unlockedActions = unlockedActions,
                        externalEvent = externalAction,
                        externalEventSeq = externalSeq,
                        applyDisplay = true
                    )
                    bubble?.let {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-42).dp)
                                .widthIn(max = 200.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.92f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                it,
                                color = Color(0xFF334155),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 13.sp,
                                style = androidx.compose.ui.text.TextStyle(
                                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                        androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                        androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 今日任务：签到卡 + 任务列表（进度/领取） */
@Composable
private fun TasksTab(modifier: Modifier = Modifier, pair: PetPairDto, onSignin: () -> Unit, onClaim: (String) -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 150.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("每日签到", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "音符 +${(10 * pair.flameMultiplier).toInt()}（火花 x${"%.1f".format(pair.flameMultiplier)}）",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }
                if (pair.signinToday) {
                    Text("已签到", color = Color(0xFF4ADE80), fontSize = 13.sp)
                } else {
                    TextButton(onClick = onSignin) {
                        Text("签到", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }
            }
        }
        items(pair.tasks, key = { it.id }) { task ->
            TaskRow(task = task, onClaim = { onClaim(task.id) })
        }
    }
}

@Composable
private fun TaskRow(task: PetTaskDto, onClaim: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${petTaskName(task.type)} ${task.progress}/${task.target}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                LinearProgressIndicator(
                    progress = { (task.progress.toFloat() / task.target).coerceIn(0f, 1f) },
                    modifier = Modifier.width(140.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (task.done) Color(0xFF4ADE80) else MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Text(
                    " +${task.notesReward} 音符",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        when {
            task.claimed -> Text("已领取", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            task.done -> TextButton(onClick = onClaim) {
                Text("领取", color = Color(0xFF4ADE80), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            else -> Text("进行中", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp)
        }
    }
}

/** 投喂页 = 超大精灵舞台 + 右下角小投喂按钮（左下名字/经验，底部中间提示语）；fullscreen=整屏只有角色 */
@Composable
private fun FeedTab(
    modifier: Modifier = Modifier,
    pair: PetPairDto,
    friend: String,
    trigger: Long,
    msg: String?,
    musicPlaying: Boolean,
    viewModel: MainViewModel,
    fullscreen: Boolean,
    onPrevSpecies: () -> Unit,
    onNextSpecies: () -> Unit,
    onFeed: () -> Unit
) {
    var renameOpen by remember { mutableStateOf(false) }
    var nameInput by remember(pair.name, pair.species) {
        mutableStateOf(pair.name.ifBlank { petSpeciesName(pair.species) })
    }
    val download by PetContentStore.progress.collectAsState()
    // 底部避开悬浮迷你播放器；展开态无播放器，只留导航条余量
    Box(modifier = modifier.fillMaxWidth().padding(bottom = if (fullscreen) 90.dp else 150.dp)) {
        BigPetStage(
            modifier = Modifier.fillMaxSize(),
            pair = pair,
            friend = friend,
            trigger = trigger,
            musicPlaying = musicPlaying,
            viewModel = viewModel,
            onPrevSpecies = onPrevSpecies,
            onNextSpecies = onNextSpecies,
            onInteract = {
                val pool = pair.actionUnlocks[pair.species].orEmpty()
                    .ifEmpty { pair.actionsUnlocked }
                    .ifEmpty { listOf("idle") }
                viewModel.petInteract(friend, pool.random())
            }
        )
        // 展开/收起：整屏只显示角色
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .clickable { viewModel.petFullscreen = !fullscreen }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                if (fullscreen) "收起" else "展开",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        // 左下：名字 + 经验条
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pair.name.ifBlank { petSpeciesName(pair.species) },
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    "改名",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { renameOpen = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            val progress = if (pair.expNeed > 0) pair.exp.toFloat() / pair.expNeed else 0f
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.width(110.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Text(
                    " ${pair.exp}/${pair.expNeed}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
        // 右下：小投喂按钮（上方标注音符余额）
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "音符 ${pair.notes}",
                color = if (pair.notes >= 5) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                fontSize = 11.sp
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        if (pair.notes >= 5) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                    .clickable(onClick = onFeed),
                contentAlignment = Alignment.Center
            ) {
                Text("投喂", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        // 底部中间：结果/不足提示
        msg?.let {
            Text(
                it,
                color = if (it.contains("升级") || it.contains("成功")) Color(0xFF4ADE80) else MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }
        download?.let { p ->
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "下载 ${p.character}/${p.act} ${(p.fraction * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
                LinearProgressIndicator(
                    progress = { p.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.padding(top = 3.dp).width(120.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }
        }
    }
    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("给宠物改名") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 20) nameInput = it },
                    singleLine = true,
                    label = { Text("1~20 字") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameOpen = false
                    viewModel.petRename(friend, nameInput.trim()) { err ->
                        viewModel.notifyHint(err ?: "名字已更新")
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("取消") }
            }
        )
    }
}
@Composable
private fun SpeciesTab(
    modifier: Modifier = Modifier,
    pair: PetPairDto,
    shop: List<PetShopItem>,
    onSwitch: (String) -> Unit,
    onBuy: (String, String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 150.dp)
    ) {
        item {
            Text(
                "我的角色（点按切换）",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }
        items(pair.speciesOwned, key = { "owned_$it" }) { species ->
            val current = species == pair.species
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    )
                    .clickable { onSwitch(species) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PetAvatar(species = species, trigger = 0L, size = 36.dp, animate = false)
                Text(
                    petSpeciesName(species),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp).weight(1f)
                )
                if (current) {
                    Icon(Icons.Default.Check, contentDescription = "使用中", tint = Color(0xFF4ADE80), modifier = Modifier.size(18.dp))
                }
            }
        }
        if (shop.isNotEmpty()) {
            item {
                Text(
                    "角色商店（音符兑换）",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
            }
            items(shop, key = { "shop_${it.species}" }) { item ->
                val owned = item.species in pair.speciesOwned
                val levelOk = pair.level >= item.levelRequired
                val notesOk = pair.notes >= item.price
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PetAvatar(species = item.species, trigger = 0L, size = 36.dp, animate = false)
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(item.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                owned -> "已拥有"
                                !levelOk -> "需要 Lv${item.levelRequired}"
                                else -> "${item.price} 音符"
                            },
                            color = when {
                                owned -> Color(0xFF4ADE80)
                                !levelOk -> Color.White.copy(alpha = 0.4f)
                                else -> Color.White.copy(alpha = 0.6f)
                            },
                            fontSize = 11.sp
                        )
                    }
                    if (!owned) {
                        TextButton(
                            enabled = levelOk && notesOk,
                            onClick = { onBuy(item.species, item.name) }
                        ) {
                            Text(
                                "兑换",
                                color = if (levelOk && notesOk) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 动作图鉴（合并原「动作」与「图鉴」）：网格卡片，未解锁也可见，点按购买 */
@Composable
private fun ActionShopTab(
    modifier: Modifier = Modifier,
    pair: PetPairDto,
    shelf: List<PetShelfItem>,
    actionCatalog: List<PetActionDef>,
    onBuy: (String, String) -> Unit,
) {
    val unlocked = pair.actionUnlocks[pair.species].orEmpty().toSet()
    val items = actionCatalog
    val ownedCount = items.count { it.unlocked || it.id in unlocked }
    // 已解锁动作的放大循环预览
    var preview by remember { mutableStateOf<PetActionDef?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 150.dp)
    ) {
        item {
            Text(
                "已解锁 $ownedCount/${items.size} 个动作 · 音符 ${pair.notes} · 未解锁点按兑换",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (items.isEmpty()) {
            item {
                Text(
                    "这个角色暂无动作目录，等服务器上新",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(items.chunked(3)) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { def ->
                        val owned = def.unlocked || def.id in unlocked
                        val lockedByLevel = def.lockedByLevel || pair.level < def.minLevel
                        val notesOk = pair.notes >= def.price
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(
                                        alpha = when {
                                            owned -> 0.7f
                                            lockedByLevel -> 0.3f
                                            else -> 0.55f
                                        }
                                    )
                                )
                                .clickable(
                                    enabled = owned || (!lockedByLevel && notesOk)
                                ) {
                                    if (owned) preview = def else onBuy(def.id, def.name)
                                }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PetAvatar(species = pair.species, trigger = 0L, size = 40.dp, animate = false)
                            Text(
                                def.name.ifBlank { def.id },
                                color = if (owned) Color.White
                                else if (lockedByLevel) Color.White.copy(alpha = 0.4f)
                                else Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(
                                when {
                                    owned -> "已解锁"
                                    lockedByLevel -> "需 Lv${def.minLevel}"
                                    else -> "${def.price} 音符"
                                },
                                color = when {
                                    owned -> Color(0xFF4ADE80)
                                    lockedByLevel -> Color.White.copy(alpha = 0.3f)
                                    notesOk -> MaterialTheme.colorScheme.primary
                                    else -> Color.White.copy(alpha = 0.3f)
                                },
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }

    // 已解锁动作放大预览：循环播放，点任意处关闭
    preview?.let { def ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { preview = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .clickable { preview = null },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PetAvatar(
                        species = pair.species,
                        trigger = 0L,
                        size = 300.dp,
                        animate = true,
                        unlockedActions = unlocked,
                        loopClip = def.clip.ifBlank { def.id },
                        applyDisplay = true
                    )
                    Text(
                        def.name.ifBlank { def.id },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "点击任意处关闭",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

