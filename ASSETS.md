# 宠物素材索引（ASSETS）

> 这个仓库本身不含宠物素材。素材都在 `~/workspace/cw/` 下，本文件是给 AI / 开发者的**导航 + 关键提示**。
> 素材格式契约见服务端仓库：`/home/bo2/workspace/music/knowledge/2026-11_pet_content_format.md`
> 线上生效目录：`~/.config/music/pet_content/`（服务端 `GET /pet/content/manifest`、`/pet/content/file/{character}/{act}` 从这里读）

## 1. 去哪儿找

| 用途 | 绝对路径 |
|---|---|
| **素材捕获总库**（多个桌宠项目汇总 + 万能画廊） | `/home/bo2/workspace/cw/harvest/`（`README.md` / `index.html` / `assets.json`） |
| **原始可操作素材**（精灵表 / 透明视频 / 图标 / 音效） | `/home/bo2/workspace/cw/pet-assets/`（先读它的 `README.md`） |
| **成品**（`manifest.json` + `act*.zip`，可直接上线） | `/home/bo2/workspace/cw/pet_content/`（`README.md`、`CLIPS.md`） |
| **生成脚本** | `/home/bo2/workspace/cw/tools/build_pet_content.py`、`build_whale_girl_motion.py` |
| **局域网预览（浏览器里点着看）** | `/home/bo2/workspace/cw/pet_preview/`，`bash serve.sh` → `http://<本机IP>:8899/` |
| 上游项目 A（鲸鱼娘，含源码/决策记录） | `/home/bo2/workspace/cw/whale-girl/` |
| 上游项目 B（大肥鱼，含 109 段动画与制作链） | `/home/bo2/workspace/cw/dsh-pet/` |
| 上游项目 C（桌面移植版，106 段动画 + 工程文档） | `/home/bo2/workspace/cw/dsh-pet-indesktop/` |

### 在线看素材

```bash
python3 -m http.server 8890 --bind 0.0.0.0 --directory ~/workspace/cw
# 素材总览（全部项目，可按项目/分类/类型筛，视频自动播放）
#   http://<本机IP>:8890/harvest/
# 宠物成品预览（whale_girl / dsh_pet 的实际渲染）
#   http://<本机IP>:8890/pet_preview/
```

## 2. 关键宝贝（容易被忽略但很有用）

| 位置 | 是什么 | 什么时候用 |
|---|---|---|
| `cw/dsh-pet/prompts/` | **AI 生成这些动画的提示词配方**（绿幕规范 + 按秒分解动作） | 要新做一段动画时，照抄改角色/动作 |
| `cw/dsh-pet/MAKE-YOUR-OWN-PET.md` | 从零造宠物的完整流程（提示词 → 绿幕视频 → 透明动画 → 插件） | 想自绘/自制素材，不再依赖开源形象（也顺带绕开授权） |
| `cw/dsh-pet/dsh-pet/assets/config.jsonc` | 动画语义与权重的**权威配置**：待机/点击池/随机动作分类权重/事件档位（工作状态 6 档、余额 6 档）/睡觉三段 | 想给某个行为位换动画、或做"随机动作池" |
| `cw/dsh-pet/dsh-pet/assets/preview/*.gif` | 109 段动画的预览 GIF（67 MB） | 光看文件名不知道动画在干嘛时，最快的人眼参照 |
| `cw/dsh-pet/video/watermark_mask_v5.mkv` | 素材制作链的水印遮罩 | 复现"源视频 → 透明动画"流水线时 |
| `cw/whale-girl/decisions/` | 架构与 motion 配方决策记录 | 想改游走/朝向/动效逻辑前先读 |
| `cw/whale-girl/docs/preview/*.gif` | 15 个状态的预览 GIF | 看鲸鱼娘各状态长什么样 |
| `cw/pet-assets/inventory.csv` | 161 个素材文件的尺寸/时长/帧数清单 | 写工具或做校验时当输入 |
| `cw/pet-assets/dsh-pet/states.json` | 我整理的语义映射（中文动画名 → 行为位） | 要把新动画挂到 `stand/music/...` 上 |
| `cw/dsh-pet-indesktop/assets/characters/shenshen/videos/` | 另一套 106 段动画，按 click/drag/events/idle/move/random/turn 分类，带 `manifest.json` 的 `body_box`/`head_box` | 想换素材来源、或需要"按类别命名"的参考 |
| `cw/dsh-pet-indesktop/docs/INDEX.md` | 那个项目的工程文档入口（素材管线、帧序列编码、打包） | 想复刻"源视频→透明动画→帧序列"的流水线 |
| `cw/tools/build_harvest_index.py` | 生成 `harvest/assets.json` 的扫描器 | 新抓一个项目后重新生成索引 |

## 3. 必须知道的约束（踩了就白干）

1. **画布必须 3:4 竖版** —— `PetAvatar` 是 `Canvas(height = size, width = size * 3 / 4)`，现在统一 **360×480**，不是 3:4 会被拉伸。
2. **帧命名** `frames/frame_%04d.webp`（从 0000 连续，透明 WebP）。`%04d` 是最小宽度，当前 dsh_pet 有 13019 帧 → 会出现 5 位 `frame_13018.webp`，`String.format` 兼容。
3. **webm 解 alpha 必须显式 `-c:v libvpx-vp9`**，否则 ffmpeg 解成 `rgb24`，alpha 静默丢失。
4. **clip 名 = 引擎行为名**：`PetEngine` 只认 `stand / music / stretch / hem / bounce / heart / neck / hop / windmill / windbounce / thumbs / neckwear / intopocket`；行为池按组生效（`heart+neck+hop`、`windmill+windbounce`、`stretch+hem` 缺一整组不触发）。manifest 里其它名字只做展示/备用，引擎忽略。
5. **待机游走是按帧号推进的**：`PetEngine.wander` 以 16~26 帧/秒 走帧索引，与角色 `fps` 无关。所以低 fps 素材（如 dsh_pet 设成 12fps）待机会被推快约 3 倍；24fps 素材基本 1:1。
6. **`manifest.version` 一变，客户端清缓存重下**（含内置 boy ~11 MB），改素材必发新版本号。

## 4. 授权红线

- **dsh-pet（大肥鱼）素材：禁止商用**（形象 CC BY-NC-SA 4.0）。个人/非商业可用，须标注 `https://github.com/PC2005-cloud/dsh-pet`。
- **whale-girl（鲸鱼娘）**：代码 MIT，形象 credit ZipZipPipe，商用前需向作者确认形象授权。
- 「上首软糖体」为第三方**商业字体**，不要打包进客户端。
- 详见 `cw/pet-assets/ATTRIBUTION.md`。

## 5. 常见动作

```bash
# 预览（局域网）
cd ~/workspace/cw/pet_preview && bash serve.sh          # http://<本机IP>:8899/

# 重新生成成品（全量 109 段 + 15 状态）
cd ~/workspace/cw && python3 tools/build_pet_content.py --out pet_content
python3 tools/build_whale_girl_motion.py --out pet_content   # whale-girl 单独（带烘焙动效）

# 上线（服务端配置目录，免重启、免发版）
cp -r ~/workspace/cw/pet_content/* ~/.config/music/pet_content/
curl -s localhost:8001/pet/content/manifest | head -c 200
```
