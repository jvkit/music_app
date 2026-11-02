# 音河音乐 Android 客户端 · Agent 工作规范

「音河音乐」（包名 com.example.helloworld，品牌名音河音乐）——音河音乐服务的官方 Android 客户端。
Compose 单 Activity 架构，音乐服务器是隔壁 `music_web`（接口前缀 /api/）。

## 构建与部署

```bash
./gradlew.bat assembleDebug            # 构建（daemon 模式，--no-daemon 在这台 16G 机器上必 OOM）
```

- **服务器地址在 `gradle.properties` 的 `serverApiBaseUrl/serverOrigin`**，构建时打进 BuildConfig。
  调试本机服务 = `http://192.168.2.16:8001/api/`，线上 = `https://jvkit.cn/music/api/`。
  注意：SettingsRepository 的 SharedPreferences 优先于 BuildConfig（用户改过设置就一直记着）。
- 发布：apk 传服务器 `/home/ubuntu/www/download/music/app/yinhe-music.apk`，
  直链 https://jvkit.cn/download/music/app/yinhe-music.apk；更新文案写线上
  `~/.config/music/announcement.json`（免重启）。

## USB 真机调试环境（iqoo8 / V2136A，Windows + Git Bash）

- **spacedesk 是 USB 调试的头号杀手**（把手机当副屏占用 USB 通道）——已在电脑端卸载。
  若再次出现设备反复 offline/消失，先查有没有装回分屏类软件。
- **输中文不用切输入法（2026-10-31 起，弃用 ADBKeyboard 切换方案）**：
  保持百度输入法默认（`com.baidu.input_vivo/.ImeVivoService`）。`input text` 发不了非 ASCII
  （命令自身 NPE，与输入法无关），但**拼音字母会进百度组词**（等同实体键盘）：
  `adb shell input text "shenqiu"` → 组词出候选 → `input keyevent 62`（空格）提交首选，
  或 keyevent 8~16（数字键 1~9）选第 N 个候选（如 shenqiu+2 → 沈秋）→ `keyevent 66` 回车。
  取消组词=111（ESC），退格=67，清输入框先 111 再 67×N。账号密码等 ASCII 直接 `input text`。
  候选条不在 uiautomator dump 里（IME 悬浮窗），选字靠"已知首选"或截屏后 tap 坐标。
- **点不准一律用 uiautomator dump 拿坐标**：`adb shell uiautomator dump /sdcard/ui.xml` +
  `adb pull` 后解析 bounds，禁止拍脑袋坐标（这台设备 1080x2376，导航栏/按钮中心都要算准）。
- **截屏**：`adb exec-out screencap -p > /tmp/x.png`（Git Bash 下 `/sdcard` 会被路径转换坑）。
- **vivo 会过滤 Log.d**：调试日志用 Log.w 以上，或 `adb shell setprop log.tag.<TAG> VERBOSE`。
- 手机长时间无操作会熄屏导致截屏全黑：先 `input keyevent 224`（亮屏）+ 上滑解锁。

## 核心架构约定（改代码前必读）

- **单 ViewModel（MainViewModel，AndroidViewModel）+ Compose state**，导航是 viewModel 里的
  状态位：`selectedTab / selectedPlaylistId / showNowPlaying / imScreen("friends"|"chat:x")`。
  YinHeApp 顶层 when 分发，返回逻辑 = 各页 BackHandler 逐层返回 + 根双击退出（非首页 tab 先回首页）。
- **★ Compose state 只能在主线程写**：`Dispatchers.IO` 里直接改 `mutableStateOf` 在这台 vivo 上
  **不触发重组**（表现为永远卡加载圈）。所有跨线程状态赋值必须
  `withContext(Dispatchers.Main) { ... }`（authState/libraryState/searchState/lyrics 等全是教训）。
- **Room 迁移校验极严**：Migration 建的表结构（列/主键/索引）必须与 Entity 完全一致，
  **多建一个索引都会启动崩溃**（IllegalStateException: Migration didn't properly handle）。
  只建 Entity 声明过的东西。
- **Modifier.padding 不允许负值**（要"压边"效果用 `offset(x = (-8).dp)`），直接崩溃。
- 媒体播放：YinHeMediaService（前台服务，Media3 ExoPlayer + SimpleCache 边听边存 512MB，
  缓存 key = trackId）。换源链 resolveStreamUrl、缓冲看门狗 watchStall 12s。
  下一首预加载 = `setPreloadConfiguration(30s)` + onMediaItemTransition 里预解析下一首 URL 存 TrackPayloadStore。
