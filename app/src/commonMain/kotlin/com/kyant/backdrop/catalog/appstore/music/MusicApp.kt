package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.CircleButton
import com.kyant.backdrop.catalog.appstore.CircleKind
import com.kyant.backdrop.catalog.appstore.SettingsSheet
import com.kyant.backdrop.catalog.appstore.Toast
import com.kyant.backdrop.catalog.appstore.Txt
import com.kyant.backdrop.catalog.appstore.tap
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LocalLiquidBottomTabDragged
import com.kyant.backdrop.catalog.utils.BackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MusicTabLabels = listOf("今天", "新歌", "我的", "统计", "搜索")

// ---- 导航栈元素 ----
// 页面用 "<kind>:<payload>" 编码（冒号是页面标记，覆盖层名字里没有冒号）。
private const val NavSettings = "set"
private const val NavQr = "qr"
private const val NavScript = "script"
private const val NavPicker = "picker"
private const val NavCache = "cache"
private const val NavGoal = "goal"
private const val NavAlbumInfo = "ainfo"
private const val NavArtistInfo = "rinfo"
private const val NavComments = "comments"
private const val NavCommentDetail = "cmtd"
private const val NavQualityPrefs = "qprefs"
private const val NavQueue = "queue"
private const val NavSongQuality = "qsong"
private const val NavImmersive = "immersive"

/**
 * true 表示这是一个整页路由（s: 歌曲 / p: 歌单 / a: 专辑 / r: 艺人 / f: 设置子页），
 * false 表示覆盖层（set / qr / cache …）。
 *
 * "c:"（旧的自定义二级页）已不再产生；显式排除它，好让升级前保存下来的旧栈
 * 不会把一个不存在的页面当成当前页。
 */
private fun String.isNavPage(): Boolean =
    length > 1 && this[1] == ':' &&
        (startsWith("s:") || startsWith("p:") || startsWith("a:") ||
            startsWith("r:") || startsWith("f:"))

