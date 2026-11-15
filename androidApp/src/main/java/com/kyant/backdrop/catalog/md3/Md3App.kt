@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kyant.backdrop.catalog.md3

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
    data object Player : Md3Dest
    data object SelfCheck : Md3Dest
    data class Song(val id: Long) : Md3Dest
    data class Playlist(val id: Long, val name: String) : Md3Dest
    data class Album(val id: Long, val name: String) : Md3Dest
    data class Artist(val id: Long, val name: String) : Md3Dest
}

private data class Md3Tab(
    val label: String,
    val filled: ImageVector,
    val outlined: ImageVector,
    val dest: Md3Dest
)

private val Md3Tabs = listOf(
    Md3Tab("首页", Icons.Rounded.Home, Icons.Outlined.Home, Md3Dest.Home),
    Md3Tab("发现", Icons.Rounded.Explore, Icons.Outlined.Explore, Md3Dest.Discover),
    Md3Tab("资料库", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic, Md3Dest.Library),
    Md3Tab("统计", Icons.Rounded.Insights, Icons.Outlined.Insights, Md3Dest.Stats),
    Md3Tab("搜索", Icons.Rounded.Search, Icons.Outlined.Search, Md3Dest.Search)
)

// ---------------------------------------------------------------------------
// 外壳
// ---------------------------------------------------------------------------

