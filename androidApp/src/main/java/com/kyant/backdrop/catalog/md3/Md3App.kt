@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kyant.backdrop.catalog.md3

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import com.kyant.backdrop.catalog.appstore.music.SelfCheckItem
import com.kyant.backdrop.catalog.appstore.music.SelfCheckLevel
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// 路由
// ---------------------------------------------------------------------------

sealed interface Md3Dest {
    data object Home : Md3Dest
    data object Discover : Md3Dest
    data object Library : Md3Dest
    data object Stats : Md3Dest
    data object Search : Md3Dest
    data object Settings : Md3Dest
    data object SelfCheck : Md3Dest
    data class Song(val id: Long) : Md3Dest
    data class Playlist(val id: Long, val name: String) : Md3Dest
    data class Album(val id: Long, val name: String) : Md3Dest
    data class Artist(val id: Long, val name: String) : Md3Dest
}

private data class Md3Tab(
    val label: String,
    val filled: ImageVector,
    val outlined: ImageVector
)

private val Md3Tabs = listOf(
    Md3Tab("首页", Icons.Rounded.Home, Icons.Outlined.Home),
    Md3Tab("发现", Icons.Rounded.Explore, Icons.Outlined.Explore),
    Md3Tab("资料库", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    Md3Tab("统计", Icons.Rounded.Insights, Icons.Outlined.Insights),
    Md3Tab("搜索", Icons.Rounded.Search, Icons.Outlined.Search)
)

/**
 * 悬浮迷你播放条占用的高度。
 *
 * 列表要用它做底部留白，否则最后一条会被胶囊盖住。
 * 由外壳提供，二级页为 0（二级页不显示迷你条）。
 */
val LocalMd3MiniInset = compositionLocalOf { 0.dp }

/** 列表底部留白 = 常规留白 + 迷你胶囊高度。 */
@Composable
fun md3ListBottom(base: Dp = 28.dp): Dp = base + LocalMd3MiniInset.current

// ---------------------------------------------------------------------------
// 外壳
// ---------------------------------------------------------------------------

@Composable
fun Md3App(config: Md3Config) {
    val state = MusicServices.state
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 720

    var tab by remember { mutableIntStateOf(if (config.rememberTab) config.lastTab else 0) }
    var stack by remember { mutableStateOf<List<Md3Dest>>(emptyList()) }
    var showQueue by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scrolls = remember { List(5) { androidx.compose.foundation.lazy.LazyListState() } }

    fun push(dest: Md3Dest) { stack = stack + dest }
    fun pop() { if (stack.isNotEmpty()) stack = stack.dropLast(1) }

    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            )
            context.startActivity(intent)
        }
        (context as? android.app.Activity)?.finish()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    LaunchedEffect(tab) { if (config.rememberTab) config.lastTab = tab }

    LaunchedEffect(state.toast) {
        val msg = state.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        state.toast = null
    }

    // 根页按返回先回「首页」，而不是直接退出应用
    BackHandler(enabled = stack.isEmpty() && tab != 0) { tab = 0 }
    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val useRail = when (config.navLayout) {
        NavLayout.Rail -> true
        NavLayout.Bar -> false
        NavLayout.Auto -> wide
    }

    val top = stack.lastOrNull()
    val miniVisible = config.showMiniPlayer && state.currentSong != null && stack.isEmpty()
    val miniInset = if (miniVisible) 84.dp else 0.dp

    Scaffold(
        // 不接管系统栏：页面自己吃状态栏，底栏自己吃导航栏。
        // 否则会像上一版那样在顶部多出一条空白「顶栏占位」。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        bottomBar = {
            if (!useRail && stack.isEmpty()) {
                Box(Modifier.navigationBarsPadding()) {
                    Md3BottomNavBar(tab = tab, onSelect = { stack = emptyList(); tab = it })
                }
            }
        }
    ) { padding ->
        CompositionLocalProvider(LocalMd3MiniInset provides miniInset) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxSize()) {
                    if (useRail) {
                        NavigationRail(modifier = Modifier.statusBarsPadding()) {
                            Spacer(Modifier.height(8.dp))
                            Md3Tabs.forEachIndexed { i, t ->
                                NavigationRailItem(
                                    selected = tab == i && stack.isEmpty(),
                                    onClick = {
                                        stack = emptyList()
                                        tab = i
                                    },
                                    icon = {
                                        Icon(
                                            if (tab == i) t.filled else t.outlined,
                                            contentDescription = t.label
                                        )
                                    },
                                    label = { Text(t.label) }
                                )
                            }
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .then(if (stack.isNotEmpty()) Modifier.navigationBarsPadding() else Modifier)
                    ) {
                        AnimatedContent(
                            targetState = top ?: listOf(
                                Md3Dest.Home, Md3Dest.Discover, Md3Dest.Library,
                                Md3Dest.Stats, Md3Dest.Search
                            )[tab],
                            transitionSpec = {
                                val isPush = targetState is Md3Dest.Song ||
                                    targetState is Md3Dest.Playlist ||
                                    targetState is Md3Dest.Album ||
                                    targetState is Md3Dest.Artist ||
                                    targetState is Md3Dest.Settings ||
                                    targetState is Md3Dest.SelfCheck
                                if (isPush) {
                                    (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(200))) togetherWith
                                        fadeOut(tween(140))
                                } else {
                                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                                }
                            },
                            label = "md3-nav"
                        ) { dest ->
                            Md3Route(
                                dest = dest,
                                config = config,
                                scrolls = scrolls,
                                onPush = ::push,
                                onPop = ::pop,
                                onTab = { tab = it },
                                onQueue = { showQueue = true },
                                onRestart = { restartApp() }
                            )
                        }
                    }
                }

                // 状态栏遮罩：列表滚上去时图标不会糊在亮色封面上
                if (stack.isEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(md3TopInset() + 10.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                    )
                                )
                            )
                    )
                }

                // 悬浮迷你胶囊：内容从它下面穿过去
                if (miniVisible) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Md3MiniPlayer(onOpen = {
                            state.currentSong?.let { push(Md3Dest.Song(it.id)) }
                        })
                    }
                }
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Md3QueueSheet { showQueue = false }
        }
    }
}

