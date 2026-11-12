# 网易云音乐客户端（Compose Multiplatform / Android）

由上一版 App Store 界面复刻工程**原地重构**而来：保留液态玻璃底栏与设计系统，
把 5 个 Tab、二级详情页、播放栏、账户设置全部改成音乐软件，并真实对接网易云音乐。

> 1.1.0 起默认 **直连网易云官方接口**：Kotlin 内实现 weapi / eapi 加密，
> 手机单独安装即可扫码登录、取歌单、播放，不再需要电脑上的 Node 代理或 `adb reverse`。
> 代理模式仍保留在设置里，供自建服务器场景使用。

## 架构

```
appstore-compose/
├─ server/                       # 可选：Node 版 NeteaseCloudMusicApi 代理（3000 端口）
│  ├─ index.js / package.json
│  ├─ plugins/sample-source.js   # 协议 v1 示例音源
│  └─ README.md                  # 接口实测结果与后端说明
├─ app/src/commonMain/.../appstore/
│  ├─ music/                     # 本轮新增：音乐客户端全部 UI + 数据层
│  │  ├─ Models.kt               # Song / Playlist / LyricLine / PlayStats / ScriptInfo ...
│  │  ├─ MusicBridge.kt          # 可插拔接口 + 全局 MusicState
│  │  ├─ Fixtures.kt             # 离线回退数据（无网络/未登录也能完整浏览）
│  │  ├─ MusicArt.kt             # 原创封面 / Tab 图标 / 歌词卡背景 / 柱状图
│  │  ├─ MusicComponents.kt      # 播放键 / 歌曲行 / 歌单卡 / 专辑卡 / 统计卡
│  │  ├─ MusicPages.kt           # 今天 / 新歌上架 / 我的 / 听歌统计 / 搜索
│  │  ├─ SongDetailPage.kt       # 歌曲详情（统计条、歌词卡、评论、属性）
│  │  ├─ LyricCards.kt           # 逐句原创卡片 + 无限 LazyRow + 自动跟随播放
│  │  ├─ PlayCapsule.kt          # 底栏右上方播放胶囊（圆形封面 + 环形进度）
│  │  ├─ AccountExtras.kt        # 扫码登录 / JS 脚本导入 / 首页卡片 / 缓存管理
│  │  └─ MusicApp.kt             # 根容器 + 歌单页
│  ├─ AppStoreTheme.kt           # 沿用设计 token
│  ├─ Components.kt / Art.kt     # 复用通用组件与头像绘制
│  └─ AccountSheet.kt            # 设置页（账号 / 存储 / 版本）
└─ androidApp/src/main/...        # Android 实现
   ├─ MusicApi.kt                # UI 需要的全部网易云能力接口（双子实现）
   ├─ NeteaseDirect.kt           # 默认：直连 music.163.com，无需任何服务端
   ├─ NeteaseCrypto.kt           # weapi（AES-CBC×2 + RSA）/ eapi（AES-ECB + MD5）加密
   ├─ NeteaseParse.kt            # 歌曲 / LRC / Cookie 解析
   ├─ QrBitmap.kt                # ZXing 本地生成登录二维码
   ├─ NeteaseApi.kt              # 可选：走自建代理的客户端，全部接口带失败回退
   ├─ MusicCache.kt              # 磁盘缓存：接口数据 / 封面 / 播放记录 / 本机统计
   ├─ MusicPlayer.kt             # Media3/ExoPlayer + 200ms 进度 ticker
   ├─ ScriptEngine.kt            # Rhino 执行导入的 JS 音源插件
   ├─ AppConfig.kt               # SharedPreferences：地址/Cookie/脚本/首页卡片
   ├─ MusicBridgeImpl.kt         # 组合实现并注入 commonMain
   └─ MainActivity.kt            # 注入 + 文件选择器 + 生命周期
```

`commonMain` 不含任何 Android API：所有网络、播放、脚本、存储能力通过
`MusicBridge` 接口注入，接口默认实现返回本地数据，因此离线也能完整浏览。

## 快速开始

```bash
# 构建 + 安装（无需启动任何服务）
bash scripts/build-android.sh                  # 或 scripts\build-android.bat
```

装好后直接打开 App →「我的 → 头像 → 链接网易云账号」扫码即可，手机联网就行。
连接方式开关在同一个弹层里：**直连（推荐）** 为默认，**自建代理** 才需要填
`http://<host>:3000`（此时先执行 `adb reverse tcp:3000 tcp:3000` 或用局域网 IP）。
Manifest 已开启 `INTERNET` 与 `usesCleartextTraffic`。

### Release 构建（脱离电脑测试）

```bash
./gradlew :androidApp:assembleRelease   # 或 gradle -p . :androidApp:assembleRelease
```