@Composable
private fun <T> PageTransition(
    targetState: T,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            (fadeIn(tween(320)) + scaleIn(initialScale = 0.92f, animationSpec = tween(360))) togetherWith
                (fadeOut(tween(260)) + scaleOut(targetScale = 0.92f, animationSpec = tween(300)))
        },
        label = "page-transition"
    ) { target ->
        val blur by transition.animateFloat(
            transitionSpec = { tween(360) },
            label = "page-blur"
        ) { state ->
            when (state) {
                EnterExitState.PreEnter -> 12f
                EnterExitState.Visible -> 0f
                EnterExitState.PostExit -> 12f
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .blur(blur.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        ) {
            content(target)
        }
    }
}

@Composable
fun MusicApp() {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    val backdrop = rememberLayerBackdrop()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 单一导航栈：页面（"s:<id>" / "p:<id>" / "a:<id>" / "r:<id>" / "c:" / "f:<group>"）
    // 与覆盖层（NavSettings / NavQr / …）共用一个 List。
    //
    // 这样「返回」永远等于 pop()，不再需要在每个入口手动记住"从哪来"：
    // 设置 → 三级页 只是把 f: 压在 set 上面，返回时 set 自然重新成为栈顶并显示。
    // 覆盖层的 visible 全部由栈顶派生，因此各 sheet 的签名和行为保持不变。
    var nav by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    val topPage = nav.lastOrNull { it.isNavPage() }
    val topLayer = nav.lastOrNull()

    // 仅用于性能日志：把当前页面名写进状态，帧监控日志里能看出是哪个页面掉的帧。
    LaunchedEffect(tab, topPage) {
        state.debugPage = when {
            topPage == null -> listOf("today", "new", "mine", "stats", "search").getOrElse(tab) { "tab" }
            topPage.startsWith("s:") -> "song"
            topPage.startsWith("p:") -> "playlist"
            topPage.startsWith("a:") -> "album"
            topPage.startsWith("r:") -> "artist"
            topPage.startsWith("f:") -> "finetune"
            else -> "tab"
        }
    }

    // ---- 覆盖层可见性：全部由栈顶派生，保持 LIFO ----
    val settingsOpen = topLayer == NavSettings
    val qrOpen = topLayer == NavQr
    val scriptOpen = topLayer == NavScript
    val pickerOpen = topLayer == NavPicker
    val cacheOpen = topLayer == NavCache
    val goalOpen = topLayer == NavGoal
    val albumIntroOpen = topLayer == NavAlbumInfo
    val artistIntroOpen = topLayer == NavArtistInfo
    val commentsOpen = topLayer == NavComments
    val commentDetailOpen = topLayer == NavCommentDetail
    val qualitySettingsOpen = topLayer == NavQualityPrefs
    val queueOpen = topLayer == NavQueue
    val songQualityOpen = topLayer == NavSongQuality
    val immersiveOpen = topLayer == NavImmersive

    var albumIntro by remember { mutableStateOf<Pair<String, String>?>(null) }
    var artistIntro by remember { mutableStateOf<Pair<String, String>?>(null) }
    var commentsSongId by remember { mutableStateOf<Long?>(null) }
    var commentsTotal by remember { mutableStateOf(0) }
    var commentDetail by remember { mutableStateOf<CommentItem?>(null) }
    var immersiveSong by remember { mutableStateOf<Song?>(null) }
    var immersiveLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }

    val todayScroll = rememberScrollState()
    val newScroll = androidx.compose.foundation.lazy.rememberLazyListState()
    val mineScroll = rememberScrollState()
    val statsScroll = rememberScrollState()
    val searchScroll = rememberScrollState()
    val detailScroll = rememberScrollState()
    val playlistScroll = androidx.compose.foundation.lazy.rememberLazyListState()
    val artistScroll = rememberScrollState()
    val fineTuneScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()
    val artistNames = remember { HashMap<Long, String>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(1800)
            state.toast = null
        }
    }

    // ---- 导航原语 ----
    fun push(layer: String) {
        if (nav.lastOrNull() == layer) return
        nav = nav + layer
    }

    fun pop() {
        if (nav.isNotEmpty()) nav = nav.dropLast(1)
    }

    fun selectTab(idx: Int) {
        // Any secondary screen is dismissed when the tab bar is used, otherwise it
        // would keep covering the primary page the user just switched to.
        if (nav.isNotEmpty()) {
            nav = emptyList()
            if (idx != tab) tab = idx
            return
        }
        if (idx == tab) {
            scope.launch {
                when (idx) {
                    0 -> todayScroll.animateScrollTo(0)
                    1 -> newScroll.animateScrollToItem(0)
                    2 -> mineScroll.animateScrollTo(0)
                    3 -> statsScroll.animateScrollTo(0)
                    4 -> searchScroll.animateScrollTo(0)
                }
            }
        } else {
            tab = idx
        }
    }

    // Keep the immersive page in sync with the player: auto-next must switch the page
    // to the new song instead of leaving the previous one on screen.
    //
    // Keyed on the *player's* song only: writing `immersiveSong` inside this effect used
    // to change one of its own keys, which cancelled the in-flight `bridge.lyric()`
    // call; the CancellationException was swallowed by runCatching and the page fell
    // back to the fixture lyric (the same "test lyrics" for every song).
    val openImmersive = if (immersiveOpen) immersiveSong?.id else null
    LaunchedEffect(state.currentSong?.id, openImmersive) {
        val current = state.currentSong ?: return@LaunchedEffect
        if (openImmersive == null || openImmersive == current.id) return@LaunchedEffect
        immersiveSong = current
        immersiveLines = emptyList()
        val loaded = withContext(NonCancellable) {
            runCatching { bridge.lyric(current.id) }.getOrNull()
        }
        // Never fall back to fixture lyrics: an empty list makes the page show
        // "暂无歌词" instead of someone else's words.
        immersiveLines = loaded.orEmpty()
    }

    fun openSong(id: Long) {
        val top = nav.lastOrNull()
        if (top == "s:$id") return
        // Replace an already-open song page instead of stacking details; this is what
        // makes switching songs refresh the page in place.
        nav = if (top != null && top.startsWith("s:")) {
            nav.dropLast(1) + "s:$id"
        } else {
            nav + "s:$id"
        }
        scope.launch { detailScroll.scrollTo(0) }
    }

    /**
     * Picking a song from a list opens its detail page **and** starts playing it with
     * the list as the new up-next queue. Tapping the song that is already loaded only
     * opens the page — the playback position is never reset.
     */
    fun openSongFromList(list: List<Song>, song: Song) {
        // playFrom keeps the existing progress when this is already the loaded song:
        // it only replaces the up-next queue (and resumes if the song was paused).
        bridge.playFrom(list, song)
        openSong(song.id)
    }

    fun openPlaylist(id: Long) {
        nav = nav + "p:$id"
        scope.launch { playlistScroll.scrollToItem(0) }
    }

    fun openAlbum(id: Long) {
        if (id <= 0L) return
        nav = nav + "a:$id"
        scope.launch { playlistScroll.scrollToItem(0) }
    }

    fun openArtist(id: Long, name: String) {
        if (id <= 0L) return
        artistNames[id] = name
        nav = nav + "r:$id"
        scope.launch { artistScroll.scrollTo(0) }
    }

    fun openFineTune(group: FineTuneGroup) {
        nav = nav + "f:${group.name}"
        scope.launch { fineTuneScroll.scrollTo(0) }
    }

    // Follow playback changes while a song page is open (next/previous, queue jump,
    // auto-advance). Without this the page kept displaying the song it was opened on.
    LaunchedEffect(state.currentSong?.id) {
        val id = state.currentSong?.id ?: return@LaunchedEffect
        val top = nav.lastOrNull() ?: return@LaunchedEffect
        if (!top.startsWith("s:")) return@LaunchedEffect
        val shown = top.removePrefix("s:").toLongOrNull()
        if (shown != id) {
            nav = nav.dropLast(1) + "s:$id"
            detailScroll.scrollTo(0)
        }
    }

    /**
     * Share button on the song detail page: renders the lyric poster (cover-sampled
     * aurora background + the line currently being sung) and opens the share sheet.
     */
    fun shareSong(id: Long) {
        scope.launch {
            val song = if (state.currentSong?.id == id) state.currentSong
            else runCatching { bridge.songDetail(id) }.getOrNull()
            if (song == null) {
                bridge.showToast("歌曲信息不可用")
                return@launch
            }
            val lines = runCatching { bridge.lyric(id) }.getOrDefault(emptyList())
            val active = if (state.currentSong?.id == id) {
                lines.lastOrNull { it.timeMs <= state.positionMs }
            } else {
                lines.firstOrNull()
            }
            // Hand the poster the current line plus the following ones: the
            // poster's "歌词行数" setting decides how many of them are drawn.
            val startIndex = lines.indexOfFirst { it === active }.coerceAtLeast(0)
            val block = lines.drop(startIndex).take(4)
                .joinToString("\n") { it.text }
                .ifBlank { active?.text.orEmpty() }
            bridge.shareLyricPoster(song, block, active?.translation)
        }
    }

    Box(Modifier.fillMaxSize().background(AS.Bg)) {
        // The song detail page paints content under the status bar so the Haze panel
        // can blur it there; other pages keep the normal inset.
        val underStatusBar = topPage?.startsWith("s:") == true
        Box(
            Modifier
                .fillMaxSize()
                .then(if (underStatusBar) Modifier else Modifier.statusBarsPadding())
                // The page background must live inside the recorded layer: the glass
                // samples this layer, and a transparent layer would let the crisp page
                // show through the (blurred) glass.
                .layerBackdrop(backdrop)
                .background(AS.Bg)
        ) {
            val tabExit by animateFloatAsState(
                targetValue = if (topPage != null) 1f else 0f,
                animationSpec = tween(320),
                label = "tab-exit"
            )
            // Once a secondary page fully covers the tab content there is no reason to
            // keep composing it: its lists, marquees and glass layers would keep
            // running behind an opaque page. Wait for the fade-out to finish first so
            // the transition still looks identical.
            var tabComposed by remember { mutableStateOf(true) }
            LaunchedEffect(topPage != null) {
                if (topPage == null) {
                    tabComposed = true
                } else {
                    delay(360)
                    tabComposed = false
                }
            }
            if (tabComposed) Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f - 0.08f * tabExit
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - tabExit
                    }
                    .blur((12f * tabExit).dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            ) {
                PageTransition(tab) { tabIndex ->
                    when (tabIndex) {
                        0 -> TodayPage(todayScroll, ::openPlaylist, ::openSong, { push(NavSettings) }, { push(NavPicker) }, backdrop)
                        1 -> NewReleasesPage(newScroll, ::openPlaylist, ::openAlbum, ::openSongFromList, { push(NavSettings) })
                        2 -> MyPlaylistsPage(mineScroll, ::openPlaylist, ::openSongFromList, { push(NavSettings) })
                        3 -> StatsPage(statsScroll, { push(NavSettings) }, { push(NavGoal) })
                        4 -> MusicSearchPage(searchScroll, ::openSongFromList, ::openPlaylist, { push(NavSettings) })
                    }
                }
            }

            PageTransition(topPage) { top ->
            when {
                    top != null && top.startsWith("s:") -> {
                        val sid = top.removePrefix("s:").toLongOrNull()
                        if (sid != null) {
                            SongDetailPage(
                                songId = sid,
                                scrollState = detailScroll,
                                onBack = ::pop,
                                onShare = { shareSong(sid) },
                                onOpenAlbum = ::openAlbum,
                                onOpenArtist = { id, name -> openArtist(id, name) },
                                onOpenComments = { total ->
                                    commentsTotal = total
                                    commentsSongId = sid
                                    push(NavComments)
                                },
                                onOpenComment = {
                                    commentDetail = it
                                    push(NavCommentDetail)
                                },
                                onOpenImmersive = { song, lyrics ->
                                    immersiveSong = song
                                    immersiveLines = lyrics
                                    push(NavImmersive)
                                }
                            )
                        }
                    }
                    top != null && top.startsWith("p:") -> {
                        val pid = top.removePrefix("p:").toLongOrNull()
                        if (pid != null) {
                            PlaylistDetailPage(
                                pid, playlistScroll, ::pop, ::openSongFromList,
                                onAvatar = { push(NavSettings) }
                            )
                        }
                    }
                    top != null && top.startsWith("a:") -> {
                        val aid = top.removePrefix("a:").toLongOrNull()
                        if (aid != null) {
                            PlaylistDetailPage(
                                aid, playlistScroll, ::pop, ::openSongFromList,
                                onAvatar = { push(NavSettings) }, isAlbum = true,
                                onOpenAlbumIntro = { title, text ->
                                    albumIntro = title to text
                                    push(NavAlbumInfo)
                                }
                            )
                        }
                    }
                    top != null && top.startsWith("r:") -> {
                        val rid = top.removePrefix("r:").toLongOrNull()
                        if (rid != null) {
                            ArtistPage(
                                artistId = rid,
                                artistName = artistNames[rid] ?: "艺术家",
                                scrollState = artistScroll,
                                onBack = ::pop,
                                onOpenSong = ::openSongFromList,
                                onOpenIntro = { title, text ->
                                    artistIntro = title to text
                                    push(NavArtistInfo)
                                },
                                onOpenAlbum = ::openAlbum
                            )
                        }
                    }
                    top != null && top.startsWith("f:") -> {
                        val group = FineTuneGroup.entries
                            .firstOrNull { it.name == top.removePrefix("f:") }
                            ?: FineTuneGroup.Appearance
                        FineTunePage(group, fineTuneScroll, ::pop)
                    }
                }
            }
        }

        // Playback capsule floats above the liquid-glass tab bar, docked left or right.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val current = state.currentSong
            // On the song detail page the capsule stays and grows into a full-width
            // transport bar (progress + prev / play / next).
            val detailOpen = topPage?.startsWith("s:") == true
            var capsuleShown by remember { mutableStateOf(false) }
            LaunchedEffect(current != null) { capsuleShown = current != null }
            val capsuleBlur by animateFloatAsState(
                targetValue = if (capsuleShown) 0f else 12f,
                animationSpec = tween(420),
                label = "capsule-blur"
            )
            AnimatedVisibility(
                visible = current != null,
                enter = fadeIn(animationSpec = tween(360)) +
                    scaleIn(initialScale = 0.64f, animationSpec = spring(dampingRatio = .78f, stiffness = 380f)),
                exit = fadeOut(animationSpec = tween(220)) +
                    scaleOut(targetScale = 0.64f, animationSpec = tween(260, easing = CubicBezierEasing(.32f, .72f, 0f, 1f)))
            ) {
                if (current != null) {
                    Column(
                        Modifier.blur(capsuleBlur.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    ) {
                        CapsuleDock(
                            current,
                            backdrop,
                            expanded = detailOpen,
                            onOpen = { openSong(current.id) },
                            onOpenQueue = { push(NavQueue) },
                            onOpenQuality = { push(NavSongQuality) }
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
            val onDetail = topPage?.startsWith("s:") == true
            val hideTabs = onDetail && state.detailHideTabs
            val tabsAlpha by animateFloatAsState(
                targetValue = if (hideTabs) 0f else 1f,
                animationSpec = tween(260),
                label = "tabs-alpha"
            )
            val tabsBlur by animateDpAsState(
                targetValue = if (hideTabs) 14.dp else 0.dp,
                animationSpec = tween(260),
                label = "tabs-blur"
            )
            // The tab bar keeps its exit animation but releases its layout space the
            // moment the detail page opens, so the capsule grows straight into its
            // final position instead of jumping down afterwards.
            Box(
                Modifier
                    .fillMaxWidth()
                    // Report zero height the instant the detail page opens, while the
                    // bar itself keeps drawing (and fading) below its old slot. That
                    // removes the layout gap immediately, so the capsule grows straight
                    // into its final position instead of sliding down afterwards.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = 0, maxHeight = Int.MAX_VALUE)
                        )
                        val reported = if (hideTabs) 0 else placeable.height
                        layout(placeable.width, reported) {
                            placeable.placeRelative(0, 0)
                        }
                    }
            ) {
            if (tabsAlpha > 0f) LiquidBottomTabs(
                selectedTabIndex = { tab },
                onTabSelected = { selectTab(it) },
                backdrop = backdrop,
                tabsCount = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = tabsAlpha
                        val scale = 0.92f + 0.08f * tabsAlpha
                        scaleX = scale
                        scaleY = scale
                        // Anchor at the slot top so the bar fades in place instead of
                        // collapsing upward when the slot reports zero height.
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .blur(tabsBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            ) {
                repeat(5) { i ->
                    LiquidBottomTab({ selectTab(i) }) {
                        // The selector control remains blue; while it is held or
                        // dragged, its icon/text turns white instead of staying blue.
                        val dragging = LocalLiquidBottomTabDragged.current
                        MusicTabIcon(
                            i,
                            if (tab == i && !dragging) AS.Blue else Color.White.copy(alpha = .78f),
                            size = 24.dp
                        )
                        BasicText(
                            MusicTabLabels[i],
                            style = AS.tabLabel(tab == i && !dragging)
                        )
                    }
                }
            }
            }
        }

        // toast 由 LaunchedEffect 在 1.8s 后清空，读两次之间可能变 null，不能断言非空。
        state.toast?.let { message ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 170.dp)) {
                Toast(message)
            }
        }

        SettingsSheet(
            visible = settingsOpen,
            scrollState = settingsScroll,
            // 从设置进入子页只需压栈：设置本身留在栈里，返回时自动重新成为栈顶，
            // 因此不再需要 settingsReturn / closeSettingsChild 这类"从哪来"的补丁。
            onOpenFineTune = ::openFineTune,
            onDismiss = ::pop,
            onNeteaseLogin = { push(NavQr) },
            onLogout = {
                scope.launch {
                    bridge.logout()
                    bridge.showToast("已退出登录")
                }
            },
            onScriptImport = { push(NavScript) },
            onCache = { push(NavCache) },
            onQuality = { push(NavQualityPrefs) }
        )
        // Always composed so the sheet's own slide/fade runs on both open and close;
        // the content is kept while it animates out.
        AlbumIntroSheet(
            visible = albumIntroOpen,
            title = albumIntro?.first ?: "",
            text = albumIntro?.second ?: "",
            heading = "专辑简介",
            onDismiss = ::pop
        )
        AlbumIntroSheet(
            visible = artistIntroOpen,
            title = artistIntro?.first ?: "",
            text = artistIntro?.second ?: "",
            heading = "艺人简介",
            onDismiss = ::pop
        )
        QrLoginSheet(qrOpen, ::pop)
        ScriptImportSheet(scriptOpen, ::pop)
        TodayCardPicker(pickerOpen, ::pop)
        CacheSheet(cacheOpen, ::pop)
        GoalSheet(goalOpen, ::pop)
        PlayQueueSheet(queueOpen, ::pop)
        CommentsSheet(
            songId = commentsSongId,
            total = commentsTotal,
            visible = commentsOpen,
            backdrop = backdrop,
            onDismiss = ::pop,
            onOpenComment = {
                commentDetail = it
                push(NavCommentDetail)
            }
        )
        CommentDetailSheet(
            comment = commentDetail,
            visible = commentDetailOpen,
            backdrop = backdrop,
            onDismiss = ::pop
        )
        QualitySheet(songQualityOpen, ::pop)
        QualitySettingsSheet(qualitySettingsOpen, ::pop)

        if (immersiveOpen && immersiveSong != null) {
            ImmersiveLyricsPage()
        }

        // Always composed: keeps the AMLL page (and its Pixi background) warm so the
        // immersive view opens without a visible load. Sits at 1 dp behind everything
        // while closed.
        val surfaceSong = (if (immersiveOpen) immersiveSong else null) ?: state.currentSong
        // A1: keep the AMLL WebView (Pixi textures + JS heap) only while playing or
        // while the immersive page is open; release it 60 s after playback stops.
        var surfaceAllowed by remember { mutableStateOf(false) }
        LaunchedEffect(state.playing, state.currentSong?.id, immersiveOpen, state.keepAmllWarm) {
            if (!state.keepAmllWarm) {
                surfaceAllowed = immersiveOpen
                return@LaunchedEffect
            }
            if (state.playing || immersiveOpen) {
                surfaceAllowed = true
            } else {
                delay(60_000)
                if (!state.playing && !immersiveOpen) surfaceAllowed = false
            }
        }
        if (surfaceSong != null && surfaceAllowed) {
            AmllSurface(
                song = surfaceSong,
                lines = if (immersiveOpen) immersiveLines else emptyList(),
                immersiveOpen = immersiveOpen
            )
        }
    }

    // 唯一的返回入口：任何页面/覆盖层都只是栈里的一项，返回即出栈。
    // 「后开的先关」由栈序保证，不再依赖 when 分支的书写顺序。
    BackHandler(nav.isNotEmpty()) { pop() }
}