@Composable
private fun Md3Route(
    dest: Md3Dest,
    config: Md3Config,
    scrolls: List<androidx.compose.foundation.lazy.LazyListState>,
    onPush: (Md3Dest) -> Unit,
    onPop: () -> Unit,
    onTab: (Int) -> Unit,
    onQueue: () -> Unit,
    onRestart: () -> Unit
) {
    when (dest) {
        Md3Dest.Home -> Md3HomeScreen(
            onOpenSong = { onPush(Md3Dest.Song(it)) },
            onOpenPlaylist = { id, name -> onPush(Md3Dest.Playlist(id, name)) },
            onOpenAlbum = { id, name -> onPush(Md3Dest.Album(id, name)) },
            onOpenArtist = { id, name -> onPush(Md3Dest.Artist(id, name)) },
            onOpenSettings = { onPush(Md3Dest.Settings) },
            onOpenSearch = { onTab(4) },
            scroll = scrolls[0]
        )
        Md3Dest.Discover -> Md3DiscoverScreen(
            onOpenPlaylist = { id, name -> onPush(Md3Dest.Playlist(id, name)) },
            onOpenAlbum = { id, name -> onPush(Md3Dest.Album(id, name)) },
            onOpenSong = { onPush(Md3Dest.Song(it)) },
            onOpenSettings = { onPush(Md3Dest.Settings) },
            onOpenSearch = { onTab(4) },
            scroll = scrolls[1]
        )
        Md3Dest.Library -> Md3LibraryScreen(
            onOpenPlaylist = { id, name -> onPush(Md3Dest.Playlist(id, name)) },
            onOpenSong = { onPush(Md3Dest.Song(it)) },
            onOpenSettings = { onPush(Md3Dest.Settings) },
            onOpenSearch = { onTab(4) },
            scroll = scrolls[2]
        )
        Md3Dest.Stats -> Md3StatsScreen(
            onOpenSettings = { onPush(Md3Dest.Settings) },
            scroll = scrolls[3]
        )
        Md3Dest.Search -> Md3SearchScreen(
            onOpenSong = { onPush(Md3Dest.Song(it)) },
            onOpenPlaylist = { id, name -> onPush(Md3Dest.Playlist(id, name)) },
            onOpenArtist = { id, name -> onPush(Md3Dest.Artist(id, name)) },
            onOpenAlbum = { id, name -> onPush(Md3Dest.Album(id, name)) },
            scroll = scrolls[4]
        )
        is Md3Dest.Song -> Md3SongScreen(
            songId = dest.id,
            onBack = onPop,
            onOpenArtist = { id, name -> onPush(Md3Dest.Artist(id, name)) },
            onOpenAlbum = { id, name -> onPush(Md3Dest.Album(id, name)) },
            onOpenQueue = onQueue
        )
        is Md3Dest.Playlist -> Md3PlaylistScreen(
            id = dest.id,
            fallbackName = dest.name,
            onBack = onPop,
            onOpenSong = { onPush(Md3Dest.Song(it)) },
            onOpenArtist = { id, name -> onPush(Md3Dest.Artist(id, name)) }
        )
        is Md3Dest.Album -> Md3AlbumScreen(
            id = dest.id,
            fallbackName = dest.name,
            onBack = onPop,
            onOpenSong = { onPush(Md3Dest.Song(it)) },
            onOpenArtist = { id, name -> onPush(Md3Dest.Artist(id, name)) }
        )
        is Md3Dest.Artist -> Md3ArtistScreen(
            id = dest.id,
            fallbackName = dest.name,
            onBack = onPop,
            onOpenSong = { onPush(Md3Dest.Song(it)) },
            onOpenAlbum = { id, name -> onPush(Md3Dest.Album(id, name)) }
        )
        Md3Dest.Settings -> Md3SettingsScreen(
            config = config,
            onBack = onPop,
            onOpenSelfCheck = { onPush(Md3Dest.SelfCheck) },
            onRestart = onRestart
        )
        Md3Dest.SelfCheck -> Md3SelfCheckScreen(onBack = onPop)
    }
}

