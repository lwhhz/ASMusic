# 第三方组件与许可证

本项目以 **AGPL-3.0** 发布（见仓库根目录 [LICENSE](LICENSE)）。原因是沉浸式歌词页面打包了
AMLL（Apple Music-like Lyrics），而 AMLL 采用 AGPL-3.0。

## 源码获取

AGPL-3.0 第 13 条要求向通过网络与程序交互的用户提供源码。本仓库即完整对应源码：

- Android 应用：`appstore-compose/`（Kotlin Multiplatform + Compose）
- AMLL 离线包构建脚本：`appstore-compose/web/amll/`（`pnpm install && node build.mjs`）
- 打包产物：`appstore-compose/androidApp/src/main/assets/amll/bundle.js` / `bundle.css`

## 组件清单

| 组件 | 版本 | 许可证 | 用途 | 使用方式 |
| --- | --- | --- | --- | --- |
| [@applemusic-like-lyrics/core](https://github.com/amll-dev/applemusic-like-lyrics) | 0.6.0 | **AGPL-3.0-only** | 沉浸式歌词渲染（`LyricPlayer`）、Pixi 流体背景（`BackgroundRender`） | 通过 esbuild 打包进 `assets/amll/bundle.js`，在 WebView 中运行 |
| [@applemusic-like-lyrics/lyric](https://github.com/amll-dev/applemusic-like-lyrics) | 1.1.0 | **AGPL-3.0-only** | TTML / LRC / YRC 歌词解析 | 同上，打包进 `bundle.js` |
| [pixi.js](https://github.com/pixi-js/pixi.js) | 7.4.3 | MIT | AMLL 的 WebGL 渲染后端 | 打包进 `bundle.js` |
| [@pixi/filter-bulge-pinch](https://github.com/pixi-js/filters) | 5.1.1 | MIT | AMLL 背景形变滤镜 | 打包进 `bundle.js` |
| [AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db) | 滚动更新 | **CC0-1.0** | 逐字 TTML 歌词数据（按网易云歌曲 ID 拉取，本地缓存 7 天） | 运行时 HTTP 获取 `ncm-lyrics/{id}.ttml`，不随包分发 |
| [Haze](https://github.com/chrisbanes/haze) | 2.1.0 | Apache-2.0 | 顶栏渐进模糊 | Gradle 依赖 |
| [AndroidX Media3](https://github.com/androidx/media) | 1.5.1 | Apache-2.0 | 播放器 / 媒体会话 | Gradle 依赖 |
| [androidx.compose](https://developer.android.com/jetpack/compose) | — | Apache-2.0 | UI | Gradle 依赖 |
| [Rhino](https://github.com/mozilla/rhino) | 1.7.15 | MPL-2.0 | 旧式 JS 音源脚本执行 | Gradle 依赖 |
| Node.js Mobile | 见 `assets/node` | MIT 等 | JS 音源运行时 | 预编译二进制 |
| [amlv](https://github.com/dokar3/amlv) | 见 `assets/amlv/LICENSE.txt` | Apache-2.0 | 已停用的原生歌词视图（保留代码与许可） | 源码 |
| [backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | — | Apache-2.0 | 液态玻璃组件 | 源码 |

| [酷狗 KRC 歌词接口](https://www.kugou.com/) | 非官方接口 | **实验性、默认关闭** | 逐字歌词兜底（按歌名/歌手/时长检索） | 运行时按需请求，仅本机缓存，不随包分发 |

## 合规说明

- AMLL 相关代码（`bundle.js`）以 AGPL-3.0 授权，任何再分发本 APK 的行为都必须同时提供
  上述完整源码，并保留本文件与 `LICENSE`。
- AMLL TTML DB 为 CC0-1.0（公有领域贡献），歌词数据版权归各贡献者与原权利人；本项目仅在
  运行时按需拉取并在本机缓存，不随 APK 分发。
- 本项目为本地界面与播放链路研究，非官方客户端，与网易云音乐及各版权方无关联。

### 关于酷狗 KRC（实验性功能）

- 「歌词源 › 酷狗 KRC 逐字」**默认关闭**，需用户主动开启。
- 开启后应用会以歌名/歌手/时长调用酷狗的公开歌词检索与下载接口，获取的逐字歌词**仅缓存于本机**，不随安装包分发、不上传。
- 歌词内容的版权归酷狗音乐及相应权利人所有；该功能仅用于个人播放场景，请勿据此二次分发或商用。
- 若权利人提出异议，该功能可整体移除，不影响其它歌词来源（TTML / 网易云）。