@Composable
fun Md3App(config: Md3Config) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 720

    var tab by remember { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf<List<Md3Dest>>(emptyList()) }
    var showQueue by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // 各个 tab 的滚动位置独立保存，切回来不会跳回顶部
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

    // toast 统一走 Snackbar
    LaunchedEffect(state.toast) {
        val msg = state.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        state.toast = null
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val useRail = when (config.navLayout) {
        NavLayout.Rail -> true
        NavLayout.Bar -> false
        NavLayout.Auto -> wide
    }

    val top = stack.lastOrNull()

    // 迷你播放器
    val miniPlayer = @Composable {
        if (config.showMiniPlayer && state.currentSong != null && top !is Md3Dest.Player) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Md3MiniPlayer(onOpen = { push(Md3Dest.Player) })
            }
        }
    }

    val navigation = @Composable {
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
                        icon = { Icon(if (tab == i) t.filled else t.outlined, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        } else {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Md3Tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i && stack.isEmpty(),
                        onClick = {
                            stack = emptyList()
                            tab = i
                        },
                        icon = { Icon(if (tab == i) t.filled else t.outlined, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // 底栏只属于主标签页：二级页（歌单/专辑/艺人/详情/设置/播放）都盖住它，
            // 与经典界面的行为一致，也让详情页能用满整屏。
            if (!useRail && stack.isEmpty()) Column { miniPlayer(); navigation() }
        },
        floatingActionButton = {
            if (stack.isEmpty() && tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val pool = state.recentSongs.ifEmpty { state.playQueue }
                        if (pool.isNotEmpty()) {
                            bridge.playFrom(pool.shuffled(), pool.shuffled().first())
                        }
                    },
                    icon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                    text = { Text("随便听听") }
                )
            }
        }
    ) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            if (useRail) {
                navigation()
                Box(Modifier.width(1.dp).fillMaxSize().background(MaterialTheme.colorScheme.outlineVariant))
            }
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = top ?: Md3Tabs[tab].dest,
                    transitionSpec = {
                        (fadeIn(tween(220)) ) togetherWith fadeOut(tween(160))
                    },
                    label = "md3-nav"
                ) { dest ->
                    when (dest) {
                        Md3Dest.Home -> Md3HomeScreen(
                            onOpenSong = { push(Md3Dest.Song(it)) },
                            onOpenPlaylist = { id, name -> push(Md3Dest.Playlist(id, name)) },
                            onOpenAlbum = { id, name -> push(Md3Dest.Album(id, name)) },
                            onOpenArtist = { id, name -> push(Md3Dest.Artist(id, name)) },
                            onOpenSettings = { push(Md3Dest.Settings) },
                            scroll = scrolls[0]
                        )
                        Md3Dest.Discover -> Md3DiscoverScreen(
                            onOpenPlaylist = { id, name -> push(Md3Dest.Playlist(id, name)) },
                            onOpenAlbum = { id, name -> push(Md3Dest.Album(id, name)) },
                            onOpenSong = { push(Md3Dest.Song(it)) },
                            scroll = scrolls[1]
                        )
                        Md3Dest.Library -> Md3LibraryScreen(
                            onOpenPlaylist = { id, name -> push(Md3Dest.Playlist(id, name)) },
                            onOpenSong = { push(Md3Dest.Song(it)) },
                            onOpenSettings = { push(Md3Dest.Settings) },
                            scroll = scrolls[2]
                        )
                        Md3Dest.Stats -> Md3StatsScreen(
                            onOpenSettings = { push(Md3Dest.Settings) },
                            scroll = scrolls[3]
                        )
                        Md3Dest.Search -> Md3SearchScreen(
                            onOpenSong = { push(Md3Dest.Song(it)) },
                            onOpenPlaylist = { id, name -> push(Md3Dest.Playlist(id, name)) },
                            onOpenArtist = { id, name -> push(Md3Dest.Artist(id, name)) },
                            onOpenAlbum = { id, name -> push(Md3Dest.Album(id, name)) },
                            scroll = scrolls[4]
                        )
                        is Md3Dest.Song -> Md3SongScreen(
                            songId = dest.id,
                            onBack = ::pop,
                            onOpenPlayer = { push(Md3Dest.Player) },
                            onOpenArtist = { id, name -> push(Md3Dest.Artist(id, name)) },
                            onOpenAlbum = { id, name -> push(Md3Dest.Album(id, name)) },
                            onOpenQueue = { showQueue = true }
                        )
                        is Md3Dest.Playlist -> Md3PlaylistScreen(
                            id = dest.id,
                            fallbackName = dest.name,
                            onBack = ::pop,
                            onOpenSong = { push(Md3Dest.Song(it)) },
                            onOpenArtist = { id, name -> push(Md3Dest.Artist(id, name)) }
                        )
                        is Md3Dest.Album -> Md3AlbumScreen(
                            id = dest.id,
                            fallbackName = dest.name,
                            onBack = ::pop,
                            onOpenSong = { push(Md3Dest.Song(it)) },
                            onOpenArtist = { id, name -> push(Md3Dest.Artist(id, name)) }
                        )
                        is Md3Dest.Artist -> Md3ArtistScreen(
                            id = dest.id,
                            fallbackName = dest.name,
                            onBack = ::pop,
                            onOpenSong = { push(Md3Dest.Song(it)) },
                            onOpenAlbum = { id, name -> push(Md3Dest.Album(id, name)) }
                        )
                        Md3Dest.Settings -> Md3SettingsScreen(
                            config = config,
                            onBack = ::pop,
                            onOpenSelfCheck = { push(Md3Dest.SelfCheck) },
                            onRestart = { restartApp() }
                        )
                        Md3Dest.SelfCheck -> Md3SelfCheckScreen(onBack = ::pop)
                        Md3Dest.Player -> Md3NowPlayingScreen(
                            onBack = ::pop,
                            onOpenQueue = { showQueue = true },
                            onOpenSong = { push(Md3Dest.Song(it)) }
                        )
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
                listOf(SelfCheckItem("自检中断", false, it.message ?: "未知错误", SelfCheckLevel.Fail, "运行环境"))
            }
            elapsed = started.elapsedNow().inWholeMilliseconds
            running = false
        }
    }

    val fails = items.count { it.level == SelfCheckLevel.Fail }
    val warns = items.count { it.level == SelfCheckLevel.Warn }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, top = 44.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(
                        androidx.compose.material.icons.Icons.Rounded.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Column {
                    Text("软件全面自检", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "逐项检查环境、网络、Node 与缓存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { run() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when {
                            running -> "自检中…"
                            items.isEmpty() -> "开始自检"
                            else -> "重新自检"
                        }
                    )
                }
                if (items.isNotEmpty() && !running) {
                    androidx.compose.material3.OutlinedButton(onClick = {
                        val text = buildString {
                            appendLine("ASMusic 自检（Material 3 界面）")
                            items.groupBy { it.group }.forEach { (g, rows) ->
                                appendLine("[$g]")
                                rows.forEach { appendLine("  ${it.level}  ${it.label}：${it.detail}") }
                            }
                        }
                        com.kyant.backdrop.catalog.appstore.Platform.bridge.copy(text)
                        bridge.showToast("已复制")
                    }) { Text("复制") }
                }
            }
        }
        if (items.isNotEmpty()) {
            item {
                Text(
                    "共 ${items.size} 项 · 失败 $fails · 提示 $warns · 耗时 ${elapsed}ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            items(items) { item ->
                val color = when (item.level) {
                    SelfCheckLevel.Pass -> MaterialTheme.colorScheme.primary
                    SelfCheckLevel.Warn -> androidx.compose.ui.graphics.Color(0xFFF2A20C)
                    SelfCheckLevel.Fail -> MaterialTheme.colorScheme.error
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color)
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