- **慢源首开（youtube/soundcloud）**：playTrack 拦截 → 不阻塞调 preview，而是 POST /api/download
  拿 task_id 轮询进度（800ms），弹**可关闭**的准备进度弹窗（关闭=仅隐藏弹窗，下载继续、完成自动播；
  中途点别的歌会 cancel 等待）。下载完成后 preview 本地秒回再 playTrackInternal。服务端进度来自
  yt-dlp progress hook → DownloadContext.report → /api/download/progress。
- 启动公告：每个版本只弹一次，seen_version 存 SharedPreferences（yinhe_announcement）——
  内存标志冷启动即失效，必须持久化。
- 用户中心：改名=PATCH /auth/me（服务端联动 users.json 条目/数据文件/好友列表/收件箱，token 不变）；
  改密=POST /auth/password（签发新 token，客户端要 AuthRepository.save 替换）；头像=POST /auth/avatar
  （base64 PNG/JPEG ≤5MB，服务端按字节头嗅探扩展名）或 POST /auth/avatar/preset（12 个预设一键选用，
  服务端复制文件）。公开读取 GET /auth/avatar/{username} 按用户名直查（无需登录），好友列表/聊天
  头像 URL 由客户端按用户名构造（viewModel.avatarUrl），404 时 UserAvatar 显示首字母。注册时服务端
  随机分配一个预设头像（users_api._assign_random_preset_avatar）。
- **一起听（领导选举单向跟随，2026-11-01 改版）**：双方 accept 后都广播都轮询，但**任意时刻只有
  一方跟随**：rev 高者为领导（平局=邀请方领导），领导永不向对方纠漂/应用，跟随方单向对齐
  （rev 变化全量应用；同版本只做 >6s 的宽松纠漂、冷却 8s、补偿固定 +2.5s 上报间隔中值）。
  客户端 ts 是服务器时间，手机时钟与之有偏差，**禁止用本地 now-ts 当流逝时间**（旧实现双方互纠
  疯狂回溯的根因）。状态带 rev（播放操作递增）和 partner（非自己→"来晚"失效），
  applyingListenState=true 屏蔽副作用上报。邀请 1 分钟时效，IM 信令 listen_accept/listen_leave
  即时反馈（listenFeedback 横幅），解散=空状态广播+leave 两边同步退出。
- 键盘悬浮/间距 bug：AndroidManifest 的 activity 必须 android:windowSoftInputMode="adjustResize"，
  默认 adjustPan 会系统平移 + Compose imePadding 双重 padding，输入栏浮在键盘上方一倍距离。
- FastAPI 裸 dict 参数默认当 query——收任意 JSON body 必须 `payload: dict = Body(...)`。
- 首页数据链：/api/server_library（所有用户收藏+歌单去重汇总，带 owner_count/play_count/today_plays；
  旧 /api/library 仅作全量同步）。today_plays 来自服务端 daily_plays.json 按天累计保留 7 天，POST /plays
  时 bump；客户端排序=有封面(coverUrl)优先>今日次数>shuffle 稳定序，显示 50 + 查看更多；
  大卡 heroTrack=今日有封面track按次数取首个，歌词 api.lyrics 拉前 4 句做轮换动效。
- 反馈/日志：POST /api/feedback（feedback.jsonl）与 POST /api/logs（logs_upload/<user>_<ts>.log）
  均需登录；设置页「上报日志」把 YinheLogger 最近 300 行+音源统计上传。「检查更新」=比
  announcement.apk_version_code 与 BuildConfig.VERSION_CODE。服务器地址已从 UI 移除
  （SettingsRepository 保留），launcher 图标=mipmap 各密度 PNG（ffmpeg 多尺寸生成）。
- 用户系统：token 存 SharedPreferences（yinhe_auth），登录后 YinheImClient.connect(token)。
  **重启 App 自动登录靠 token 校验，改服务端密码/重新 curl 登录会顶掉旧 token**（表现=回登录页，重登即可）。
- IM：WebSocket（OkHttp）/api/ws/im，好友=users.json 双向直加，离线消息存服务端信箱（拉取即清），
  一起听=房主 POST /im/listen/state 覆盖式 + 好友 3 秒轮询跟随（不严谨、够用）。