- 产物：`androidApp/build/outputs/apk/release/androidApp-release.apk`
- 签名：`keys/appstore-release.jks`（alias `appstore`，密码 `appstore123`，仅供本地测试）
- release 关闭了 R8 压缩：Rhino 通过反射执行插件脚本，混淆有裁剪风险；后续如需开启需补 keep 规则
- **可完全脱离电脑**：默认直连模式下 App 自己加密请求 `music.163.com`，
  另一台真机装好、联网、扫码即可，不需要电脑、不需要代理服务、不需要 `adb reverse`。
- 登录 Cookie 不随 APK 打包，新设备需重新扫码（扫码本身也走直连）。

环境：JDK 21 / Gradle 9.8.1 / AGP 9.3.2 / Kotlin 2.4.10 / Compose 1.12.0 /
compileSdk 37（依赖 AAR metadata 强制要求，降到 36 会校验失败）。

## 功能与需求对应

| 需求 | 实现 |
| --- | --- |
| Today 首卡展示「每日推荐」第一首歌封面，点进去是相应歌单 | `TodayPage` 首卡用 `playlist.songs.first()` 的封面；登录后由 `/recommend/songs` 覆盖；点击进歌单页 |
| 卡片数量不固定、可自定义展示哪些歌单 | 数据驱动列表；「自定义卡片」弹层可勾选每日推荐 / 私人 FM / 专属歌单 / 我喜欢的音乐 / 精选 / 新歌速递 |
| Games → 新歌上架 | `/personalized/newsong` 按真实专辑分组；保留旧 Games 页布局：分类 chips + 横向滑动 NEW RELEASE/NEW SINGLE 大卡（底部信息条+播放键）+「最新单曲」列表；点开大卡走 `/album` 取专辑曲目 |
| Apps → 个人页 | 用户资料卡 + 我的歌单（登录后 `/user/playlist`）+ 最近播放 |
| Arcade → 听歌统计，iOS 风格重做 | 优先用账号 `/record?type=1` 周榜；拿不到时只统计本机播放记录（次数 / 今日与本周时长 / 连续天数 / 时段 / 排行） |
| Search → 搜歌曲/艺术家/专辑 | `/search?type=1`，结果分三组，热门搜索 chips |
| 二级页 → 歌曲详情 | 统计条（风格/收藏数/专辑第几首/评论数/时长/码率）、评论区、属性模块；**已去掉 What's New** |
| 截图轮播 → 无限滚动歌词展示图 | `LyricCards`：一句一张原创渐变卡，LazyRow 循环 200 轮，播放进度到下一句自动翻页，无歌词时占位 |
| Get 按钮 → 播放暂停键 | 详情页、列表行、卡片、mini player 全部替换为 `PlayPauseButton` |
| 底栏右上方播放胶囊 | `PlayCapsule`：圆形封面（外圈环形进度）、上一首 / 播放暂停 / 下一首；点封面进歌曲详情 |
| 设置页 | 已移除 App Store 遗留项（Purchased / Subscriptions / 礼卡 / Personalized Recommendations），改为账号、JS 音源、缓存管理、版本 |

## 扫码登录

「头像 → 链接网易云账号」→ 直连模式下调 eapi `/api/login/qrcode/unikey` 取 key，
二维码由 **ZXing 在本机生成**（内容 `https://music.163.com/login?codekey=<key>`）→
每 2 秒轮询 `/api/login/qrcode/client/login`（801 等待扫码 / 802 待确认 /
803 成功并下发 Cookie / 800 过期）→ Cookie 清洗后存入 SharedPreferences，
后续所有请求自动带上。（代理模式下则改用 `/login/qr/key|create|check`，二维码由服务端出图。）

**每日推荐、私人 FM、我喜欢的音乐、听歌排行、歌曲播放地址都需要登录 Cookie。**
未登录时这些模块自动回退到本地样例数据，播放会提示登录或导入音源脚本。

## JS 音源插件（协议 v1）

```js
function sourceInfo() { return { name, version, description }; }
function resolve(songJson) { return "可播放的音频 URL"; }  // 返回 "" 回退网易云音源
```

- 导入方式：设置页「从文件导入 .js」/「载入内置示例」/ 粘贴脚本内容。
- Rhino 在应用进程内执行（`optimizationLevel = -1`，Android 必需）。
- 播放链路：启用插件 `resolve()` → 失败则 `/song/url/v1` → 都失败提示登录或换音源。
- 内置示例指向公开测试音频，**无需登录即可验证联网播放与歌词卡自动翻页**。
- 仅导入可信脚本：插件可运行任意 JS。

## 已实测接口（2026-11-09）

`/search`、`/personalized/newsong`、`/top/playlist`、`/lyric/new`（62 行）、
`/comment/music`（热评 15 条）、`/login/qr/key`、`/login/qr/create`、`/login/qr/check`
全部 200；`/song/url/v1` 匿名返回 `url: null`，登录后才返回可播放地址。
详见 `server/README.md`。

## 已知偏差 / 限制