/** 底部导航：药丸指示器 + 仅选中项显示文字。 */
@Composable
private fun Md3BottomNavBar(tab: Int, onSelect: (Int) -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Md3Tabs.forEachIndexed { i, t ->
                val selected = tab == i
                val bg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent,
                    label = "nav-bg"
                )
                val fg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "nav-fg"
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .md3SurfaceClick(
                            onClick = { onSelect(i) },
                            shape = RoundedCornerShape(20.dp),
                            scaleTo = .94f
                        )
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .height(32.dp)
                            .then(
                                if (selected) Modifier.widthIn(min = 56.dp)
                                else Modifier.width(48.dp)
                            )
                            .clip(CircleShape)
                            .background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (selected) t.filled else t.outlined,
                            contentDescription = t.label,
                            tint = fg,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = selected) {
                        Text(
                            t.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = fg,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 自检（MD3 版）
// ---------------------------------------------------------------------------

@Composable
fun Md3SelfCheckScreen(onBack: () -> Unit) {
    val bridge = MusicServices.bridge
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var items by remember { mutableStateOf<List<SelfCheckItem>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }

    fun run() {
        if (running) return
        running = true
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        scope.launch {
            items = runCatching { bridge.fullSelfCheck() }.getOrElse {
                listOf(
                    SelfCheckItem(
                        "自检中断",
                        false,
                        it.message ?: "未知错误",
                        SelfCheckLevel.Fail,
                        "运行环境"
                    )
                )
            }
            elapsed = started.elapsedNow().inWholeMilliseconds
            running = false
        }
    }

    val fails = items.count { it.level == SelfCheckLevel.Fail }
    val warns = items.count { it.level == SelfCheckLevel.Warn }
    val groups = items.groupBy { it.group.ifBlank { "其它" } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Md3TopBar(title = "软件全面自检", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        if (items.isEmpty()) {
                            Text(
                                "逐项检查运行环境、网络、播放内核、Node 与缓存",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "不自动运行：会发起真实网络请求，点下面的按钮才开始。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                when {
                                    fails > 0 -> "发现 $fails 项异常"
                                    warns > 0 -> "全部通过，$warns 项提示"
                                    else -> "全部通过"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "共 ${items.size} 项 · 通过 ${items.size - fails - warns} · 提示 $warns · 失败 $fails · 耗时 ${elapsed}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Md3Button(
                                onClick = { run() },
                                text = when {
                                    running -> "自检中…"
                                    items.isEmpty() -> "开始自检"
                                    else -> "重新自检"
                                },
                                size = Md3Size.S,
                                enabled = !running
                            )
                            if (items.isNotEmpty() && !running) {
                                Md3Button(
                                    onClick = {
                                        val text = buildString {
                                            appendLine("ASMusic 自检（Material 3 界面）")
                                            appendLine("共 ${items.size} 项 · 失败 $fails · 提示 $warns")
                                            items.groupBy { it.group }.forEach { (g, rows) ->
                                                appendLine("[$g]")
                                                rows.forEach {
                                                    appendLine("  ${it.level}  ${it.label}：${it.detail}")
                                                }
                                            }
                                        }
                                        com.kyant.backdrop.catalog.appstore.Platform.bridge.copy(text)
                                        bridge.showToast("已复制")
                                    },
                                    text = "复制结果",
                                    style = Md3ButtonStyle.Tonal,
                                    size = Md3Size.S
                                )
                            }
                            if (running) Md3LoadingIndicator()
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    SettingsCard {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "会检查这些内容",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(10.dp))
                            listOf(
                                "运行环境" to "版本、系统、CPU 架构、堆内存",
                                "网络与接口" to "域名连通、song/detail 实测、cookie 有效性",
                                "播放内核" to "ExoPlayer 状态、当前曲目、解析缓存",
                                "Node 运行时" to "三个 .so 是否在位、解析服务健康",
                                "音源与歌词源" to "脚本启用与文件可读、TTML / KRC 开关",
                                "缓存与存储" to "写入回读、三类占用、可用空间",
                                "权限与后台" to "通知权限、后台常驻"
                            ).forEach { (title, desc) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Box(
                                        Modifier
                                            .padding(top = 3.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(title, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            groups.forEach { (group, rows) ->
                item {
                    Text(
                        group,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    SettingsCard {
                        Column {
                            rows.forEachIndexed { i, item ->
                                if (i > 0) Md3Divider()
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        Modifier
                                            .padding(top = 6.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (item.level) {
                                                    SelfCheckLevel.Pass -> MaterialTheme.colorScheme.primary
                                                    SelfCheckLevel.Warn -> Color(0xFFF2A20C)
                                                    SelfCheckLevel.Fail -> MaterialTheme.colorScheme.error
                                                }
                                            )
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.label, style = MaterialTheme.typography.bodyLarge)
                                        if (item.detail.isNotBlank()) {
                                            Text(
                                                item.detail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