/**
 * AMLL 宿主。`positionMs` / `playing` 每 500ms 变一次，读取必须留在这一层：
 * 写在 MusicApp 的根 lambda 里，会让整个根作用域（含全部 sheet、列表与转场）
 * 每 500ms 重组一次，白白吃掉每帧预算。
 */
@Composable
private fun AmllSurface(
    song: Song,
    lines: List<LyricLine>,
    immersiveOpen: Boolean
) {
    val state = MusicServices.state
    val isCurrent = state.currentSong?.id == song.id
    AmlvImmersiveLyrics(
        songId = song.id,
        coverUrl = song.coverUrl,
        lines = lines,
        positionMs = if (isCurrent) state.positionMs else 0L,
        playing = isCurrent && state.playing,
        style = LyricTextStyle(
            fontSize = state.amllFontSize,
            fontWeight = state.amllFontWeight,
            fontFamily = state.amllFontFamily,
            maskProfanity = state.amllMaskProfanity,
            resumeDelaySec = state.amllResumeDelay
        ),
        onSeek = { MusicServices.bridge.seek(it) },
        visible = immersiveOpen,
        modifier = if (immersiveOpen) {
            Modifier.fillMaxSize().zIndex(20f)
        } else {
            Modifier.size(1.dp).zIndex(-1f)
        }
    )
}