1. 上游 `NeteaseCloudMusicApi` 仓库已归档，接口可能随网易云调整而失效；所有接口均有本地回退，不会白屏。
2. 听歌统计：账号周榜可用时榜单来自网易云；次数/时长/连续天数/时段始终按本机播放记录计算，未登录或取不到账号数据时整页只统计本机播放。
3. 歌单、歌曲信息、歌词、评论、封面均落盘缓存（`files/music_cache/`，设置页可查看与清理）；音频流不落盘，离线时回放已缓存信息、封面回退为原创渐变。
4. 直连模式不依赖任何服务端；若网易云调整加密或风控，可能触发 `-460 网络环境存在风险`，
   播放地址已做「exhigh → standard → br=320000」三级重试与回退。
5. debug + release APK 均已产出；未做平板/横屏/多语言适配。
6. 上一版 App Store 复刻的 APK 与对比图归档在 `qa/legacy-appstore/`。

## 真机验收（2026-11-09）

设备 `e91fb0f`（1080×2400 @420dpi，Android 17 / API 37）。debug APK 覆盖安装成功，
`am start -W` 冷启动正常，全程 `logcat` 无 `FATAL EXCEPTION` / `AndroidRuntime` 报错。

| # | 状态 | 截图 |
| --- | --- | --- |
| 1 | 今天（每日推荐首曲封面 + 可配置歌单卡） | `qa/device/01-today.png` |
| 2 | 新歌上架 | `qa/device/02-new-releases.png` |
| 3 | 我的（个人歌单） | `qa/device/03-mine.png` |
| 4 | 听歌统计（iOS 风格） | `qa/device/04-stats.png` |
| 5 | 搜索 | `qa/device/05-search.png` |
| 6 | 歌曲详情（统计条 / 进度 / 歌词卡 / 评论 / 属性） | `qa/device/06-song-detail.png` |
| 7 | 联网播放中的 mini player | `qa/device/07-playing.png` |
| 8 | Account（保留原选项 + 新增两项） | `qa/device/08-account.png` |
| 9 | 扫码登录二维码 | `qa/device/09-qr-login.png`、`qa/device/qr-code.png` |
| 10 | JS 音源脚本导入 / 载入示例 | `qa/device/10-script-import.png`、`10b-script-loaded.png` |
| 11 | 自定义首页卡片 | `qa/device/11-today-picker.png` |
| 12 | 歌词卡自动翻页 | `qa/device/12-lyric-cards.png`、`12b-lyric-later.png` |
| 13 | 搜索「周杰伦」真实结果 | `qa/device/13-search-results.png` |

关键链路实测：

- 内置示例 JS 音源经 Rhino 解析成功（`sourceInfo()`），`resolve()` 返回公网音频，
  Media3/ExoPlayer 真实播放（mini player 显示「晴天 / 周杰伦」、暂停态、进度推进）。
- 歌词卡随播放进度自动切换：**0:03 第 2/17 句 → 0:11 第 4/17 句**。
- 搜索「周杰伦」返回真实网易云结果（想你就写信 / 屋顶 / 布拉格广场 …）。
- 扫码登录二维码由 `/login/qr/create?qrimg=true` 正常生成，状态轮询显示「等待扫码」；
  **完成登录需您本人用网易云 App 扫码并在手机端确认**（二维码有时效，过期重开该页即可刷新）。
- 未登录时每日推荐 / 私人 FM / 听歌排行 / 播放地址自动回退本地数据，不白屏、不崩溃。

### 扫码登录后的验证（已授权）

`/login/qr/check` 返回 `803 授权登陆成功`，Cookie 经清洗后写入本机 `SharedPreferences`。
实测账号 `KIKO_CAT_canony`（VIP）、9 个真实歌单（喜欢的音乐 93 首、
我关心的新热趋势 30 首、日语流行 23 首、国语经典 162 首 …）、
每日推荐 33 首（首曲「茉茉」）、真实播放地址 `/song/url?id=...` →
`http://m801.music.126.net/...`（br 918368、size 27401646）。

| 状态 | 截图 |
| --- | --- |
| 登录后 Today（真实每日推荐首曲） | `qa/device/20-logged-today.png` |
| 登录后「我的」（真实昵称 + 9 个真实歌单） | `qa/device/21-logged-mine.png` |
| 禁用测试音源后播放网易云真实音频 | `qa/device/24-realtime-playback.png` |

本轮为让登录稳定落地，额外修复：

1. **Cookie 清洗**：二维码返回的原始 Cookie 混有 `Max-Age/Expires/Path` 等 Set-Cookie 属性，
   直接当请求头会让 `/user/account` 返回空账号；`cleanCookie()` 只保留 `name=value` 并去重。
2. **播放地址回退**：`/song/url/v1` 对部分扫码 Cookie 返回 400，改为
   `v1(exhigh) → v1(standard) → /song/url` 依次尝试。
3. **补登机制**：QR key 持久化到 `lastQrKey`，App 启动时会 `redeemQr()` 检查该 key 是否已授权，
   即使页面轮询漏接 803 也能在下次启动自动登录。
