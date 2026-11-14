package com.kyant.backdrop.catalog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.kyant.backdrop.catalog.appstore.music.DensityDefaults
import com.kyant.backdrop.catalog.appstore.music.MusicApp
import com.kyant.backdrop.catalog.appstore.music.MusicServices

class MainActivity : ComponentActivity() {

    private var bridge: MusicBridgeImpl? = null
    private lateinit var picker: ActivityResultLauncher<String>
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installPlatformBridge(this) // keeps the legacy share Intent bridge alive

        val config = AppConfig(this)
        // Local recovery channel: push a cookie.txt into the app's external files dir
        // to inject an already-authorized QR cookie (read once, then removed).
        runCatching {
            val cookieFile = java.io.File(getExternalFilesDir(null), "cookie.txt")
            if (cookieFile.exists()) {
                val raw = cookieFile.readText().trim()
                if (raw.isNotBlank()) config.cookie = cleanCookie(raw)
                cookieFile.delete()
            }
        }
        val proxy = NeteaseApi(config)
        val direct = NeteaseDirect(config)
        val cache = MusicCache(this)
        // Reuse the existing player: creating a second MusicPlayer would open the same
        // SimpleCache folder and crash with "Another SimpleCache instance uses the folder".
        val player = PlaybackHolder.player ?: MusicPlayer(
            this,
            MusicServices.state,
            { message -> MusicServices.bridge.showToast(message) },
            onEnded = { bridge?.onTrackEnded() },
            cacheLimitBytes = config.audioCacheLimitMb.toLong() * 1024L * 1024L
        ).also { PlaybackHolder.player = it }

        picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            val name = queryDisplayName(uri) ?: "imported.js"
            val content = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (content.isNullOrBlank()) {
                MusicServices.bridge.showToast("文件读取失败")
            } else {
                val info = MusicServices.bridge.importScript(name, content)
                MusicServices.bridge.showToast(
                    if (info != null) "已导入 ${info.name}" else "脚本解析失败，请检查 sourceInfo()"
                )
            }
        }

        PlaybackHolder.player = player
        startFrameMonitor()
        // 冷启动预热缓存索引，避免第一次点缓存歌曲时卡在磁盘扫描上。
        Thread { runCatching { player.warmCache() } }.start()
        startService(Intent(this, PlaybackService::class.java))
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        bridge = MusicBridgeImpl(applicationContext, config, proxy, direct, cache, player) { picker.launch("*/*") }
        MusicServices.bridge = bridge!!
        bridge!!.initState()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // Record the device density once, then apply the user's UI / font scale.
            // Lyric surfaces opt out by restoring this stored density.
            val baseDensity = LocalDensity.current
            val musicState = MusicServices.state
            DensityDefaults.original = baseDensity
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density * musicState.uiScale,
                    fontScale = baseDensity.fontScale
                )
            ) {
                MusicApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bridge?.refreshBackgroundPermission()
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

    override fun onDestroy() {
        bridge?.release()
        PlaybackHolder.player = null
        stopService(Intent(this, PlaybackService::class.java))
        super.onDestroy()
    }
    /**
     * 轻量帧监控：Choreographer 每帧回调，记录掉帧分布。
     * 设备无 root、trace_processor 也不可达，用这个替代 Perfetto 做第一手定位。
     */
    private fun startFrameMonitor() {
        val file = java.io.File(
            android.os.Environment.getExternalStorageDirectory(),
            "Android/data/$packageName/files/frames.txt"
        )
        file.parentFile?.mkdirs()
        val choreographer = android.view.Choreographer.getInstance()
        var last = 0L
        var frames = 0
        var jank = 0
        var worst = 0L
        var windowStart = System.currentTimeMillis()
        val cb = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val now = System.currentTimeMillis()
                if (last != 0L) {
                    val dt = now - last
                    frames++
                    if (dt > 20) {
                        jank++
                        if (dt > worst) worst = dt
                        runCatching {
                            val mode = MusicServices.state.heartMode
                            val playing = MusicServices.state.playing
                            file.appendText("$now jank dt=${dt}ms heart=$mode playing=$playing page=${MusicServices.state.debugPage}\n")
                        }
                    }
                }
                last = now
                if (now - windowStart >= 5000) {
                    runCatching {
                        file.appendText(
                            "$now summary frames=$frames jank=$jank worst=${worst}ms " +
                                "heart=${MusicServices.state.heartMode} page=${MusicServices.state.debugPage}\n"
                        )
                    }
                    frames = 0; jank = 0; worst = 0L; windowStart = now
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(cb)
    }

}