/**
 * Docks the playback capsule against the left or right edge and morphs it into the wide
 * control bar on the song detail page. Moving between edges fades / defocuses the inner
 * controls and swaps their order at the midpoint.
 *
 * The capsule can also be **dragged** from one side to the other: while the finger is
 * down it follows horizontally (keeping the same fade / swap / defocus treatment as the
 * animated travel), and on release it springs to whichever edge is closer and the
 * side preference is persisted.
 */
@Composable
private fun CapsuleDock(
    song: Song,
    backdrop: Backdrop,
    expanded: Boolean,
    onOpen: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenQuality: () -> Unit
) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val fullWidth = constraints.maxWidth.toFloat()
        val compactPx = with(density) { 182.dp.toPx() }
        val targetWidth = if (expanded) fullWidth else compactPx
        val width by animateFloatAsState(
            targetValue = targetWidth,
            animationSpec = spring(dampingRatio = .86f, stiffness = 320f),
            label = "capsuleWidth"
        )
        val side = remember { Animatable(if (state.capsuleOnLeft) 0f else 1f) }
        // Root-space left edge, updated every layout. Using this instead of the
        // pointer's local x prevents the capsule's own motion from feeding back
        // into the drag and making it jitter left/right.
        val capsuleLeft = remember { floatArrayOf(0f) }
        var dragging by remember { mutableStateOf(false) }
        // Finger position (0 = left edge, 1 = right edge) while dragging; null otherwise.
        var dragFraction by remember { mutableStateOf<Float?>(null) }
        // External side changes (settings switch) animate the capsule over.
        LaunchedEffect(state.capsuleOnLeft) {
            val target = if (state.capsuleOnLeft) 0f else 1f
            if (!dragging && kotlin.math.abs(target - side.value) > 0.001f) {
                side.animateTo(target, spring(dampingRatio = .80f, stiffness = 290f))
            }
        }
        // Release (or cancel): snap to the closer edge and remember the choice.
        fun snapToEdge() {
            dragging = false
            val from = dragFraction
            val target = if ((from ?: side.value) < 0.5f) 0f else 1f
            val onLeft = target == 0f
            scope.launch {
                // Continue the spring from exactly where the finger left the capsule.
                from?.let { side.snapTo(it) }
                dragFraction = null
                side.animateTo(target, spring(dampingRatio = .80f, stiffness = 290f))
            }
            if (onLeft != state.capsuleOnLeft) bridge.setCapsuleOnLeft(onLeft)
        }
        val fraction = dragFraction ?: side.value
        val x = (fullWidth - width).coerceAtLeast(0f) * fraction
        // 0 at either edge, 1 half way — drives the fade / defocus while travelling.
        val fade = kotlin.math.sin(Math.PI * fraction).toFloat().coerceIn(0f, 1f)

        Box(
            Modifier
                .width(with(density) { width.toDp() })
                .offset { IntOffset(x.roundToInt(), 0) }
                .onGloballyPositioned { capsuleLeft[0] = it.positionInRoot().x }
                .then(
                    if (!expanded) Modifier.pointerInput(fullWidth) {
                        var startFraction = 0f
                        var startRootX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { pos ->
                                dragging = true
                                startFraction = dragFraction ?: side.value
                                startRootX = capsuleLeft[0] + pos.x
                            },
                            onDragEnd = { snapToEdge() },
                            onDragCancel = { snapToEdge() }
                        ) { change, _ ->
                            val travel = (fullWidth - compactPx).coerceAtLeast(1f)
                            val rootX = capsuleLeft[0] + change.position.x
                            dragFraction = (startFraction + (rootX - startRootX) / travel)
                                .coerceIn(0f, 1f)
                        }
                    } else Modifier
                )
        ) {
            PlayCapsule(
                song = song,
                backdrop = backdrop,
                playing = state.playing,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                expanded = expanded,
                onLeft = fraction < 0.5f,
                fade = fade,
                onToggle = { bridge.toggle() },
                onPrevious = { bridge.previous() },
                onNext = { bridge.next() },
                liked = song.id in state.likedIds,
                onLike = {
                    val id = song.id
                    scope.launch {
                        val target = id !in state.likedIds
                        val ok = bridge.likeSong(id, target)
                        bridge.showToast(
                            if (ok) (if (target) "已添加到我喜欢" else "已取消喜欢")
                            else "同步网易云失败"
                        )
                    }
                },
                onSeek = { bridge.seek(it) },
                onOpen = onOpen,
                onQuality = onOpenQuality,
                onOpenQueue = onOpenQueue,
                corner = { state.capsuleCorner }
            )
        }
    }
}