- 黑胶旋转正确姿势：`Animatable` + LaunchedEffect(isPlaying) 里 while 循环
  `animateTo(value+360f, tween(12s, LinearEasing))`；**不要**用大 target + infiniteRepeatable
  （12 秒转 36 万度闪瞎眼）；协程取消即停，恢复从当前角度续走。
- 歌词：服务端 LRC 带行级时间戳（无逐字标签），当前句按「行时长线性均分」逐字渐变+当前字放大，
  点句 seek、用户滚动时跟随暂停 4s。
- **宠物系统（2026-11，P1 已落地）**：好友对共养一只（pair_key=sorted(a|b)，config/pets.json，
  服务端 pet_api.py）。经济=音符：每日任务（0 点刷新 3 随机+签到，2 简单 20 + 1 中等 40）与签到
  得音符，投喂 5 音符=10 经验（经验唯一来源），升级自动解锁动作，角色商店音符兑换+等级门槛
  （miao Lv3/200、wang Lv8/500、tiao Lv15/1000）。续火花：当天签到+完成≥2 任务记活跃日，
  >48h 断签清零，倍率 1.0→2.0。WS 信令 pet_invite/pet_accept（accept 由服务端建 pair）/pet_action
  （REST interact/feed 成功后经 im_api._send_to 实时推对方）。行为埋点统一 POST /pet/event
  （客户端在 search/maybeReportPlay/toggleFavorite 添加/sendChat/shareTrackTo/一起听退出处调用
  reportPetEvent，累加到该用户所有 pair）。聊天页灰底渐变 #8A8F98→#6E747E（融合透明素材灰边）。
  设计文档 docs/pet-design.md，冒烟 tools/pet_smoke_test.py（PET_PORT 起服务后跑）。
- **宠物素材服务器化（2026-11，manifest v3，全热更新）**：角色/动画/动作价格都不依赖发版。
  `GET /pet/content/manifest` 下发 characters[id]={name,fps,roles,starter,behaviors,clips,packs,display}、
  actions 目录、shop、shop_rotation_version；`GET /pet/content/file/{character}/{act}` 返回分片 zip。
  动作商店：`GET /pet/shop?friend=`（返回 shelf 折扣货架 + **catalog 全量动作**，含 price/on_shelf/
  unlocked/locked_by_level）、`POST /pet/buy_action`（货架内走折扣价、货架外走标准价
  `10 + (min_level-1)*5`）、`PATCH /pet/name`。
  PetAnimationEngine 由 roles/behaviors/unlocked 驱动 idle/music/click/随机池/睡觉/事件；
  帧走 PetFrameCache LRU。新增角色/动作 = 服务端放 zip + 改 manifest/actions。
- **★ 宠物动作必须"买了才能播/才能下载"（2026-11-02 修复）**：
  `PetAnimationEngine.allowed()` 曾有一个漏洞——只要 clip 出现在 `roles` 任意槽位（idle/music/move/
  turn/drag/events...）就放行，导致**商店在售的动作仅因被 `roles` 引用即可被随机池抽中、并触发整包下载**。
  现在引擎构造时传入 `sellable`（manifest.actions 里该角色的在售 clip 集合），未购买一律拒绝：
  starter / unlocked 才免费，其余槽位里的 clip 若在售则视为需要购买。
  同时**删除了解锁动作的批量预下载**（原 `LaunchedEffect(unlockedActions)` 会为每个买过的动作下载
  其所在 pack，实测 10 个解锁动作触发 85MB：core 43MB + small 35MB + text 6.6MB）。
  现在的下载时机 = `PetAvatar` 帧循环里"当前播放的 act 未就绪才后台拉"，不阻塞渲染。
- **★ PetContentStore.refresh() 的坑（2026-11-02，闪切/重复下载根因）**：`characters` 是 Compose state，
  `PetAvatar` 的 `characterId` 由它派生，`remember(characterId, character, ...)` 建引擎。
  早期实现每次 refresh 都无条件 `characters = map`（还比较对象是否相等，而 boy 会被替换成内置 BOY
  导致永远"不相等"），于是刷新→重组→引擎重建→LaunchedEffect 再 refresh 形成死循环，
  表现为**角色在 boy 和当前角色之间疯狂交替 + 反复重新下载素材**。修复：
  ① `refreshMutex` + `refreshing` 标志串行化；② **只在 manifest.version 变化时替换** `characters`；
  ③ 版本没变时不清 readyActs / PetFrameCache；④ 首次加载从 SharedPreferences 恢复已解压记录而不是清空。
