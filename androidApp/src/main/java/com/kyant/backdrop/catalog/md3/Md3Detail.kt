@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kyant.backdrop.catalog.md3

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import com.kyant.backdrop.catalog.appstore.music.PlayMode
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
    var showPlaylists by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<com.kyant.backdrop.catalog.appstore.music.Playlist>>(emptyList()) }
    val listState = rememberLazyListState()
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
    val liked = state.likedIds.contains(songId)
    val config = tokens.config
    val scope = rememberCoroutineScope()
    // 播放中封面缓慢呼吸（设置里可关）
    val breath = if (config?.breathingCover != false && playing) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "breath")
        t.animateFloat(
            initialValue = .982f,
            targetValue = 1.008f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(5600),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "breath-scale"
        ).value
    } else 1f

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(tokens.gap(16))
        ) {
            // ---- 封面 ----
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
                                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .55f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                ) {
                    // 顶部压暗：状态栏图标压在浅色渐变上也能看清
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(md3TopInset() + 72.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.scrim.copy(alpha = .22f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = md3TopInset() + 16.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Md3Cover(
                            current?.coverUrl,
                            Modifier
                                .fillMaxWidth(.66f)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    scaleX = breath
                                    scaleY = breath
                                }
                                .shadow(
                                    elevation = 18.dp,
                                    shape = MaterialTheme.shapes.extraLarge,
                                    ambientColor = MaterialTheme.colorScheme.scrim,
                                    spotColor = MaterialTheme.colorScheme.scrim
                                ),
                            shape = MaterialTheme.shapes.extraLarge
                        )
                    }
                }
            }

            // ---- 标题 ----
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            current?.name ?: "加载中…",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 没有有效 id 时不挂点击：之前 id=0 也照样可点，
                            // 点下去什么都不会发生（看起来像按钮坏了）。
                            val artistId = current?.artistId ?: 0L
                            Text(
                                current?.artist.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (artistId > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .then(
                                        if (artistId > 0) Modifier.md3SurfaceClick(
                                            onClick = { onOpenArtist(artistId, current?.artist.orEmpty()) },
                                            shape = CircleShape,
                                            scaleTo = .97f
                                        ) else Modifier
                                    )
                                    .padding(vertical = 2.dp, horizontal = 2.dp)
                            )
                            if (current?.album?.isNotBlank() == true) {
                                val albumId = current.albumId
                                Text(
                                    " · ${current.album}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .then(
                                            if (albumId > 0) Modifier.md3SurfaceClick(
                                                onClick = { onOpenAlbum(albumId, current.album.orEmpty()) },
                                                shape = CircleShape,
                                                scaleTo = .97f
                                            ) else Modifier
                                        )
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    if (state.currentQuality != null && isCurrent) {
                        Md3Badge(state.currentQuality!!.shortLabel)
                    }
                }
            }

            // ---- 进度（只有正在播放的这首才有意义） ----
            if (isCurrent && state.durationMs > 0) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Md3WavySeekBar(
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            playing = state.playing,
                            onSeek = { bridge.seek(it) },
                            wavy = config?.seekStyle != SeekStyle.Linear
                        )
                    }
                }
            }

            // ---- 传输控制：居中三个，不再和模式/队列抢宽度 ----
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Md3ButtonGroup(spacing = 18.dp) {
                        Md3IconButton(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "上一首",
                            onClick = { bridge.previous() },
                            size = 52.dp,
                            iconSize = 26.dp,
                            tonal = true
                        )
                        Md3PlayFab(
                            playing = playing,
                            isCurrent = isCurrent,
                            onClick = {
                                if (isCurrent) bridge.toggle()
                                else if (current != null) bridge.play(current)
                            }
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
                }
            }

            // ---- 次操作 ----
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Md3SplitButton(
                        text = if (liked) "已喜欢" else "喜欢",
                        onClick = {
                            scope.launch { runCatching { bridge.likeSong(songId, !liked) } }
                        },
                        actions = listOf(
                            (if (liked) "取消喜欢" else "喜欢这首歌") to {
                                scope.launch { runCatching { bridge.likeSong(songId, !liked) } }
                            },
                            "加入歌单" to { showPlaylists = true },
                            "分享歌词海报" to { current?.let { bridge.shareLyricPoster(it, null, null) } }
                        ),
                        icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        size = Md3Size.XS,
                        tonal = true,
                        trailingLabel = "喜欢的更多操作"
                    )
                    Spacer(Modifier.weight(1f))
                    Md3IconButton(
                        Icons.Rounded.QueueMusic,
                        contentDescription = "播放队列",
                        onClick = onOpenQueue,
                        size = 40.dp,
                        iconSize = 19.dp,
                        tonal = true
                    )
                }
            }

            // ---- 倍速 + 睡眠定时 ----
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Md3SplitButton(
                        text = if (state.playbackSpeed == 1f) "原速"
                        else "${state.playbackSpeed}×",
                        onClick = {
                            // 单击在 1.0 → 1.25 → 1.5 → 0.75 之间循环，右箭头挑具体档
                            val next = when (state.playbackSpeed) {
                                1f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> .75f
                                else -> 1f
                            }
                            bridge.setPlaybackSpeed(next)
                        },
                        actions = listOf(
                            "0.5×" to { bridge.setPlaybackSpeed(.5f) },
                            "0.75×" to { bridge.setPlaybackSpeed(.75f) },
                            "1.0×（原速）" to { bridge.setPlaybackSpeed(1f) },
                            "1.25×" to { bridge.setPlaybackSpeed(1.25f) },
                            "1.5×" to { bridge.setPlaybackSpeed(1.5f) },
                            "2.0×" to { bridge.setPlaybackSpeed(2f) }
                        ),
                        icon = Icons.Rounded.Speed,
                        size = Md3Size.XS,
                        tonal = true,
                        trailingLabel = "选择播放速度"
                    )
                    Md3SplitButton(
                        text = if (state.sleepMinutes > 0) "剩 ${state.sleepMinutes} 分" else "睡眠定时",
                        onClick = {
                            bridge.setSleepTimer(if (state.sleepMinutes > 0) 0 else 30)
                            bridge.showToast(
                                if (state.sleepMinutes > 0) "已取消睡眠定时" else "30 分钟后自动暂停"
                            )
                        },
                        actions = listOf(
                            "15 分钟后" to { bridge.setSleepTimer(15) },
                            "30 分钟后" to { bridge.setSleepTimer(30) },
                            "60 分钟后" to { bridge.setSleepTimer(60) },
                            "关闭定时" to { bridge.setSleepTimer(0) }
                        ),
                        icon = Icons.Rounded.Bedtime,
                        size = Md3Size.XS,
                        tonal = true,
                        trailingLabel = "睡眠定时选项"
                    )
                    Spacer(Modifier.weight(1f))
                    Md3IconButton(
                        Icons.Rounded.GraphicEq,
                        contentDescription = "音质",
                        onClick = { if (qualities.isNotEmpty()) showQuality = true },
                        size = 40.dp,
                        iconSize = 19.dp,
                        tonal = true
                    )
                }
            }

            // ---- 歌词 ----
            if (lyrics.isNotEmpty()) {
                item {
                    Md3LyricCard(
                        lines = lyrics,
                        positionMs = if (isCurrent) state.positionMs else 0L,
                        showTranslation = config?.showLyricTranslation ?: true,
                        onSeek = {
                            if (isCurrent) bridge.seek(it)
                            else if (current != null) bridge.play(current)
                        },
                        wordByWord = state.wordByWord
                    )
                }
            }

            // ---- 评论 ----
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
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
                            color = MaterialTheme.colorScheme.surfaceContainer,
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

            // ---- 歌曲信息 ----
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Md3SectionTitle("歌曲信息")
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Md3InfoRow("专辑", current?.album ?: "—")
                            Md3InfoRow("时长", md3Time(current?.durationMs ?: 0))
                            val br = if (isCurrent) state.currentQuality?.bitrate ?: 0 else 0
                            Md3InfoRow("码率", if (br > 0) "$br kbps" else "—")
                            Md3InfoRow(
                                "音质",
                                if (isCurrent) state.currentQuality?.label
                                    ?: "${qualityLabel(state.wifiQuality)}（上限）"
                                else "${qualityLabel(state.wifiQuality)}（上限）"
                            )
                            Md3InfoRow("来源", if (state.directMode) "直连" else "代理")
                        }
                    }
                }
            }
        }

        // ---- 顶栏：滚过封面后出现 ----
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

        if (!collapsed) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .82f),
                modifier = Modifier
                    .padding(start = 12.dp, top = md3TopInset() + 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .md3SurfaceClick(onClick = onBack, shape = CircleShape, scaleTo = .94f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(20.dp)
                    )
                }
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
    if (showPlaylists) {
        LaunchedEffect(Unit) {
            playlists = runCatching { bridge.personalPlaylists() }.getOrDefault(emptyList())
        }
        ModalBottomSheet(
            onDismissRequest = { showPlaylists = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("加入歌单", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.loggedIn) "选择一个歌单收下这首歌" else "需要登录后才能加入歌单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (playlists.isEmpty()) {
                    Md3EmptyState(
                        icon = Icons.Rounded.QueueMusic,
                        title = if (state.loggedIn) "还没有自建歌单" else "未登录",
                        desc = if (state.loggedIn) "在网易云里建一个歌单再来" else "先扫码登录账号"
                    )
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(playlists) { pl ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .md3SurfaceClick(
                                    onClick = {
                                        scope.launch {
                                            val ok = bridge.addToPlaylist(pl.id, songId)
                                            bridge.showToast(
                                                if (ok) "已加入《${pl.name}》" else "加入失败，检查登录状态"
                                            )
                                            if (ok) {
                                                // 歌单列表有缓存，本地先 +1，免得看起来"没加进去"
                                                playlists = playlists.map {
                                                    if (it.id == pl.id) it.copy(songCount = it.songCount + 1)
                                                    else it
                                                }
                                                showPlaylists = false
                                            }
                                        }
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    scaleTo = .99f
                                )
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Md3Cover(pl.coverUrl, Modifier.size(44.dp), corner = 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pl.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(
                                    "${pl.songCount} 首",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .md3SurfaceClick(
                                onClick = {
                                    bridge.setPlaybackQuality(q.level)
                                    if (!isCurrent && current != null) bridge.play(current)
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
                        if (selected) Md3Badge("当前")
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

/**
 * 主播放键：Expressive 的形状 morph。
 *
 * 静止是正圆，按下收成 26dp 圆角并缩到 0.93；播放/暂停切换走 scale + fade，
 * 而不是直接换图标（避免"闪一下"）。
 */
@Composable
private fun Md3PlayFab(playing: Boolean, isCurrent: Boolean, onClick: () -> Unit) {
    val tokens = LocalMd3Tokens.current
    val haptics = LocalHapticFeedback.current
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (pressed) 20.dp else 34.dp,
        animationSpec = tokens.springDp,
        label = "play-corner"
    )
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) .93f else 1f,
        animationSpec = tokens.spring,
        label = "play-scale"
    )
    Box(
        Modifier
            .size(68.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                interactionSource = source,
                indication = null,
                onClick = {
                    if (tokens.haptics) {
                        runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    }
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = playing,
            transitionSpec = {
                (androidx.compose.animation.scaleIn(initialScale = .5f) +
                    androidx.compose.animation.fadeIn()) togetherWith
                    (androidx.compose.animation.scaleOut(targetScale = .5f) +
                        androidx.compose.animation.fadeOut())
            },
            label = "play-icon"
        ) { isPlaying ->
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp)
            )
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
    modifier: Modifier = Modifier,
    wordByWord: Boolean = true
) {
    val active = lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme

    // 用户正在拖动歌词时不要抢滚动：之前自动滚动会在手指下把列表拽走。
    // 松手后再等 3 秒恢复跟随，够用户读完想读的那几句。
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var holdAutoScroll by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) {
        if (dragged) {
            holdAutoScroll = true
        } else {
            kotlinx.coroutines.delay(3000)
            holdAutoScroll = false
        }
    }
    LaunchedEffect(active, holdAutoScroll) {
        if (!holdAutoScroll && active in lines.indices) {
            // 让当前行停在偏上位置，后面还有可见的上下文
            runCatching { listState.animateScrollToItem(active, scrollOffset = -220) }
        }
    }

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
                Spacer(Modifier.weight(1f))
                if (wordByWord && lines.any { it.words.isNotEmpty() }) {
                    Text(
                        "逐字",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
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
                    // 逐字：有 yrc 轴时按字上色，没有就整行上色
                    val hasWords = wordByWord && line.words.isNotEmpty()
                    val text = remember(line, isActive, positionMs) {
                        if (isActive && hasWords) {
                            karaokeText(
                                line,
                                positionMs,
                                unsung = colors.onSurfaceVariant.copy(alpha = .45f),
                                sung = colors.onSurface
                            )
                        } else {
                            androidx.compose.ui.text.AnnotatedString(
                                cleanLyric(line.text).ifBlank { "♪" }
                            )
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp)
                            .clip(MaterialTheme.shapes.small)
                            .md3SurfaceClick(onClick = { onSeek(line.timeMs) }, scaleTo = .99f)
                    ) {
                        Text(
                            text,
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

/**
 * 逐字上色。
 *
 * 每个词按它自己的时间窗口从「未唱」渐变到「已唱」，这样长音也会跟着走；
 * 没有词级时间轴的行退化成整行单色（调用方处理）。
 */
private fun karaokeText(
    line: LyricLine,
    positionMs: Long,
    unsung: Color,
    sung: Color
): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    line.words.forEach { w ->
        val progress = when {
            positionMs <= w.timeMs -> 0f
            w.durationMs <= 0L -> 1f
            else -> ((positionMs - w.timeMs).toFloat() / w.durationMs).coerceIn(0f, 1f)
        }
        withStyle(
            androidx.compose.ui.text.SpanStyle(
                color = androidx.compose.ui.graphics.lerp(unsung, sung, progress)
            )
        ) {
            append(w.text)
        }
    }
}

private fun Modifier.graphicsLayerScale(scale: Float): Modifier =
    this.scale(scale)

// ---------------------------------------------------------------------------
// 歌单 / 专辑 / 艺人
// ---------------------------------------------------------------------------

/**
 * 二级页数据的内存缓存。
 *
 * 返回上一页时，页面会被重新组合：如果曲目列表从空开始，LazyColumn 会先把
 * 恢复出来的滚动位置夹回 0（列表项不够），等网络回来已经晚了 —— 表现就是
 * 「从歌曲返回歌单，列表跳回顶部」。这里把上次的列表留在内存里，重新进入时
 * 立刻有数据，滚动位置才能对上。进程退出即失效，不写磁盘。
 */
internal object Md3PageCache {
    val playlist = mutableMapOf<Long, List<Song>>()
    val album = mutableMapOf<Long, List<Song>>()
    val artist = mutableMapOf<Long, List<Song>>()
}

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
    var songs by remember(id) { mutableStateOf(Md3PageCache.playlist[id].orEmpty()) }
    var loading by remember(id) { mutableStateOf(Md3PageCache.playlist[id] == null) }
    // 大歌单只先组合前 80 首：1000 首一次性进 LazyColumn 会让首帧明显变慢，
    // 滚动到底再补 80（列表本身是懒组合，这里省的是数据/状态构造的开销）。
    var visibleCount by remember(id) { mutableIntStateOf(80) }
    val listState = rememberLazyListState()
    val collapsed by remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemScrollOffset > 400 || listState.firstVisibleItemIndex > 0 } }

    val nearEnd by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, songs.size) {
        if (nearEnd && visibleCount < songs.size) visibleCount = (visibleCount + 80).coerceAtMost(songs.size)
    }

    LaunchedEffect(id) {
        coroutineScope {
            val listTask = async { runCatching { bridge.playlistSongs(id) }.getOrDefault(emptyList()) }
            val songsResult = listTask.await()
            if (songsResult.isNotEmpty()) {
                songs = songsResult
                Md3PageCache.playlist[id] = songsResult
            }
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

        itemsIndexed(songs.take(visibleCount)) { i, song ->
            Box(Modifier.padding(horizontal = 8.dp)) {
                Md3SongRow(
                    song = song,
                    index = i + 1,
                    isCurrent = state.currentSong?.id == song.id,
                    playing = state.playing,
                    onClick = {
                        // 队列始终用完整列表，而不是当前已加载的窗口
                        bridge.playFrom(songs, song)
                        onOpenSong(song.id)
                    }
                )
            }
        }
        if (visibleCount < songs.size) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Md3Button(
                        onClick = { visibleCount = (visibleCount + 80).coerceAtMost(songs.size) },
                        text = "继续加载（${songs.size - visibleCount}）",
                        style = Md3ButtonStyle.Tonal,
                        size = Md3Size.XS
                    )
                }
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
    var songs by remember(id) { mutableStateOf(Md3PageCache.album[id].orEmpty()) }
    var desc by remember(id) { mutableStateOf<String?>(null) }
    var loading by remember(id) { mutableStateOf(Md3PageCache.album[id] == null) }
    LaunchedEffect(id) {
        coroutineScope {
            val s = async { runCatching { bridge.albumSongs(id) }.getOrNull().orEmpty() }
            val d = async { runCatching { bridge.albumDescription(id) }.getOrNull() }
            val list = s.await()
            if (list.isNotEmpty()) {
                songs = list
                Md3PageCache.album[id] = list
            }
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
    val listState = rememberLazyListState()
    var removeIndex by remember { mutableStateOf<Int?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    // 打开时定位到正在播放的那首：队列几十首时，从第 1 首往下翻很折磨。
    LaunchedEffect(state.playQueue.size) {
        if (state.playQueue.isNotEmpty() && state.playQueue.size > 6) {
            runCatching { listState.scrollToItem(state.queueIndex) }
        }
    }

    Column(Modifier.fillMaxWidth().height(540.dp)) {
        // 标题放在 LazyColumn 外面：定位到当前曲时它不会被一起滚走。
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("播放队列", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${state.playQueue.size} 首 · 正在播放第 ${state.queueIndex + 1} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.playQueue.size > 1) {
                Md3Button(
                    onClick = { confirmClear = true },
                    text = "清空",
                    style = Md3ButtonStyle.Text,
                    size = Md3Size.XS
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(state.playQueue) { i, song ->
                Md3SongRow(
                    song = song,
                    index = i + 1,
                    isCurrent = state.currentSong?.id == song.id,
                    playing = state.playing,
                    onClick = {
                        bridge.playQueueIndex(i)
                        onClose()
                    },
                    onMore = { removeIndex = i }
                )
            }
            if (state.playQueue.isEmpty()) {
                item { Md3EmptyState(title = "队列是空的", desc = "播放一首歌后会出现在这里") }
            }
        }
    }

    removeIndex?.let { index ->
        val song = state.playQueue.getOrNull(index)
        AlertDialog(
            onDismissRequest = { removeIndex = null },
            title = { Text("从队列移除？") },
            text = {
                Text(
                    if (song != null) "《${song.name}》会从播放队列里移除" +
                        if (index == state.queueIndex) "，并切到下一首。" else "。"
                    else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    bridge.removeFromQueue(index)
                    removeIndex = null
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { removeIndex = null }) { Text("取消") } }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空播放队列？") },
            text = { Text("会清掉其余 ${(state.playQueue.size - 1).coerceAtLeast(0)} 首，正在播放的这首保留。") },
            confirmButton = {
                TextButton(onClick = {
                    bridge.clearQueue()
                    confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }
}