@Composable
private fun PlaylistDetailPage(
    playlistId: Long,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    onBack: () -> Unit,
    onOpenSong: (List<Song>, Song) -> Unit,
    onAvatar: () -> Unit,
    isAlbum: Boolean = false,
    onOpenAlbumIntro: (String, String) -> Unit = { _, _ -> }
) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    var songs by remember(playlistId) { mutableStateOf<List<Song>>(emptyList()) }
    var playlist by remember(playlistId) { mutableStateOf<Playlist?>(null) }
    var loading by remember(playlistId) { mutableStateOf(true) }
    var albumDesc by remember(playlistId) { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId, isAlbum) {
        loading = true
        // Album page: opened from the album name on a song's detail page.
        if (isAlbum) {
            val list = runCatching { bridge.albumSongs(playlistId) }.getOrNull()
                ?: runCatching { bridge.playlistSongs(playlistId) }.getOrNull().orEmpty()
            songs = list
            val head = list.firstOrNull()
            playlist = Playlist(
                id = playlistId,
                name = head?.album?.takeIf { it.isNotBlank() } ?: "专辑",
                subtitle = if (list.isEmpty()) "共 0 首"
                else "${head?.artist ?: "未知艺术家"} · 共 ${list.size} 首",
                coverUrl = head?.coverUrl,
                songCount = list.size,
                kind = PlaylistKind.NewRelease,
                songs = list
            )
            albumDesc = runCatching { bridge.albumDescription(playlistId) }.getOrNull()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        // The Today page cards carry their own metadata + songs. They used to be
        // resolved only by id, which sent fake ids (401/402/406) to the playlist
        // endpoint and returned unrelated tracks.
        val cards = runCatching { bridge.todayCards() }.getOrNull().orEmpty()
        val mine = runCatching { bridge.personalPlaylists() }.getOrNull().orEmpty()
        val releases = runCatching { bridge.newReleases() }.getOrNull().orEmpty()
        val charts = runCatching { bridge.toplists() }.getOrNull().orEmpty()
        val card = cards.firstOrNull { it.id == playlistId }
        val meta = card
            ?: (mine + releases + charts).firstOrNull { it.id == playlistId }
            ?: (Fixtures.todayCards(Fixtures.allTodayKinds) +
                Fixtures.personalPlaylists + Fixtures.newReleases)
                .firstOrNull { it.id == playlistId }

        // Render instantly from what the card already knows.
        if (!card?.songs.isNullOrEmpty()) {
            songs = card.songs
            playlist = card
            loading = false
        }

        val fetched: List<Song>? = when {
            meta == null -> runCatching { bridge.playlistSongs(playlistId) }.getOrNull()
            meta.kind == PlaylistKind.Daily -> runCatching { bridge.dailySongs() }.getOrNull()
            meta.kind == PlaylistKind.PrivateFm -> null
            Fixtures.isVirtualCard(meta.id) -> null
            meta.kind == PlaylistKind.NewRelease ->
                runCatching { bridge.albumSongs(meta.id) }.getOrNull()
                    ?: runCatching { bridge.playlistSongs(meta.id) }.getOrNull()
            else -> runCatching { bridge.playlistSongs(meta.id) }.getOrNull()
        }
        if (!fetched.isNullOrEmpty()) {
            songs = fetched
        } else if (songs.isEmpty()) {
            songs = runCatching { bridge.playlistSongs(playlistId) }.getOrNull().orEmpty()
        }

        val count = songs.size
        val shown = if (count > 0) count else meta?.songCount ?: 0
        playlist = when {
            meta == null -> Playlist(
                id = playlistId,
                name = "歌单",
                subtitle = "共 $shown 首",
                coverUrl = songs.firstOrNull()?.coverUrl,
                songCount = shown,
                kind = PlaylistKind.Custom,
                songs = songs
            )
            else -> meta.copy(
                songs = songs,
                songCount = shown,
                coverUrl = meta.coverUrl ?: songs.firstOrNull()?.coverUrl,
                subtitle = when (meta.kind) {
                    PlaylistKind.Daily -> "根据你的口味生成 · $shown 首"
                    PlaylistKind.PrivateFm -> "私人漫游 · $shown 首"
                    PlaylistKind.NewRelease ->
                        if (Fixtures.isVirtualCard(meta.id)) "本周新上架 · $shown 首"
                        else "共 $shown 首"
                    else -> if (shown > 0) "共 $shown 首" else meta.subtitle
                }
            )
        }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
    androidx.compose.foundation.lazy.LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .background(AS.Bg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 250.dp)
    ) {
        item(key = "header") {
        Column {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            CircleButton(CircleKind.Back, onBack)
        }
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            CoverImage(
                playlist?.coverUrl ?: songs.firstOrNull()?.coverUrl,
                playlistId,
                Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(18.dp))
        if (isAlbum) {
            Txt("ALBUM", AS.eyebrow(), Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(6.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Txt(
                playlist?.name ?: if (isAlbum) "专辑" else "歌单",
                AS.largeTitle().copy(fontSize = 30.sp),
                Modifier.weight(1f),
                maxLines = 2
            )
            if (isAlbum) {
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AS.Card)
                        .tap {
                            onOpenAlbumIntro(
                                playlist?.name ?: "专辑简介",
                                albumDesc.orEmpty()
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Txt("简介", AS.caption().copy(color = AS.Blue))
                }
            }
        }
        Txt(
            playlist?.subtitle ?: if (loading) "正在加载歌单…" else "共 ${songs.size} 首",
            AS.subtitle(),
            Modifier.padding(horizontal = 16.dp),
            maxLines = 1
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(AS.Blue)
                .tap { songs.firstOrNull()?.let { bridge.playFrom(songs, it) } }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayPauseButton(false, { songs.firstOrNull()?.let { bridge.playFrom(songs, it) } }, size = 22.dp, filled = false)
            Spacer(Modifier.width(10.dp))
            Txt("播放全部", AS.body().copy(fontSize = 15.sp, color = Color.White))
        }
        Spacer(Modifier.height(20.dp))
        }
        }
        if (songs.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Txt(if (loading) "正在加载…" else "这张歌单暂时没有可播放的歌曲", AS.caption())
                }
            }
        } else {
            itemsIndexed(songs, key = { _, song -> song.id }, contentType = { _, _ -> "song-row" }) { _, song ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SongRow(
                        song,
                        playing = state.currentSong?.id == song.id && state.playing,
                        onOpen = { onOpenSong(songs, song) },
                        onPlay = { bridge.toggleSong(songs, song) }
                    )
                }
            }
            item(key = "summary") {
                Column {
                    Spacer(Modifier.height(18.dp))
                    Txt(
                        playlistSummary(songs),
                        AS.caption().copy(color = AS.Text3),
                        Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
    }
}

/** Apple Music-style “N 首 · X 小时 Y 分钟” footer under a playlist. */
private fun playlistSummary(songs: List<Song>): String {
    if (songs.isEmpty()) return "0 首"
    val minutes = songs.sumOf { it.durationMs } / 60_000L
    val hours = minutes / 60
    val rest = minutes % 60
    val duration = if (hours > 0) "$hours 小时 $rest 分钟" else "$rest 分钟"
    return "${songs.size} 首 · $duration"
}
