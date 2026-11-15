@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kyant.backdrop.catalog.md3

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.music.CommentItem
import com.kyant.backdrop.catalog.appstore.music.LyricLine
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import com.kyant.backdrop.catalog.appstore.music.Song
import com.kyant.backdrop.catalog.appstore.music.SongQuality
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

// ---------------------------------------------------------------------------
// 歌曲详情
// ---------------------------------------------------------------------------

@Composable
fun Md3SongScreen(
    songId: Long,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenArtist: (Long, String) -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    onOpenQueue: () -> Unit
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val tokens = LocalMd3Tokens.current
    var song by remember(songId) {
        mutableStateOf(state.currentSong?.takeIf { it.id == songId })
    }
    var lyrics by remember(songId) { mutableStateOf<List<LyricLine>>(emptyList()) }
    var comments by remember(songId) { mutableStateOf<List<CommentItem>>(emptyList()) }
    var qualities by remember(songId) { mutableStateOf<List<SongQuality>>(emptyList()) }
    var showComments by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // 滚过封面后换成实心顶栏：否则固定的返回键会压在歌词卡上
    val collapsed by remember {
        androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    LaunchedEffect(songId) {
        coroutineScope {
            val d = async { runCatching { bridge.songDetail(songId) }.getOrNull() }
            val l = async { runCatching { bridge.lyric(songId) }.getOrDefault(emptyList()) }
            val c = async { runCatching { bridge.comments(songId) }.getOrDefault(emptyList()) }
            val q = async { runCatching { bridge.songQualities(songId) }.getOrDefault(emptyList()) }
            song = d.await() ?: song
            lyrics = l.await()
            comments = c.await()
            qualities = q.await()
        }
    }

    val current = song
    val isCurrent = state.currentSong?.id == songId
    val playing = isCurrent && state.playing
    val config = tokens.config
    val liked = song != null && state.likedIds.contains(songId)
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(tokens.gap(18))
        ) {
            // ---- 封面 + 渐变 ----
            item {
                Box(Modifier.fillMaxWidth()) {
                    Md3CoverBackdrop(current?.coverUrl, Modifier.fillMaxWidth().height(420.dp), darken = .2f)
                    Column(
                        Modifier.fillMaxWidth().padding(top = md3TopInset() + 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Md3Cover(
                            current?.coverUrl,
                            Modifier.fillMaxWidth(.62f).aspectRatio(1f),
                            shape = MaterialTheme.shapes.extraLarge
                        )
                    }
                }
            }

            // ---- 标题区 ----
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        current?.name ?: "加载中…",
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            current?.artist ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .md3Clickable(scaleTo = .97f) {
                                    val id = current?.artistId ?: 0L
                                    if (id > 0) onOpenArtist(id, current?.artist.orEmpty())
                                }
                                .padding(vertical = 2.dp)
                        )
                        if (current?.album?.isNotBlank() == true) {
                            Text(
                                "  ·  ${current.album}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ---- 操作行 ----
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Md3Button(
                        onClick = {
                            if (current != null) {
                                if (isCurrent) bridge.toggle() else bridge.play(current)
                            }
                        },
                        text = if (playing) "暂停" else "播放",
                        icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        size = Md3Size.S,
                        modifier = Modifier.weight(1f)
                    )
                    Md3IconButton(
                        icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (liked) "取消喜欢" else "喜欢",
                        onClick = {
                            val id = current?.id
                            if (id != null) {
                                scope.launch { runCatching { bridge.likeSong(id, !liked) } }
                            }
                        },
                        selected = liked,
                        tonal = true
                    )
                    Md3IconButton(
                        Icons.Rounded.GraphicEq,
                        contentDescription = "打开播放页",
                        onClick = {
                            if (current != null) {
                                if (!isCurrent) bridge.play(current)
                                onOpenPlayer()
                            }
                        },
                        tonal = true
                    )
                    Md3IconButton(
                        Icons.Rounded.MoreVert,
                        contentDescription = "音质与更多",
                        onClick = { if (qualities.isNotEmpty()) showQuality = true },
                        tonal = true
                    )
                }
            }

            // ---- 歌词与信息：顺序由「歌词优先 / 封面优先」决定 ----
            val lyricsBlock: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                if (lyrics.isNotEmpty()) {
                    item {
                        Md3LyricCard(
                            lines = lyrics,
                            positionMs = if (isCurrent) state.positionMs else 0L,
                            showTranslation = config?.showLyricTranslation ?: true,
                            onSeek = {
                                if (isCurrent) bridge.seek(it)
                                else if (current != null) bridge.play(current)
                            }
                        )
                    }
                }
            }
            val infoBlock: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Md3SectionTitle("歌曲信息")
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Md3InfoRow("专辑", current?.album ?: "—")
                                Md3InfoRow("时长", md3Time(current?.durationMs ?: 0))
                                val br = state.currentQuality?.bitrate ?: 0
                                Md3InfoRow("码率", if (br > 0) "$br kbps" else "—")
                                Md3InfoRow(
                                    "音质",
                                    state.currentQuality?.label ?: "${qualityLabel(state.wifiQuality)}（上限）"
                                )
                                Md3InfoRow("来源", if (state.directMode) "直连" else "代理")
                            }
                        }
                    }
                }
            }
            when (config?.detailLayout ?: DetailLayout.LyricsFirst) {
                DetailLayout.LyricsFirst -> {
                    lyricsBlock()
                    infoBlock()
                }
                DetailLayout.CoverFirst -> {
                    infoBlock()
                    lyricsBlock()
                }
            }

            // ---- 评论 ----
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Md3SectionTitle("评论", Modifier.weight(1f))
                        if (comments.isNotEmpty()) {
                            Md3Button(
                                onClick = { showComments = true },
                                text = "全部 ${comments.size}",
                                style = Md3ButtonStyle.Text,
                                size = Md3Size.XS
                            )
                        }
                    }
                    if (comments.isEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "还没有人评论这首歌",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            comments.take(3).forEach { c -> Md3CommentCard(c) }
                        }
                    }
                }
            }
        }

        // ---- 顶栏：滚过封面后出现（避免返回键压在歌词上） ----
        androidx.compose.animation.AnimatedVisibility(
            visible = collapsed,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
            exit = androidx.compose.animation.fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.statusBarsPadding()) {
                    Md3TopBar(
                        title = current?.name ?: "歌曲",
                        onBack = onBack,
                        subtitle = current?.artist
                    )
                }
            }
        }

        if (!collapsed) Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .92f),
            modifier = Modifier
                .padding(start = 12.dp, top = md3TopInset() + 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .md3SurfaceClick(onClick = onBack, shape = CircleShape, scaleTo = .94f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", modifier = Modifier.size(20.dp))
            }
        }
    }

    if (showComments) {
        ModalBottomSheet(
            onDismissRequest = { showComments = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Md3CommentsSheet(comments) { showComments = false }
        }
    }
    if (showQuality) {
        ModalBottomSheet(onDismissRequest = { showQuality = false }) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("选择音质", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                qualities.forEach { q ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                            .md3Clickable { bridge.setPlaybackQuality(q.level); showQuality = false }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(q.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (q.playable) "${q.bitrate} kbps" else "需要会员",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.currentQuality?.level == q.level) {
                            Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Md3InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
    }
}

@Composable
fun Md3CommentCard(c: CommentItem, onClick: (() -> Unit)? = null) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.large).md3Clickable(scaleTo = .99f, onClick = onClick) else Modifier)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Md3Cover(c.avatarUrl, Modifier.size(28.dp), shape = CircleShape)
                Spacer(Modifier.width(8.dp))
                Text(
                    c.user,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${md3Count(c.liked)} 赞",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(c.content, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
            if (c.time.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    c.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun Md3CommentsSheet(comments: List<CommentItem>, onClose: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxWidth().height(520.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "评论 · ${comments.size}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        if (comments.isEmpty()) {
            item { Md3EmptyState(title = "暂无评论", desc = "这首歌还没有人留言") }
        }
        items(comments) { c -> Md3CommentCard(c) }
    }
}

// ---------------------------------------------------------------------------
// 歌词卡（时间同步 + 点击跳转）
// ---------------------------------------------------------------------------

@Composable
fun Md3LyricCard(
    lines: List<LyricLine>,
    positionMs: Long,
    showTranslation: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0 && active < lines.size) {
            runCatching { listState.animateScrollToItem(active.coerceAtLeast(0)) }
        }
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "歌词",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(240.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 28.dp)
            ) {
                itemsIndexed(lines) { i, line ->
                    val isActive = i == active
                    val scale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isActive) 1f else .93f,
                        animationSpec = LocalMd3Tokens.current.spring,
                        label = "lyric-scale"
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp)
                            .clip(MaterialTheme.shapes.small)
                            .md3SurfaceClick(onClick = { onSeek(line.timeMs) }, scaleTo = .99f)
                    ) {
                        Text(
                            cleanLyric(line.text).ifBlank { "♪" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isActive) colors.onSurface
                            else colors.onSurfaceVariant.copy(alpha = .55f),
                            modifier = Modifier.graphicsLayerScale(scale = scale)
                        )
                        if (showTranslation && !line.translation.isNullOrBlank()) {
                            Text(
                                cleanLyric(line.translation!!),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isActive) colors.primary.copy(alpha = .85f)
                                else colors.onSurfaceVariant.copy(alpha = .35f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.graphicsLayerScale(scale: Float): Modifier =
    this.scale(scale)

// ---------------------------------------------------------------------------
// 歌单 / 专辑 / 艺人
// ---------------------------------------------------------------------------

@Composable
fun Md3PlaylistScreen(
    id: Long,
    fallbackName: String,
    onBack: () -> Unit,
    onOpenSong: (Long) -> Unit,
    onOpenArtist: (Long, String) -> Unit
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val tokens = LocalMd3Tokens.current
    var name by remember(id) { mutableStateOf(fallbackName) }
    var cover by remember(id) { mutableStateOf<String?>(null) }
    var subtitle by remember(id) { mutableStateOf<String?>(null) }
    var songs by remember(id) { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember(id) { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val collapsed by remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemScrollOffset > 400 || listState.firstVisibleItemIndex > 0 } }

    LaunchedEffect(id) {
        coroutineScope {
            val listTask = async { runCatching { bridge.playlistSongs(id) }.getOrDefault(emptyList()) }
            val songsResult = listTask.await()
            songs = songsResult
            loading = false
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            val cover = songs.firstOrNull()?.coverUrl
            Box(Modifier.fillMaxWidth()) {
                Md3CoverBackdrop(cover, Modifier.fillMaxWidth().height(320.dp), darken = .28f)
                Column(
                    Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = md3TopInset() + 60.dp
                    )
                ) {
                    Md3Cover(cover, Modifier.size(150.dp), shape = MaterialTheme.shapes.large)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle ?: "${songs.size} 首",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Md3Button(
                            onClick = { if (songs.isNotEmpty()) bridge.playFrom(songs, songs.first()) },
                            text = "播放全部",
                            icon = Icons.Rounded.PlayArrow,
                            size = Md3Size.S
                        )
                        Md3Button(
                            onClick = {
                                if (songs.isNotEmpty()) {
                                    val shuffled = songs.shuffled()
                                    bridge.playFrom(shuffled, shuffled.first())
                                }
                            },
                            text = "随机播放",
                            icon = Icons.Rounded.Shuffle,
                            style = Md3ButtonStyle.Tonal,
                            size = Md3Size.S
                        )
                    }
                }
            }
        }

        itemsIndexed(songs) { i, song ->
            Box(Modifier.padding(horizontal = 8.dp)) {
                Md3SongRow(
                    song = song,
                    index = i + 1,
                    isCurrent = state.currentSong?.id == song.id,
                    playing = state.playing,
                    onClick = {
                        bridge.playFrom(songs, song)
                        onOpenSong(song.id)
                    }
                )
            }
        }
        if (loading) item { Md3SkeletonList(5) }
        if (!loading && songs.isEmpty()) item { Md3EmptyState(title = "这个歌单是空的") }
    }

    // 收起后的悬浮标题
    androidx.compose.animation.AnimatedVisibility(
        visible = collapsed,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .96f),
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                }
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (!collapsed) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .9f),
            modifier = Modifier
                .padding(start = 12.dp, top = md3TopInset() + 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .md3SurfaceClick(onClick = onBack, shape = CircleShape, scaleTo = .94f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun Md3AlbumScreen(
    id: Long,
    fallbackName: String,
    onBack: () -> Unit,
    onOpenSong: (Long) -> Unit,
    onOpenArtist: (Long, String) -> Unit
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    var songs by remember(id) { mutableStateOf<List<Song>>(emptyList()) }
    var desc by remember(id) { mutableStateOf<String?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    LaunchedEffect(id) {
        coroutineScope {
            val s = async { runCatching { bridge.albumSongs(id) }.getOrNull().orEmpty() }
            val d = async { runCatching { bridge.albumDescription(id) }.getOrNull() }
            songs = s.await()
            desc = d.await()
            loading = false
        }
    }
    val cover = songs.firstOrNull()?.coverUrl
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = md3TopInset() + 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Md3Cover(cover, Modifier.fillMaxWidth(.58f).aspectRatio(1f), shape = MaterialTheme.shapes.extraLarge)
                Spacer(Modifier.height(16.dp))
                Text(
                    songs.firstOrNull()?.album ?: fallbackName,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    songs.firstOrNull()?.artist ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                if (songs.isNotEmpty()) {
                    Md3Button(
                        onClick = { bridge.playFrom(songs, songs.first()) },
                        text = "播放专辑",
                        icon = Icons.Rounded.PlayArrow,
                        size = Md3Size.S
                    )
                }
                if (!desc.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        desc!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        itemsIndexed(songs) { i, song ->
            Box(Modifier.padding(horizontal = 8.dp)) {
                Md3SongRow(
                    song = song,
                    index = i + 1,
                    isCurrent = state.currentSong?.id == song.id,
                    playing = state.playing,
                    onClick = {
                        bridge.playFrom(songs, song)
                        onOpenSong(song.id)
                    }
                )
            }
        }
        if (loading) item { Md3SkeletonList(5) }
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .9f),
        modifier = Modifier.padding(12.dp).size(40.dp).clip(CircleShape).md3Clickable(onClick = onBack)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun Md3ArtistScreen(
    id: Long,
    fallbackName: String,
    onBack: () -> Unit,
    onOpenSong: (Long) -> Unit,
    onOpenAlbum: (Long, String) -> Unit
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    var songs by remember(id) { mutableStateOf<List<Song>>(emptyList()) }
    var albums by remember(id) { mutableStateOf<List<com.kyant.backdrop.catalog.appstore.music.Playlist>>(emptyList()) }
    var info by remember(id) { mutableStateOf<com.kyant.backdrop.catalog.appstore.music.ArtistInfo?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    LaunchedEffect(id) {
        coroutineScope {
            val s = async { runCatching { bridge.artistSongs(id) }.getOrDefault(emptyList()) }
            val a = async { runCatching { bridge.artistAlbums(id) }.getOrDefault(emptyList()) }
            val i = async { runCatching { bridge.artistInfo(id) }.getOrNull() }
            songs = s.await()
            albums = a.await()
            info = i.await()
            loading = false
        }
    }
    val cover = info?.avatarUrl ?: songs.firstOrNull()?.coverUrl
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = md3TopInset() + 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Md3Cover(cover, Modifier.size(132.dp), shape = CircleShape)
                Spacer(Modifier.height(12.dp))
                Text(songs.firstOrNull()?.artist ?: fallbackName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${songs.size} 首热门 · ${albums.size} 张专辑",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Md3Button(
                    onClick = { if (songs.isNotEmpty()) bridge.playFrom(songs, songs.first()) },
                    text = "播放热门",
                    icon = Icons.Rounded.PlayArrow,
                    size = Md3Size.S
                )
                Md3Button(
                    onClick = {
                        if (songs.isNotEmpty()) {
                            val shuffled = songs.shuffled()
                            bridge.playFrom(shuffled, shuffled.first())
                        }
                    },
                    text = "随便听听",
                    icon = Icons.Rounded.QueueMusic,
                    style = Md3ButtonStyle.Tonal,
                    size = Md3Size.S
                )
            }
        }
        if (albums.isNotEmpty()) {
            item { Column(Modifier.padding(horizontal = 16.dp)) { Md3SectionTitle("专辑") } }
            item {
                Md3Rail {
                    items(albums) { album ->
                        Md3MediaCard(album, onClick = { onOpenAlbum(album.id, album.name) })
                    }
                }
            }
        }
        item { Column(Modifier.padding(horizontal = 16.dp)) { Md3SectionTitle("热门歌曲") } }
        itemsIndexed(songs) { i, song ->
            Box(Modifier.padding(horizontal = 8.dp)) {
                Md3SongRow(
                    song = song,
                    index = i + 1,
                    isCurrent = state.currentSong?.id == song.id,
                    playing = state.playing,
                    onClick = {
                        bridge.playFrom(songs, song)
                        onOpenSong(song.id)
                    }
                )
            }
        }
        if (loading) item { Md3SkeletonList(4) }
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .9f),
        modifier = Modifier.padding(12.dp).size(40.dp).clip(CircleShape).md3Clickable(onClick = onBack)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// 播放队列
// ---------------------------------------------------------------------------

@Composable
fun Md3QueueSheet(onClose: () -> Unit) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    LazyColumn(
        Modifier.fillMaxWidth().height(540.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("播放队列", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "${state.playQueue.size} 首",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        itemsIndexed(state.playQueue) { i, song ->
            Md3SongRow(
                song = song,
                index = i + 1,
                isCurrent = state.currentSong?.id == song.id,
                playing = state.playing,
                onClick = { bridge.playQueueIndex(i) }
            )
        }
        if (state.playQueue.isEmpty()) {
            item { Md3EmptyState(title = "队列是空的", desc = "播放一首歌后会出现在这里") }
        }
    }
}