- **display 显示修正（2026-11-02）**：各角色素材在 360x480 画布内的留白基准不同（鲸鱼娘上方空 20%、
  小河灵左右各空 27%、大肥鱼几乎占满），UI 取一个值必然顾此失彼。改为 manifest 每角色下发
  `display={offsetX, offsetY, scale}`（offset 是画布宽高的百分比，正=右/下），`PetAvatar` 绘制时应用。
  **只给投喂页大角色开 `applyDisplay=true`**：商店/图鉴缩略图、聊天页小宠物用原比例铺满，
  否则套用 0.5 缩放会变得极小。当前值：鲸鱼娘 {0.07, -0.15, 0.5}、大肥鱼 {0, -0.15, 0.402}
  （0.402 是按内容占画布比例折算，与大肥鱼同样观感）、小河灵 {0,0,1.0}。
  注意：改素材帧做"归一化"的方案已验证不可行（会裁边/位移丢失），已被 display 方案取代。
- **宠物页面结构（2026-11-02 合并）**：tab 从 5 个减为 4 个 = 任务 / 投喂 / 角色 / 图鉴。
  原「动作」与「图鉴」已合并为「图鉴」：3 列网格卡片，列出**全部**动作（未解锁也可见），
  已解锁点击进入全屏循环预览（`PetAnimationEngine.playLoop()`），未解锁点击即购买，等级不够置灰。
  `AlbumTab` 已删除。

- **试听剪辑检测（2026-11-01）**：部分音源返回 15s/30s/50s 试听片段。YinHeMediaService
  checkTruncatedStream：STATE_READY 且元数据时长≥90s、实际<60% 且<75s → blockUrl+标记源已试，
  重新走换源链找完整版；换源无更优时用回此前地址（不跳歌，防元数据时长不准误伤）。
  TrackPayloadStore.blockUrl/isBlocked 按曲目屏蔽流地址。

## 数据流速查

- 歌单/收藏同步：客户端权威，登录 pullSync 全量覆盖本地，任何变更 schedulePush 防抖 600ms 推全量。
- 服务器"曲库"= /api/server_library（所有用户收藏+歌单去重，不存音频）；播放计数 POST /api/plays（进度≥80% 每首一次）。
- 缓存状态机：trackPlayStarted 标 DOWNLOADING，done = 缓存字节≥总字节（听完才 done；
  听一半切歌 = "已缓存 xx%" 部分缓存，UI 用 cacheProgress 显示百分比）。

## 当前未完事项（2026-10-31）

- **外网源播放（2026-10-31 深夜已修）**：YouTube/SoundCloud 可搜索可播放。
  根因链：googlevideo 对机房 IP 只放行首 1MB（default 客户端直链全 403）→
  `player_client=["android"]`（带 default 会污染选择）+ `get_stream_url` 恒 None 走
  服务端完整下载（yt-dlp 下载器可绕开风控，原因不明别复刻）；SoundCloud 已 HLS 化，
  旧自研下载把 m3u8 存成 mp3，下载也改 yt-dlp。首开缓冲 5-45s 属预期。
  详见 music_web knowledge/2026-10_server_ops.md「外网源播放修复实录」。
- **搜索（2026-10-31 已修）**：SearchField 补 `ImeAction.Search`（回车立即搜，此前回车无响应）；
  ViewModel 加 searchGeneration 代次守卫（旧搜索迟到结果不再覆盖新结果）。
- ~~服务器 mihomo 代理 curl 出口 000（DNS 待修）~~ **已解决（2026-10-31）**：
  没改配置，url-test 换点后出口即通（google/youtube/soundcloud 全 200）；
  `节点选择` 组已指 `自动节点` 自动换点 + 每 5 分钟看门狗（重新测速→重启服务）。
  线上 music 服务 `~/.config/music/config.json` 已指 `http://127.0.0.1:7890` 并重启，
  `/api/search?source=youtube|soundcloud` 线上返回真实结果 200。
  详见 music_web knowledge/2026-10_server_ops.md。
- 调试机当前装的是线上版全功能包；本机调试服务（192.168.2.16:8001）与线上（jvkit.cn）代码已同步。
