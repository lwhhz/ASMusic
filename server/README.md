# 本机网易云 API 代理（可选）

> **1.1.0 起不再需要本服务。** App 默认「直连」模式：`NeteaseCrypto` 在端内实现
> weapi / eapi 加密，直接访问 `music.163.com`，手机联网即可扫码登录与播放。
> 只有想在代理层做缓存、抓包或换域名时才需要启动本服务，并在
> 「我的 → 头像 → 链接网易云账号 → 自建代理」里填入地址。

本服务为自建代理通道，基于
[`NeteaseCloudMusicApi@4.32.0`](https://github.com/Binaryify/NeteaseCloudMusicApi)（上游仓库已归档，
但 npm 包与其转发接口仍可用）。

## 启动

```bash
cd server
npm install
npm start            # 默认 0.0.0.0:3000，可用 PORT 环境变量覆盖
```

Android 设备访问方式（USB 调试）：

```bash
adb reverse tcp:3000 tcp:3000     # 之后 App 里填 http://127.0.0.1:3000
```

若设备与电脑不在同一台机器，请在 App 的「设置」里填局域网/公网地址，例如
`http://192.168.1.10:3000`。注意 `http://` 明文流量需要在 Manifest 里允许
（本项目已设置 `android:usesCleartextTraffic="true"`）。

## 已实测接口（2026-11-09）

| 接口 | 结果 |
| --- | --- |
| `/search?keywords=...&type=1` | 200，返回歌曲 |
| `/personalized/newsong` | 200，10 首新歌 |
| `/top/playlist` | 200 |
| `/lyric/new?id=...` | 200，含 LRC 逐行歌词 |
| `/comment/music?id=...` | 200，热评 + 总数 |
| `/login/qr/key` | 200，返回 unikey |
| `/login/qr/create?key=...&qrimg=true` | 200，返回 base64 PNG 二维码 |
| `/login/qr/check?key=...` | 801 等待扫码 / 802 待确认 / 803 成功并返回 Cookie |
| `/song/url/v1?id=...` | **匿名返回 `url: null`，必须带登录 Cookie** |

Cookie 不保存在本服务：客户端扫码成功后保存 Cookie，并在后续每个请求的
`Cookie` 头上行透传，由代理转发给网易云。

## JS 音源插件

`plugins/sample-source.js` 是协议 v1 的示例。App 的「设置 → JS 音乐源脚本导入」
可导入任意 `.js` 文件，用 Rhino 执行 `sourceInfo()` / `resolve(songJson)`，
返回值作为播放 URL；返回空字符串时回退到网易云 `/song/url/v1`。

仅导入可信脚本——脚本在应用进程内以任意 JS 权限运行。

## 合规

本代理仅用于本地界面与播放链路研究。歌曲封面、歌词、音频流版权归 respective
权利人所有；请勿分发缓存内容。扫码登录使用您本人的网易云账号。