4. **轮询健壮性**：单次网络失败不再直接退出轮询（容忍 5 次），803 时先保存 Cookie 再拉资料。
5. 本地恢复通道：可向 `/sdcard/Android/data/com.dsh.appstore/files/cookie.txt` 推送已授权 Cookie，
   App 启动时读取一次即删除，便于在本机补登。

## 直连模式（1.1.0）

**问题**：此前 App 所有请求都打到本机 `http://127.0.0.1:3000` 的 Node 代理，
手机上日用必须先开电脑、跑 `npm start`、再 `adb reverse`，否则扫码页只会显示
「二维码获取失败，请确认代理服务已启动」。

**做法**：把代理做的加密搬到 App 内——`NeteaseCrypto` 实现网易云 web 端的两种协议：

| 协议 | 加密 | 端点 |
| --- | --- | --- |
| weapi | AES-CBC（预设 key）→ AES-CBC（随机 16 位 key）→ RSA 包裹随机 key | `music.163.com/weapi/*` |
| eapi | `path-36cd479b6b5-json-36cd479b6b5-md5` 后 AES-ECB 大写 hex | `interface.music.163.com/eapi/*` |

二维码改由 ZXing 本地生成，登录 key 走 eapi `/api/login/qrcode/unikey`，
扫码结果（803）从 `Set-Cookie` 取 `MUSIC_U`（同时兼容 JSON body 回传的写法）。
设置里保留「自建代理」开关，默认不再使用。

**无电脑验证**（设备 `e91fb0f`，`adb reverse --remove-all` 后、未启动任何 Node 服务）：

| 项 | 结果 | 截图 |
| --- | --- | --- |
| 打开扫码页 | 二维码正常显示，状态「等待扫码」 | `qa/device/direct-qr-final.png` |
| 二维码内容（cv2 解码） | `https://music.163.com/login?codekey=849f7f44-…` | — |
| 今天页 | 真实每日推荐「茉茉 / Nzwin 黄跃成 · 33 首」 | `qa/device/direct-01.png` |
| 我的 | 真实账号 `KIKO_CAT_canony` + 真实歌单列表 | `qa/device/direct-04.png` |
| 歌单详情 | 93 首真实曲目（Man of the House / positions …） | `qa/device/direct-09.png` |
| 播放 | mini player 播放中，进度 0:20/2:35 | `qa/device/direct-09.png` |
| 歌曲详情 | 真实 LRC 逐句歌词卡（3/31）、统计条 | `qa/device/direct-10.png` |
| 听歌统计 | Top 歌曲「晴天 / 周杰伦 328」来自 `/api/v1/play/record` | `qa/device/direct-12.png` |

## 1.2.0 更新

| 需求 | 做法 |
| --- | --- |
| 联网内容要有缓存 | 新增 `MusicCache`：接口 JSON 写入 `files/music_cache/data`，封面写 `img`（最长边 800px JPEG），播放时预取该曲歌词与封面；再次打开歌单/歌曲/歌词直接读缓存，后台静默刷新。TTL：日推 12h、新歌/我的歌单 6h、搜索/评论 24h、歌单与歌曲信息 7 天。 |
| 播放过的歌曲要缓存 | 每次播放写入 `plays.json`（歌曲 + 时间戳，保留 800 条），并预取歌词/封面；「我的 → 最近播放」改为读这份真实记录。 |
| 设置页缓存管理 | 「设置 → 缓存管理」显示总占用、数据缓存、封面、缓存歌曲数，可分别清理数据/封面或清空全部（含播放记录）。 |
| 移除无用设置 | 删掉 App Store 的 Purchased / Subscriptions / 礼卡 / Personalized Recommendations 与假账号资料，改为真实昵称、扫码/退出登录、JS 音源、缓存、版本。 |
| 二级页挡住一级页 | 二级页改为页面栈；点击任意底栏 Tab 立即关闭二级页并切到对应一级页；返回键按栈回退。 |
| 统计取不到账号数据 | `stats()` 先取账号周榜，失败或为空时使用本机播放日志计算整页数据，并在标题下标注数据来源。 |
| 播放胶囊 | 底栏右上方胶囊：`‹ ‖ ›` + 圆形封面，封面外圈画 360° 进度环；点封面进详情，返回回到原页面原滚动位置。 |

**1.2.0 真机验证**（设备 `e91fb0f`，覆盖安装保留登录态与缓存）：

