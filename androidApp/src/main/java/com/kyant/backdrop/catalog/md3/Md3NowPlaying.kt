@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kyant.backdrop.catalog.md3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.music.LyricLine
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import com.kyant.backdrop.catalog.appstore.music.PlayMode
import kotlinx.coroutines.launch

/**
 * 全屏播放页。
 *
 * 视觉主线：封面 → 呼吸缩放（播放时缓慢起伏，暂停即停），背后的渐变取同一张封面，
 * 让整页像从封面上长出来的。歌词不是另一个页面，而是从同一张卡片底部推上来，
 * 避免"跳页"打断听感。
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
    val song = state.currentSong
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var lyrics by remember(song?.id) { mutableStateOf<List<LyricLine>>(emptyList()) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }

    LaunchedEffect(song?.id) {
        lyrics = song?.let { runCatching { bridge.lyric(it.id) }.getOrDefault(emptyList()) } ?: emptyList()
    }

    val breath = if (config?.breathingCover != false) {
        val transition = rememberInfiniteTransition(label = "breath")
        transition.animateFloat(
            initialValue = .975f,
            targetValue = 1.015f,
            animationSpec = infiniteRepeatable(tween(5200), RepeatMode.Reverse),
            label = "breath-scale"
        ).value
    } else 1f
    val coverScale = if (state.playing) breath else 1f

    Box(Modifier.fillMaxSize()) {
        // 背景：封面铺满 + 大范围渐变压暗
        Md3CoverBackdrop(song?.coverUrl, Modifier.fillMaxSize(), darken = .55f)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = .15f),
                            MaterialTheme.colorScheme.surface.copy(alpha = .55f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )

        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            // 顶栏
            Row(
                Modifier.fillMaxWidth().padding(top = 44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "收起", modifier = Modifier.size(28.dp))
                }
                Text(
                    "正在播放",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showQuality = true }) {
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = "音质")
                }
            }

            Spacer(Modifier.weight(.02f))

            // 封面
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Md3Cover(
                    song?.coverUrl,
                    Modifier
                        .fillMaxWidth(.86f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = coverScale
                            scaleY = coverScale
                        },
                    shape = MaterialTheme.shapes.extraLarge
                )
            }

            Spacer(Modifier.height(26.dp))

            // 标题
            Column(Modifier.fillMaxWidth()) {
                Text(
                    song?.name ?: "没有正在播放的歌曲",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        song?.artist.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(CircleShape)
                            .md3Clickable(scaleTo = .98f) { song?.let { onOpenSong(it.id) } }
                    )
                    Spacer(Modifier.width(8.dp))
                    if (state.currentQuality != null) {
                        Md3Badge(state.currentQuality!!.shortLabel)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // 进度
            Md3SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = { bridge.seek(it) }
            )

            Spacer(Modifier.height(10.dp))

            // 控制
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    bridge.setPlayMode(
                        when (state.playMode) {
                            PlayMode.LoopAll -> PlayMode.Shuffle
                            PlayMode.Shuffle -> PlayMode.RepeatOne
                            else -> PlayMode.LoopAll
                        }
                    )
                }) {
                    Icon(
                        when (state.playMode) {
                            PlayMode.Shuffle -> Icons.Rounded.Shuffle
                            PlayMode.RepeatOne -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = "播放模式",
                        tint = if (state.playMode == PlayMode.LoopAll) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { bridge.previous() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(34.dp))
                }
                FilledIconButton(
                    onClick = { bridge.toggle() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.playing) "暂停" else "播放",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { bridge.next() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = {
                    val id = song?.id
                    if (id != null) scope.launch { runCatching { bridge.likeSong(id, true) } }
                }) {
                    Icon(Icons.Rounded.FavoriteBorder, contentDescription = "喜欢")
                }
            }

            Spacer(Modifier.height(6.dp))

            // 底部工具
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "队列", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Md3Pill(if (showLyrics) "收起歌词" else "显示歌词", selected = showLyrics) {
                    showLyrics = !showLyrics
                }
                IconButton(onClick = { song?.let { bridge.shareLyricPoster(it, null, null) } }) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = "分享歌词", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 歌词抽屉
            AnimatedVisibility(
                visible = showLyrics,
                enter = slideInVertically(
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = .82f, stiffness = 380f),
                    initialOffsetY = { it }
                ),
                exit = slideOutVertically(animationSpec = tween(220), targetOffsetY = { it })
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().height(260.dp).padding(bottom = 8.dp)
                ) {
                    if (lyrics.isEmpty()) {
                        Md3EmptyState(title = "暂无歌词", desc = "这首歌还没有时间轴歌词")
                    } else {
                        Md3LyricFull(lyrics, state.positionMs) { bridge.seek(it) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Md3LyricFull(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit
) {
    val active = lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(active) {
        runCatching { listState.animateScrollToItem(active.coerceAtLeast(0)) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 110.dp)
    ) {
        itemsIndexed(lines) { i, line ->
            val isActive = i == active
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(MaterialTheme.shapes.small)
                    .md3Clickable(scaleTo = .99f) { onSeek(line.timeMs) }
            ) {
                Text(
                    line.text.ifBlank { "♪" },
                    style = if (isActive) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyLarge,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f)
                )
                if (!line.translation.isNullOrBlank()) {
                    Text(
                        line.translation!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isActive) .8f else .35f)
                    )
                }
            }
        }
    }
}
