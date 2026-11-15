@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kyant.backdrop.catalog.md3

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.music.LyricLine
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import com.kyant.backdrop.catalog.appstore.music.PlayMode
import com.kyant.backdrop.catalog.appstore.music.SongQuality
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 全屏播放页（Material 3 Expressive）。
 *
 * 这一版按 Expressive 的组件语言重做：
 * - 播放键是形状 morph 的：静止 44dp 圆 → 按下收成 24dp 圆角，图标跟着交叉淡入；
 * - 上一首/下一首组成 standard button group，按下时组内间距从 8 收到 2（官方行为）；
 * - 播放模式与「喜欢」用 split button：左边执行、右边展开其它动作；
 * - 进度条是波形（Expressive 的 wavy progress），拖动时定住不动；
 * - 次要操作收在 floating toolbar 里，可折叠；
 * - 整页支持下拉关闭（拖动跟随 + 松手回弹）。
 */
@Composable
fun Md3NowPlayingScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSong: (Long) -> Unit
) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    val tokens = LocalMd3Tokens.current
    val config = tokens.config
    val scope = rememberCoroutineScope()

    val song = state.currentSong
    var lyrics by remember(song?.id) { mutableStateOf<List<LyricLine>>(emptyList()) }
    var lyricsLoading by remember(song?.id) { mutableStateOf(true) }
    var showLyrics by remember { mutableStateOf(config?.playerStartLyrics ?: false) }
    var showQuality by remember { mutableStateOf(false) }
    var qualities by remember(song?.id) { mutableStateOf<List<SongQuality>>(emptyList()) }

    // 下拉关闭：拖动时跟手，松手按位移阈值决定关闭或回弹
    var dragOffset by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(
        targetValue = if (dragging) dragOffset else 0f,
        animationSpec = tokens.spring,
        label = "drag-offset"
    )

    LaunchedEffect(song?.id) {
        val id = song?.id ?: return@LaunchedEffect
        lyricsLoading = true
        lyrics = runCatching { bridge.lyric(id) }.getOrDefault(emptyList())
        lyricsLoading = false
        qualities = runCatching { bridge.songQualities(id) }.getOrDefault(emptyList())
    }

    val liked = song != null && state.likedIds.contains(song.id)
    val breath = if (config?.breathingCover != false) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "breath")
        transition.animateFloat(
            initialValue = .972f,
            targetValue = 1.012f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(5600),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "breath-scale"
        ).value
    } else 1f
    val coverScale = if (state.playing) breath else 1f

    Box(
        Modifier
            .fillMaxSize()
            .offsetY(animatedOffset)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        if (dragOffset > 160.dp.toPx()) {
                            dragOffset = 0f
                            onBack()
                        } else {
                            dragOffset = 0f
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        dragOffset = 0f
                    }
                ) { _, delta ->
                    // 只允许往下拉，且越拉越沉
                    dragOffset = (dragOffset + delta * .85f).coerceIn(0f, size.height * .8f)
                }
            }
    ) {
        // ---- 背景：封面 + 渐变 ----
        Md3CoverBackdrop(song?.coverUrl, Modifier.fillMaxSize(), darken = .62f)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = .08f),
                            MaterialTheme.colorScheme.surface.copy(alpha = .62f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // ---- 顶栏 ----
            Row(
                Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Md3IconButton(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "收起播放页",
                    onClick = onBack,
                    iconSize = 26.dp
                )
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "正在播放",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (state.playQueue.size > 1) "队列 ${state.queueIndex + 1}/${state.playQueue.size}" else "单曲",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f)
                    )
                }
                // 音质：split button（左执行=打开音质表，右展开=直连/代理信息）
                Md3SplitButton(
                    text = state.currentQuality?.shortLabel ?: qualityShortLabel(state.wifiQuality),
                    onClick = { if (qualities.isNotEmpty()) showQuality = true },
                    actions = qualities.map { q ->
                        (q.label + if (q.playable) "" else "（会员）") to {
                            bridge.setPlaybackQuality(q.level)
                            bridge.showToast("已切到 ${q.label}")
                        }
                    },
                    size = Md3Size.XS,
                    tonal = true
                )
            }

            // ---- 封面 ----
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Md3Cover(
                    song?.coverUrl,
                    Modifier
                        .fillMaxWidth(.9f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = coverScale
                            scaleY = coverScale
                        }
                        .shadow(
                            elevation = 28.dp,
                            shape = MaterialTheme.shapes.extraLarge,
                            ambientColor = MaterialTheme.colorScheme.scrim,
                            spotColor = MaterialTheme.colorScheme.scrim
                        ),
                    shape = MaterialTheme.shapes.extraLarge
                )
                if (state.loading) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = .7f)),
                        contentAlignment = Alignment.Center
                    ) { Md3LoadingIndicator() }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---- 标题 + 喜欢 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song?.name ?: "没有正在播放的歌曲",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            song?.artist.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(CircleShape)
                                .md3SurfaceClick(
                                    onClick = { song?.let { onOpenSong(it.id) } },
                                    shape = CircleShape,
                                    scaleTo = .98f
                                )
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        )
                        if (song?.album?.isNotBlank() == true) {
                            Text(
                                " · ${song.album}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Md3SplitButton(
                    text = if (liked) "已喜欢" else "喜欢",
                    onClick = {
                        val id = song?.id ?: return@Md3SplitButton
                        scope.launch {
                            runCatching { bridge.likeSong(id, !liked) }
                        }
                    },
                    actions = listOf(
                        (if (liked) "取消喜欢" else "喜欢这首歌") to {
                            val id = song?.id
                            if (id != null) scope.launch { runCatching { bridge.likeSong(id, !liked) } }
                        },
                        "分享歌词海报" to {
                            song?.let { bridge.shareLyricPoster(it, null, null) }
                        },
                        "查看歌曲详情" to { song?.let { onOpenSong(it.id) } }
                    ),
                    icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    size = Md3Size.XS,
                    tonal = true,
                    trailingLabel = "喜欢的更多操作"
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---- 进度（波形 / 直线可切） ----
            Md3WavySeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                playing = state.playing,
                onSeek = { bridge.seek(it) },
                wavy = config?.seekStyle != SeekStyle.Linear
            )

            Spacer(Modifier.height(6.dp))

            // ---- 传输控制：三段等宽，播放键组真正居中 ----
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    // 播放模式：split button，左侧直接换模式、右侧展开其它模式
                    Md3SplitButton(
                        text = when (state.playMode) {
                            PlayMode.Shuffle -> "随机"
                            PlayMode.RepeatOne -> "单曲"
                            PlayMode.LoopAll -> "循环"
                            else -> "顺序"
                        },
                        onClick = {
                            bridge.setPlayMode(
                                when (state.playMode) {
                                    PlayMode.LoopAll -> PlayMode.Shuffle
                                    PlayMode.Shuffle -> PlayMode.RepeatOne
                                    else -> PlayMode.LoopAll
                                }
                            )
                        },
                        actions = listOf(
                            "列表循环" to { bridge.setPlayMode(PlayMode.LoopAll) },
                            "单曲循环" to { bridge.setPlayMode(PlayMode.RepeatOne) },
                            "随机播放" to { bridge.setPlayMode(PlayMode.Shuffle) }
                        ),
                        icon = when (state.playMode) {
                            PlayMode.Shuffle -> Icons.Rounded.Shuffle
                            PlayMode.RepeatOne -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        size = Md3Size.XS,
                        tonal = true
                    )
                }

                // 上一首 / 下一首：standard button group（按下时组内间距收拢）
                Md3ButtonGroup(spacing = 12.dp) {
                    Md3IconButton(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        onClick = { bridge.previous() },
                        size = 52.dp,
                        iconSize = 26.dp,
                        tonal = true
                    )
                    Md3IconButton(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        onClick = { bridge.next() },
                        size = 52.dp,
                        iconSize = 26.dp,
                        tonal = true
                    )
                }

                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Md3IconButton(
                        Icons.Rounded.QueueMusic,
                        contentDescription = "播放队列",
                        onClick = onOpenQueue,
                        size = 44.dp,
                        iconSize = 20.dp,
                        tonal = true
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- 播放键：形状 morph ----
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Md3PlayFab(
                    playing = state.playing,
                    enabled = song != null,
                    onClick = { if (song != null) bridge.toggle() }
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---- 悬浮工具栏：次要操作 ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Md3FloatingToolbar(
                    actions = listOf(
                        Md3ToolbarAction(
                            label = "歌词",
                            icon = Icons.Rounded.Lyrics,
                            onClick = { showLyrics = true },
                            selected = showLyrics
                        ),
                        Md3ToolbarAction(
                            label = "歌词海报",
                            icon = Icons.Rounded.Share,
                            onClick = { song?.let { bridge.shareLyricPoster(it, null, null) } }
                        ),
                        Md3ToolbarAction(
                            label = "音质",
                            icon = Icons.Rounded.GraphicEq,
                            onClick = { if (qualities.isNotEmpty()) showQuality = true }
                        )
                    ),
                    startExpanded = true
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // ---- 歌词：从底部推上来，不挤压播放页布局 ----
    if (showLyrics) {
        ModalBottomSheet(
            onDismissRequest = { showLyrics = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ) {
            Md3LyricsPanel(
                lyrics = lyrics,
                loading = lyricsLoading,
                positionMs = state.positionMs,
                showTranslation = config?.showLyricTranslation ?: true,
                onSeek = { bridge.seek(it) }
            )
        }
    }

    // ---- 音质 ----
    if (showQuality) {
        ModalBottomSheet(onDismissRequest = { showQuality = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("选择音质", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.directMode) "当前为直连模式" else "当前走代理",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                qualities.forEach { q ->
                    val selected = state.currentQuality?.level == q.level
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .md3SurfaceClick(
                                onClick = {
                                    bridge.setPlaybackQuality(q.level)
                                    showQuality = false
                                    bridge.showToast("已切到 ${q.label}")
                                },
                                shape = MaterialTheme.shapes.medium,
                                scaleTo = .99f
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                q.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (q.playable) "${q.bitrate} kbps" else "${q.bitrate} kbps · 需要会员",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Md3Badge("当前", MaterialTheme.colorScheme.primaryContainer)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

/**
 * 播放键。Expressive 的形状 morph：静止是正圆，按下收成 24dp 圆角并缩到 0.92；
 * 播放/暂停切换走 scale + fade，而不是直接换图标（避免"闪一下"）。
 */
@Composable
private fun Md3PlayFab(playing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tokens = LocalMd3Tokens.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 26.dp else 44.dp,
        animationSpec = tokens.springDp,
        label = "play-corner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) .93f else 1f,
        animationSpec = tokens.spring,
        label = "play-scale"
    )
    Box(
        Modifier
            .size(88.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.primary)
            .md3SurfaceClick(
                onClick = onClick,
                shape = RoundedCornerShape(corner),
                enabled = enabled,
                scaleTo = 1f,
                layerColor = MaterialTheme.colorScheme.onPrimary
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = {
                (scaleIn(initialScale = .5f) + fadeIn()) togetherWith (scaleOut(targetScale = .5f) + fadeOut())
            },
            label = "play-icon"
        ) { isPlaying ->
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

/** 歌词面板：当前行高亮 + 自动居中 + 点击跳转。 */
@Composable
fun Md3LyricsPanel(
    lyrics: List<LyricLine>,
    loading: Boolean,
    positionMs: Long,
    showTranslation: Boolean,
    onSeek: (Long) -> Unit
) {
    if (loading) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Md3LoadingIndicator()
        }
        return
    }
    if (lyrics.isEmpty()) {
        Md3EmptyState(
            icon = Icons.Rounded.Lyrics,
            title = "暂无歌词",
            desc = "这首歌还没有时间轴歌词"
        )
        return
    }
    val active = lyrics.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(active) {
        if (active > 0) runCatching { listState.animateScrollToItem(active) }
    }
    Column(Modifier.fillMaxWidth().height(430.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("歌词", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${lyrics.size} 行 · 点任意行跳转",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 120.dp)
        ) {
            itemsIndexed(lyrics) { i, line ->
                val isActive = i == active
                val alpha by animateFloatAsState(
                    targetValue = if (isActive) 1f else .42f,
                    animationSpec = tokensSpring(),
                    label = "line-alpha"
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp)
                        .clip(MaterialTheme.shapes.small)
                        .md3SurfaceClick(onClick = { onSeek(line.timeMs) }, scaleTo = .99f)
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        cleanLyric(line.text).ifBlank { "♪" },
                        style = if (isActive) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyLarge,
                        color = (if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface).copy(alpha = alpha),
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (showTranslation && !line.translation.isNullOrBlank()) {
                        Text(
                            cleanLyric(line.translation!!),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (isActive) .8f else .36f
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun tokensSpring() = LocalMd3Tokens.current.spring
/** 垂直偏移（下拉跟手用）。 */
private fun Modifier.offsetY(dy: Float): Modifier =
    this.then(
        Modifier.graphicsLayer { translationY = dy }
    )

/** 给整页用的偏移版本（IntOffset 便于和手势配合）。 */
@Suppress("unused")
private fun Modifier.offsetPx(dy: Float): Modifier = this.then(
    Modifier.then(
        Modifier.graphicsLayer { translationY = dy.roundToInt().toFloat() }
    )
)