| 项 | 结果 | 截图 |
| --- | --- | --- |
| 设置页 | 只剩账号 / JS 音源 / 缓存 / 版本，无 App Store 遗留项 | `qa/device/v12-21-settings-final.png` |
| 缓存管理 | 总占用 10.0 MB（数据 54 KB + 封面 9.9 MB），可分别清理 | `qa/device/v12-14-cache.png` |
| 清理封面 | 点击后立即变为 0 B，总占用降为 54 KB | `qa/device/v12-15-cleared.png` |
| 缓存落盘 | `files/music_cache/` 下 12 个接口 JSON + `img/` + `plays.json`，重启后仍在 | — |
| 播放胶囊 | 底栏右上方，圆形封面 + 外圈进度环，上一首/播放/下一首 | `qa/device/v12-16-next2.png` |
| 胶囊切歌 | 点下一首从 Man of the House → positions，列表暂停态与封面同步 | `qa/device/v12-16-next2.png` |
| 胶囊背景点击 | 不再穿透到下方列表 | `qa/device/v12-17-capsule-bg.png` |
| 点封面进详情 | 进入当前歌曲详情页 | `qa/device/v12-19-cover.png` |
| 返回原位 | 从详情返回歌单，滚动位置与进入前完全一致 | `qa/device/v12-05-scrolled.png` / `v12-07-back.png` |
| 二级页与底栏 | 详情页打开时点「新歌」直接回到一级页，不再被遮挡 | `qa/device/v12-09-tabswitch.png` |
| 本机统计 | 显示「总播放 1 / 今日 2 分钟 / 连续 1 天」与真实本机 Top 歌曲 | `qa/device/v12-11-stats.png` |
| 最近播放 | 重启后仍显示 Man of the House / positions | `qa/device/v12-22-recent.png` |

## 1.2.1 修复：今天页卡片与二级页数据

**问题**：今天页的卡片 id 是写死的假 id（401–406）。点开二级页后按这个 id 请求歌单接口，
拿回的是别人的歌单或空数据；除第一张「每日推荐」外，其余卡片（私人 FM / 专属歌单 / 我喜欢的音乐 /
华语流行精选 / 新歌速递）只用了本地示例歌曲，卡片上的封面也是生成的渐变图，看起来「没有生效」。

**做法**：

| 卡片 | 数据来源 | 二级页 |
| --- | --- | --- |
| 每日推荐 | `/api/v3/discovery/recommend/songs`，id 固定 401（虚拟） | 用卡片携带的曲目立即渲染，再拉一次最新日推 |
| 私人 FM | 日推曲目轮转，id 402（虚拟） | 直接用卡片曲目，不发歌单请求 |
| 专属歌单 | 账号第 2 个自建歌单（真实 id / 封面 / 曲数） | `/playlist/track/all` |
| 我喜欢的音乐 | `specialType=5` 的红心歌单（真实 id） | 同上 |
| 华语流行精选 | 账号第 3 个自建歌单（真实 id） | 同上 |
| 新歌速递 | `/personalized/newsong` 聚合，id 406（虚拟） | 用卡片曲目 |

- `Fixtures` 增加 `DAILY_CARD_ID / FM_CARD_ID / NEW_CARD_ID` 与 `isVirtualCard()`；
  `playlistSongs()` 遇到虚拟 id 直接返回卡片自带的曲目，不再拿假 id 去请求接口。
- 二级页重写解析：先从「今天页卡片 → 我的歌单 → 新歌专辑」找元数据，标题 / 封面 / 副标题
  （`根据你的口味生成 · 33 首` 等）与卡片完全一致；曲目按类型取，先渲染缓存再刷新。
- 卡片视觉：非首张卡片改用真实封面（失败才回退原创渐变），并加底部渐变保证文字可读；
  副标题改为 `共 N 首`，不再显示账号昵称。
- `personalPlaylists()` 改用 `specialType == 5` 识别「我喜欢的音乐」，不再假设它排第一。

**真机验证**（设备 `e91fb0f`，直连模式）：

| 项 | 结果 | 截图 |
| --- | --- | --- |
| 今天页 4 张卡 | 全部真实封面；日推「茉茉 · 共 33 首」 | `qa/device/v121-01-home.png` |
| 每日推荐二级页 | 标题「每日推荐」、`根据你的口味生成 · 33 首`、与卡片同一封面、首曲「茉茉」 | `qa/device/v121-22-daily-final.png` |
| 私人 FM 二级页 | `私人漫游 · 33 首`，真实 33 首 | `qa/device/v121-03-exclusive.png` |
| 专属歌单二级页 | 真实歌单「日语流行」`共 23 首`，曲目 otukare / 真っ白 / ひとひら | `qa/device/v121-19-custom.png` |
| 我喜欢的音乐二级页 | 红心歌单 93 首，Man of the House / positions / Goldfish Song | `qa/device/v121-13-likeddetail.png` |
| 新歌速递二级页 | `本周新上架 · 8 首`：The Weeknd《Hong Kong》、茉茉、无意… | `qa/device/v121-18-newsongs.png` |
| 卡片增删 | 勾选「华语流行精选」「新歌速递」后立即出现 6 张真实卡片 | `qa/device/v121-17-six-cards.png` |

## 1.3.21 Haze 预设/预览与切歌修复

