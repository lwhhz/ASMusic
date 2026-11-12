# 贡献指南

感谢关注 **ASMusic**。在动手之前请先读一遍下面几条——它们能省掉双方很多时间。

## 这个项目是什么

一个**界面与播放链路的研究型项目**：Compose Multiplatform 写的音乐播放器，播放地址完全由**用户自己导入的 JS 音源脚本**在运行时解析。

因此：

- 仓库**不包含、也不会包含**任何音源地址、会员账号、破解接口
- 播放不出来，绝大多数情况是**没有导入可用音源**，不是程序 bug

## Issue / PR

- 本仓库的 **Issues 与 Discussions 已关闭**（音源相关的求助会淹没真正的 bug）
- 确实要反馈问题，请用 PR 的形式附上：复现步骤、设备与系统版本、`adb logcat` 相关片段
- 提问型内容请发到你自己的渠道（博客/群组），不要开 PR 只为了提问

## 可以接受的 PR

| 类型 | 说明 |
|---|---|
| 崩溃 / 内存泄漏 / 卡顿修复 | 附 `TotalTime`、`dumpsys meminfo`、`gfxinfo` 前后对比 |
| 无障碍 / 适配问题 | 附设备型号与系统版本 |
| 文档修正 | 错别字、过期说明、构建步骤问题 |
| 新歌词源接入 | 必须说明许可证，且**不能**是破解接口 |

## 不接受

- 内置/打包任何音源脚本或音乐文件
- 绕过会员、破解签名接口（QQ QRC / 酷狗 KRC 等私有接口的破解实现）
- 移除 `LICENSE` / `THIRD-PARTY-NOTICES.md` 或其中的声明
- 把 `applicationId` 改成别的包名后重新分发（会破坏升级链路）

## 开发环境

```bash
git clone https://github.com/lwhhz/ASMusic.git
cd ASMusic
bash scripts/fetch-node-libs.sh      # 下载 Node.js 运行时（约 70 MB，不入库）
./gradlew :androidApp:assembleDebug
```

- JDK 21，Android SDK 见 `androidApp/build.gradle.kts`
- 签名：把 keystore 放到 `keys/appstore-release.jks`，密码写在 `local.properties`
  （两者都在 `.gitignore` 里，**不要提交**）

## 许可证

本项目以 **AGPL-3.0** 发布。提交 PR 即表示你同意以同一许可证授权你的贡献。

特别注意：项目打包了 [AMLL](https://github.com/amll-dev/applemusic-like-lyrics)（AGPL-3.0），
任何分发本软件（含 APK）的行为都必须同时提供完整对应源码。