- 顶栏风格卡片新增：Haze 实时预览、轻/标准/重三档预设（预设后仍可手动微调）、暗色渐变强度滑块。
- Haze 覆盖高度改为从顶栏底部起算（顶栏 64dp 不再计入模糊区，控件始终清晰）。
- 歌曲详情页隐藏底栏：进入详情页的瞬间就释放底栏布局占位，底栏本体继续播放渐隐+模糊+缩小动画；播放胶囊直接到达最终位置，不再二次下移。
- 切歌立即停止旧歌：切换歌曲时先 stop + clearMediaItems，再解析新歌地址，不再出现"点了 B 仍在放 A"。
- 清理临时排查日志。
- 版本号 1.3.21（versionCode 34）。

## 1.3.20 Haze 参数与详情页底栏自定义

- 顶栏风格卡片新增 Haze 参数（仅 Haze 模式显示）：
  - 模糊覆盖高度 120–420dp
  - 清晰到模糊的过渡高度 40–320dp（自动限制不超过覆盖高度）
  - 最终模糊强度 0–140dp
- 新增“歌曲详情页隐藏底栏”开关：进入歌曲详情页时底栏淡出并轻微缩小，返回后恢复。
- 版本号 1.3.20（versionCode 33）。

## 1.3.19 播放地址可用性校验与 Haze 加强

- 修复正常操作时“能解析但播不出”：音源返回的 URL 可能是已失效的 404/403 地址，此前按最快候选直接使用。现在会按 Range 探测每个候选，跳过不可用地址，取第一个真正可播放的。
- 解析结果缓存 10 分钟内也会先探测，失效即重新解析。
- Haze 顶栏改为完全不透明遮罩，模糊半径 48dp，覆盖高度 262dp；详情页文本（歌名、歌手、歌词标题、评论）滚入顶部时被真正模糊，顶栏控件保持清晰。
- 顶部渐变过渡起点下移，清晰到模糊的过渡更缓。
- 版本号 1.3.19（versionCode 32）。

## 1.3.18 自检修复与正常播放修复

- 修复正常触摸操作时 JS 音源无法播放：Node 改为由 App 直接启动子进程，不再依赖可能在 Release 中被系统限制的普通 Service；媒体前台服务继续保活。
- Haze 顶栏：返回、歌曲名、播放按钮保持清晰；详情页歌曲名、歌手、歌词标题等文本在滚入顶栏时真正被模糊，不再有清晰文字透出。
- 设置页“JS 音乐源脚本导入”下方新增“自检与修复”：
  - JS 音源脚本
  - Node 可执行文件
  - 音源文件
  - Node 解析服务健康检查
  - 通知权限
  - 后台常驻权限
- 一键修复会重启 Node 子进程、重写启用音源文件并重新检测。
- 版本号 1.3.18（versionCode 31）。

## 1.3.17 Haze 顶栏与详情文字修正

- 顶栏歌曲名、返回/播放等控件恢复清晰，不再被额外模糊。
- Haze 模糊层增高并加强遮罩，让歌曲详情页的封面、歌曲名、歌手等页面内容在滚入顶栏时真正被模糊，而不是透出清晰文字。
- 版本号 1.3.17（versionCode 30）。

## 1.3.16 动画与细节完善

- 页面切换：旧页面向中心缩小、模糊、渐隐；新页面反向进入。歌曲详情、自定义页、歌单/专辑/艺术家页统一使用。
- 统计页：时段柱状图、周目标进度、歌曲/艺术家排行条使用 iOS 风格增长动画。
- 播放胶囊：进场改为从小放大、透明度渐显、模糊到清晰；退场反向。
- 设置页关闭按钮改为叉号；退出登录移至底部、红字，新增 iOS 风格确认弹窗；版本行始终在最底部。
- 移除流体云实验开关：ColorOS 真机已确认 MediaSession 自动接入，不需要手动开关。
- Node 前台服务通知仅在 Debug 构建显示，Release 不再额外占用通知栏。
- 系统媒体控制补齐“下一首/上一首”命令；媒体通知和系统控制中心均可切歌。
- 沉浸式歌词背景加强流体形变，并在自定义页新增模糊强度、流体形变、流动速度、暗角强度选项。
- 修复 Haze 渐进模糊顶栏歌曲名未被模糊的问题。
- 版本号 1.3.16（versionCode 29）。

## 1.3.15 沉浸式歌词流体封面背景

- 长按歌词卡进入的沉浸式歌词页不再使用纯黑背景。
- 使用当前歌曲封面作为输入，通过 AGSL RuntimeShader 实时做域扭曲 + 噪声流体形变，不是视频或预渲染动画。
- 叠加 30dp 实时模糊层与上/下暗角渐变，保证歌词可读性。
- Android 13+ 使用 GPU shader；低版本/无封面时回退为封面缓慢漂移或动态渐变。
- 背景每帧更新，已用连续截图验证画面持续变化。
- 版本号 1.3.15（versionCode 28）。

## 1.3.14 前台服务、系统媒体与常驻权限

- Node 解析器改为前台服务持有，退出 App 后仍可保持音源服务；通知栏显示“音源解析服务”。
- 接入 Media3 MediaSessionService：系统媒体控制中心、锁屏、蓝牙按键可读取歌曲信息并播放/暂停/切歌；新增 MediaStyle 常驻通知。
- 设置页新增“后台常驻权限”检查，读取 Doze 白名单、后台限制和 run-in-background AppOp；未完全允许时高亮闪烁，点击直达 HyperOS/ColorOS 的省电策略页面，返回后原位刷新。
- 新增“系统实况 / 流体云”实验开关，默认关闭；仅 Android 16+ 显示为可用，使用 promoted ongoing notification，部分 OEM 需要系统白名单，实际展示取决于机型。
- 删除隐藏 WebView/LxWebResolver/lx_runner.html 及旧解析入口，Node 成为唯一 JS 执行器。
- 版本号 1.3.14（versionCode 27）。

## 1.3.13 Node.js 音源运行时

- 引入 Node.js for Mobile（arm64-v8a）承载 LX 音源，彻底替换每首歌新建隐藏 WebView 的执行方式。
- Node 在独立子进程中运行 HTTP 解析服务，UI 进程不加载 libnode；支持 LX 事件协议和 sourceInfo()/resolve() 简单协议，支持 async/await、fetch、process、Buffer、require。
- 首个请求启动 Node；后续复用已加载脚本，避免 WebView 冷启动和 handler 卡死。
- 音频流继续完全由 JS 音源提供；网易云只保留元数据。
- 增加 Node.js LICENSE 资产。
- 诊断用的临时日志与代理已清理。

## 1.3.12 完全使用 JS 音源播放

- 不再回退到网易云音频 URL；播放地址只能由已启用 JS 音源提供。
- 无可用 JS 音源或解析失败时停止播放并给出提示，避免卡在加密网易云缓存。
- 音频缓存统一使用 js_ 命名空间；升级后首次启动会清理旧的 song_* 缓存（可能包含网易云加密流）。
- 元数据（歌单、歌词、评论、封面、音质选项）仍可来自网易云，但音频流完全由 JS 源提供。
- 多源并发、质量优先和缓存优先逻辑保留。

## 1.3.11 多 JS 音源聚合

- 多个已启用 JS 音源改为并发查询，最多 3 个并发。
- 质量优先：先等待 1.8 秒收集结果，按请求音质等级和延迟排序；1.8 秒内没有结果则继续等待首个成功源（最长 25 秒，兼容 LX WebView 冷启动）。
- 全部 JS 源失败后仍回退网易云。
- 播放 URL 缓存键改为「歌曲 + 音质」，避免切换音质后误用旧 URL。
- JS 命中后把当前音质标记为目标音质（LX 协议只返回 URL，音质为请求等级代理）。
- 高音质请求时 JS 源优先于网易云账号受限音质。

## 1.3.10 歌词自动回位与缓存优先播放

- 歌词卡手动划动后等待 2.5 秒自动回到当前播放歌词；点击卡片仍然立即 seek。
- 音频缓存增加完整缓存检测：整首已缓存时跳过新的播放 URL 解析，直接用缓存播放。
- 保存每首歌/音质的原始 URL，缓存命中时用原 URL + 自定义 cache key 交给 Media3，避免额外网络请求。

## 1.3.9 自定义音频缓存上限

- 缓存管理新增「音频缓存上限」滑块，范围 512 MB ～ 5 GB，默认 512 MB。
- 上限持久化到本机；修改后立即应用自定义 LRU 淘汰器，超限时按最久未使用顺序清理。
- 保持音频缓存大小、清理音频缓存和清空全部缓存逻辑；上限调整后总量实时刷新。

## 1.3.8 缓存管理与性能优化

- 缓存管理新增「音频缓存」大小，并计入缓存总量；新增「清理音频缓存」。
- 「清空全部缓存」现在同时清理音频缓存；说明文字更新。
- 音频缓存改为按歌曲 + 音质生成稳定缓存键，重复播放同一首歌直接读缓存。
- 歌曲详情页缓存大小刷新间隔从 2 秒放宽到 5 秒，减少频繁读取缓存索引。
- 为歌曲模型、歌单、歌词、评论、缓存信息等 19 个数据模型标记 @Immutable，帮助 Compose 跳过不必要的重组。
- 首页卡片排序改为 LazyColumn + animateItem，只组合可见行，并保留跟手/弹簧动效。

## 1.3.7 动效、专辑简介与歌曲缓存

- 修复今日页第一张卡的 eyebrow 被写死为 DAILY RECOMMEND 的问题；现在按卡片类型显示 PRIVATE FM 等，不再覆盖私人 FM。
- 自定义首页卡片排序：长按后卡片跟手移动，其余卡片通过 LazyColumn 动画让位，松手用弹簧回弹归位，并加入抬升缩放和触感反馈。
- 专辑简介提升到 MusicApp 根部渲染，位于底栏和播放胶囊之上；简介区域可正常滚到底。
- 新增音频磁盘缓存（Media3 SimpleCache，512MB LRU），重复播放同一歌曲可直接读缓存。
- 详情页「大小」改为读取本地音频缓存，按 B/KB/MB/GB 动态显示；新增 SongStorageInfo / songStorageInfo(songId) 接口，预留给下载功能。已下载时显示「大小（已下载）」和本地下载文件大小。

## 1.3.6 JS 音源在线导入

- 保留「从文件导入 .js」。
- 移除「载入内置示例」和「粘贴脚本内容」，改为「在线链接导入」。
- 仅允许 HTTPS 直链，限制 2MB；raw.githubusercontent.com 失败时自动尝试 gh-proxy.org。
- 内置 pdone/lx-music-source 的 20 个音源，支持一键导入、内容更新和卸载。
- 脚本记录增加 catalogId/sourceUrl，更新时保留启用状态和原记录 ID。

## 1.3.5 Haze 渐进模糊

- 详情页「顶栏风格 → 渐变模糊」替换为「Haze 渐进模糊」；纯色顶栏保留。
- 使用 Haze 2.1.0 的 `hazeSource` / `hazeBlur`，直接对详情页滚动内容做实时渐进模糊，不再绘制自定义模糊顶栏。
- 模糊范围保持 150dp，方向按 Haze 示例：顶部强度 1.0 → 底部 0.0，并叠加 alpha mask 使最底端完全透明。
- 依赖升级：Compose 1.12.0 → 1.12.2；新增 `dev.chrisbanes.haze:haze:2.1.0` 与 `haze-blur:2.1.0`（Apache-2.0）。

## 1.3.4 实时顶栏模糊修正

- 顶栏模糊改为在 Backdrop 的 onDrawBackdrop 回调内应用 DstIn 渐变遮罩，确保硬件模糊层本身被逐像素淡出，而不是在矩形边缘被裁断。
- 移除额外的暗色遮罩，渐变最透明端完全透出页面内容。
- 模糊区域仍是实时采样详情页内容。

## 1.3.3 详情页渐变模糊顶栏

- 自定义 → 歌曲详情页新增「顶栏风格」：纯色 / 渐变模糊。
- 渐变模糊使用 150dp 的实时背景毛玻璃，并从顶部向下渐隐到页面内容，接近 iOS 大范围材质顶栏；纯色保持原有 64dp 顶栏。
- 选项持久化到本机。

## 1.3.2 交互与音源修复

- 切歌/重新开始播放时，歌词卡会先锁定第一张，避免被上一首的播放进度立刻拉走。
- 设置内二级页面（自定义、扫码、JS 导入、缓存、音质）返回后回到设置页。
- 歌词卡流光改为程序化、非短周期循环的 HSV/渐变变化；取色经过饱和度提升和黄金角散列，逐卡颜色不再趋同。
- 播放胶囊 ↔ 详情控制栏改为与左右切换一致的渐隐 + 模糊过渡。
- JS 音源导入支持 LX Music 的 `lx.on / lx.send / lx.request`；现代语法脚本在 Web Worker 中执行，并缓存解析出的播放 URL 10 分钟。

## 1.3.1 沉浸式 AMLV 歌词

- 长按任意歌词卡进入全屏沉浸式歌词界面；只显示歌词，状态栏/导航栏自动隐藏，返回键退出。
- 改用 `amlv-main` 的原生 Compose 实现（`com.dokar.amlv`）：`LyricsView` + `LyricsViewState`，保留其弹簧缩放、透明度过渡、滚动动画和上下渐隐边缘，不再使用 WebView。
- 播放进度由应用播放器逐帧同步到 `LyricsViewState.seekTo()`，AMLV 内部时钟保持暂停，仅作为展示层。
- AMLV 许可证随包附带：`androidApp/src/main/assets/amlv/LICENSE.txt`。
- 产物：`dist/音乐-1.3.1-release.apk`，versionCode 14 / versionName 1.3.1。

## 1.3.0 性能优化与瘦身

- Release 开启 R8 代码压缩 + 资源压缩，并为 Rhino 反射执行、枚举持久化、OkHttp/Media3 补充 keep 规则。
- 移除未使用的系统/壁纸 drawable；保留全部业务源码与功能。
- 播放进度 ticker 改为仅在真正播放时运行，暂停/报错/释放时停止。
- 歌词卡无限轮播的索引空间从 200 轮降到 40 轮，视觉上仍是无限轮播。
- Release APK：**11.7 MB → 2.5 MB**。
- 产物：`dist/音乐-1.3.0-release.apk`，版本号 1.3.0 / versionCode 13。

## 合规

本项目为本地界面与播放链路研究，非官方客户端，与网易云及各版权方无关联。
扫码登录使用您本人账号；封面、歌词、音频版权归 respective 权利人所有，请勿分发缓存内容。
`backdrop` 液态玻璃库及其组件版权归 Kyant0 所有，按 Apache-2.0 使用（见 `LICENSE`）。
