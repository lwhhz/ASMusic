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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import com.kyant.backdrop.catalog.appstore.music.PlayStats
import com.kyant.backdrop.catalog.appstore.music.Playlist
import com.kyant.backdrop.catalog.appstore.music.SearchResults
import com.kyant.backdrop.catalog.appstore.music.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// ---------------------------------------------------------------------------
// 首页
// ---------------------------------------------------------------------------

@Composable
fun Md3HomeScreen(
    onOpenSong: (Long) -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    onOpenArtist: (Long, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    scroll: LazyListState = rememberLazyListState()
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val tokens = LocalMd3Tokens.current
    val config = tokens.config
    var cards by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var daily by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        coroutineScope {
            val cardsTask = async { runCatching { bridge.todayCards() }.getOrDefault(emptyList()) }
            val dailyTask = async { runCatching { bridge.dailySongs() }.getOrDefault(emptyList()) }
            cards = cardsTask.await()
            daily = dailyTask.await()
            loading = false
        }
    }

    val greeting = when (config?.greeting ?: 1) {
        0 -> state.nickname ?: "欢迎回来"
        2 -> "今天想听点什么"
        else -> {
            val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            when {
                h < 6 -> "夜深了"
                h < 11 -> "早上好"
                h < 14 -> "中午好"
                h < 18 -> "下午好"
                else -> "晚上好"
            }
        }
    }
    val layout = config?.homeLayout ?: HomeLayout.Cards

    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = md3TopInset(), bottom = md3ListBottom()),
        verticalArrangement = Arrangement.spacedBy(tokens.gap(18))
    ) {
        item {
            Md3PageHeader(
                title = greeting,
                subtitle = state.currentSong?.let { "正在听 · ${it.name}" }
                    ?: "从每日推荐开始，或搜索你想听的歌"
            ) {
                Md3IconButton(
                    Icons.Rounded.Search,
                    contentDescription = "搜索",
                    onClick = onOpenSearch,
                    size = 44.dp,
                    tonal = true,
                    iconSize = 20.dp
                )
                Spacer(Modifier.width(8.dp))
                Md3AvatarButton(state.avatarUrl, "设置", onOpenSettings)
            }
        }

        // Hero：每日推荐
        item {
            val hero = cards.firstOrNull { it.songCount > 0 } ?: cards.firstOrNull()
            Md3HeroCard(
                playlist = hero,
                subtitle = if (daily.isNotEmpty()) "${daily.size} 首 · 每天 6:00 更新" else "为你挑选",
                onPlay = {
                    if (daily.isNotEmpty()) bridge.playFrom(daily, daily.first())
                    else if (hero != null) onOpenPlaylist(hero.id, hero.name)
                },
                onOpen = { if (hero != null) onOpenPlaylist(hero.id, hero.name) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Md3IconTile(Icons.Rounded.AutoAwesome, "每日推荐") {
                    if (daily.isNotEmpty()) bridge.playFrom(daily, daily.first())
                    else bridge.showToast("每日推荐还在加载")
                }
                Md3IconTile(Icons.Rounded.Favorite, "私人漫游") {
                    bridge.enterHeartMode(true)
                }
                Md3IconTile(Icons.Rounded.Shuffle, "随机播放") {
                    val pool = daily.ifEmpty { state.recentSongs }
                    if (pool.isNotEmpty()) {
                        val shuffled = pool.shuffled()
                        bridge.playFrom(shuffled, shuffled.first())
                    } else {
                        bridge.showToast("还没有可播放的歌")
                    }
                }
                Md3IconTile(Icons.Rounded.History, "最近播放") {
                    if (state.recentSongs.isNotEmpty()) {
                        bridge.playFrom(state.recentSongs, state.recentSongs.first())
                    } else {
                        bridge.showToast("暂无播放记录")
                    }
                }
            }
        }

        if (cards.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) { Md3SectionTitle("为你推荐") }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(tokens.gap(12))
                ) {
                    items(cards.size) { i ->
                        val card = cards[i]
                        Md3MediaCard(card, onClick = { onOpenPlaylist(card.id, card.name) })
                    }
                }
            }
        }

        if (daily.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Md3SectionTitle(
                        "今日推荐",
                        action = "播放全部",
                        onAction = { bridge.playFrom(daily, daily.first()) }
                    )
                }
            }
            when (layout) {
                HomeLayout.Grid -> items(daily.take(8).chunked(2)) { pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { song ->
                            Md3SongGridCard(
                                song = song,
                                isCurrent = state.currentSong?.id == song.id,
                                playing = state.playing,
                                modifier = Modifier.weight(1f)
                            ) {
                                bridge.playFrom(daily, song)
                                onOpenSong(song.id)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                HomeLayout.List -> itemsIndexed(daily.take(20)) { index, song ->
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        Md3SongRow(
                            song = song,
                            index = index + 1,
                            isCurrent = state.currentSong?.id == song.id,
                            playing = state.playing,
                            onClick = {
                                bridge.playFrom(daily, song)
                                onOpenSong(song.id)
                            }
                        )
                    }
                }
                HomeLayout.Cards -> itemsIndexed(daily.take(8)) { index, song ->
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        Md3SongRow(
                            song = song,
                            index = index + 1,
                            isCurrent = state.currentSong?.id == song.id,
                            playing = state.playing,
                            onClick = {
                                bridge.playFrom(daily, song)
                                onOpenSong(song.id)
                            }
                        )
                    }
                }
            }
        }

        if (state.recentSongs.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) { Md3SectionTitle("最近播放") }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(tokens.gap(12))
                ) {
                    items(state.recentSongs.take(12)) { song ->
                        Md3RecentCard(song) {
                            bridge.playFrom(state.recentSongs, song)
                            onOpenSong(song.id)
                        }
                    }
                }
            }
        }

        if (loading && cards.isEmpty() && daily.isEmpty()) {
            item { Md3SkeletonList(5) }
        }
    }
}

/** 首页 hero 卡片：封面做背景、大字标题、播放按钮。 */
@Composable
private fun Md3HeroCard(
    playlist: Playlist?,
    subtitle: String,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().height(196.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Box {
            Md3CoverBackdrop(playlist?.coverUrl, Modifier.fillMaxSize(), darken = .32f)
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Surface(color = colors.primary.copy(alpha = .18f), shape = CircleShape) {
                        Text(
                            "FOR YOU",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                            color = colors.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        playlist?.name ?: "每日推荐",
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .md3SurfaceClick(onClick = onOpen, scaleTo = .99f)
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Md3Button(
                        onClick = onPlay,
                        text = "播放",
                        icon = Icons.Rounded.PlayArrow,
                        size = Md3Size.S
                    )
                    Spacer(Modifier.width(8.dp))
                    Md3Button(
                        onClick = onOpen,
                        text = "查看歌单",
                        style = Md3ButtonStyle.Text,
                        size = Md3Size.S
                    )
                }
            }
        }
    }
}

@Composable
private fun Md3RecentCard(song: Song, onClick: () -> Unit) {
    Column(
        Modifier
            .width(104.dp)
            .clip(MaterialTheme.shapes.medium)
            .md3SurfaceClick(onClick = onClick, shape = MaterialTheme.shapes.medium)
    ) {
        Md3Cover(song.coverUrl, Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            song.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Md3SongGridCard(
    song: Song,
    isCurrent: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .md3SurfaceClick(onClick = onClick, shape = MaterialTheme.shapes.large, scaleTo = .97f)
    ) {
        Box {
            Md3Cover(song.coverUrl, Modifier.fillMaxWidth().aspectRatio(1f), shape = MaterialTheme.shapes.large)
            if (isCurrent) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "正在播放" else "已暂停",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            song.name,
            style = MaterialTheme.typography.titleSmall,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// 发现
// ---------------------------------------------------------------------------

@Composable
fun Md3DiscoverScreen(
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    onOpenSong: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    scroll: LazyListState = rememberLazyListState()
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val tokens = LocalMd3Tokens.current
    var albums by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var singles by remember { mutableStateOf<List<Song>>(emptyList()) }
    var toplists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        coroutineScope {
            val a = async { runCatching { bridge.newAlbums() }.getOrDefault(emptyList()) }
            val s = async { runCatching { bridge.dailySongs() }.getOrDefault(emptyList()) }
            val t = async { runCatching { bridge.toplists() }.getOrDefault(emptyList()) }
            albums = a.await()
            singles = s.await()
            toplists = t.await()
            loading = false
        }
    }

    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = md3TopInset(), bottom = md3ListBottom()),
        verticalArrangement = Arrangement.spacedBy(tokens.gap(16))
    ) {
        item {
            Md3PageHeader(title = "发现", subtitle = "新上架的专辑、最热的榜单") {
                Md3IconButton(
                    Icons.Rounded.Search,
                    contentDescription = "搜索",
                    onClick = onOpenSearch,
                    size = 44.dp,
                    tonal = true,
                    iconSize = 20.dp
                )
                Spacer(Modifier.width(8.dp))
                Md3AvatarButton(state.avatarUrl, "设置", onOpenSettings)
            }
        }

        if (albums.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) { Md3SectionTitle("新碟上架") }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(tokens.gap(12))
                ) {
                    items(albums.size) { i ->
                        val album = albums[i]
                        Column(
                            Modifier
                                .width(180.dp)
                                .clip(MaterialTheme.shapes.large)
                                .md3SurfaceClick(
                                    onClick = { onOpenAlbum(album.id, album.name) },
                                    shape = MaterialTheme.shapes.large
                                )
                        ) {
                            Md3Cover(
                                album.coverUrl,
                                Modifier.fillMaxWidth().aspectRatio(1f),
                                shape = MaterialTheme.shapes.large
                            )
                            Spacer(Modifier.height(8.dp))
                            // 固定两行的高度：专辑名长短不一时条目还能对齐
                            Column(Modifier.height(38.dp)) {
                                Text(
                                    album.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    album.subtitle ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        if (toplists.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) { Md3SectionTitle("排行榜") }
            }
            items(toplists) { list ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(MaterialTheme.shapes.large)
                        .md3SurfaceClick(
                            onClick = { onOpenPlaylist(list.id, list.name) },
                            shape = MaterialTheme.shapes.large
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Md3Cover(list.coverUrl, Modifier.size(64.dp), corner = 14.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(list.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            list.subtitle ?: "${list.songCount} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Rounded.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (singles.isNotEmpty()) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Md3SectionTitle("最新单曲", Modifier.weight(1f))
                    Md3Button(
                        onClick = { bridge.playFrom(singles, singles.first()) },
                        text = "播放全部",
                        style = Md3ButtonStyle.Text,
                        size = Md3Size.XS
                    )
                }
            }
            itemsIndexed(singles.take(10)) { i, song ->
                Box(Modifier.padding(horizontal = 8.dp)) {
                    Md3SongRow(
                        song = song,
                        index = i + 1,
                        isCurrent = state.currentSong?.id == song.id,
                        playing = state.playing,
                        onClick = {
                            bridge.playFrom(singles, song)
                            onOpenSong(song.id)
                        }
                    )
                }
            }
        }

        if (loading) item { Md3SkeletonList(4) }
    }
}

// ---------------------------------------------------------------------------
// 资料库
// ---------------------------------------------------------------------------

@Composable
fun Md3LibraryScreen(
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenSong: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    scroll: LazyListState = rememberLazyListState()
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val tokens = LocalMd3Tokens.current
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var tab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(state.loggedIn) {
        loading = true
        playlists = runCatching { bridge.personalPlaylists() }.getOrDefault(emptyList())
        loading = false
    }

    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = md3TopInset(), bottom = md3ListBottom()),
        verticalArrangement = Arrangement.spacedBy(tokens.gap(12))
    ) {
        item {
            Md3PageHeader(
                title = "资料库",
                subtitle = state.nickname ?: "未登录 · 仅显示本地历史"
            ) {
                Md3IconButton(
                    Icons.Rounded.Search,
                    contentDescription = "搜索",
                    onClick = onOpenSearch,
                    size = 44.dp,
                    tonal = true,
                    iconSize = 20.dp
                )
                Spacer(Modifier.width(8.dp))
                Md3AvatarButton(state.avatarUrl, "设置", onOpenSettings)
            }
        }

        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Md3CollectionCard(
                    title = "最近播放",
                    subtitle = "${state.recentSongs.size} 首",
                    icon = Icons.Rounded.History,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.recentSongs.isNotEmpty()) {
                        bridge.playFrom(state.recentSongs, state.recentSongs.first())
                    } else {
                        bridge.showToast("暂无播放记录")
                    }
                }
                Md3CollectionCard(
                    title = "私人漫游",
                    subtitle = if (state.heartMode) "进行中" else "按口味推荐",
                    icon = Icons.Rounded.LocalFireDepartment,
                    modifier = Modifier.weight(1f)
                ) {
                    bridge.enterHeartMode(true)
                }
            }
        }

        item {
            Md3ConnectedButtonGroup(
                options = listOf("歌单", "最近播放"),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp),
                fullWidth = true
            )
        }

        if (tab == 0) {
            if (playlists.isEmpty() && !loading) {
                item {
                    Md3EmptyState(
                        icon = Icons.Rounded.LibraryMusic,
                        title = if (state.loggedIn) "还没有歌单" else "未登录",
                        desc = if (state.loggedIn) "在网页版创建歌单后会同步到这里"
                        else "登录网易云后可见你的歌单"
                    )
                }
            }
            items(playlists) { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .md3SurfaceClick(
                            onClick = { onOpenPlaylist(p.id, p.name) },
                            shape = MaterialTheme.shapes.medium
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Md3Cover(p.coverUrl, Modifier.size(56.dp), corner = 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${p.songCount} 首${if (!p.subtitle.isNullOrBlank()) " · ${p.subtitle}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (loading) item { Md3SkeletonList(3) }
        } else {
            if (state.recentSongs.isEmpty()) {
                item { Md3EmptyState(Icons.Rounded.History, "还没有播放记录", "听过的歌会出现在这里") }
            }
            itemsIndexed(state.recentSongs) { i, song ->
                Box(Modifier.padding(horizontal = 8.dp)) {
                    Md3SongRow(
                        song = song,
                        index = i + 1,
                        isCurrent = state.currentSong?.id == song.id,
                        playing = state.playing,
                        onClick = {
                            bridge.playFrom(state.recentSongs, song)
                            onOpenSong(song.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Md3CollectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .height(96.dp)
            .clip(MaterialTheme.shapes.large)
            .md3SurfaceClick(onClick = onClick, shape = MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = colors.secondaryContainer.copy(alpha = .55f)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = colors.onSecondaryContainer, modifier = Modifier.size(22.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSecondaryContainer)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSecondaryContainer.copy(alpha = .7f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 统计
// ---------------------------------------------------------------------------

@Composable
fun Md3StatsScreen(
    onOpenSettings: () -> Unit,
    scroll: LazyListState = rememberLazyListState()
) {
    val bridge = MusicServices.bridge
    val tokens = LocalMd3Tokens.current
    var stats by remember { mutableStateOf<PlayStats?>(null) }
    LaunchedEffect(Unit) { stats = runCatching { bridge.stats() }.getOrNull() }
    val s = stats

    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = md3TopInset(), bottom = md3ListBottom()),
        verticalArrangement = Arrangement.spacedBy(tokens.gap(14))
    ) {
        item {
            Md3PageHeader(title = "统计", subtitle = "你的收听习惯") {
                Md3IconButton(
                    Icons.Rounded.Settings,
                    contentDescription = "设置",
                    onClick = onOpenSettings,
                    size = 44.dp,
                    tonal = true,
                    iconSize = 20.dp
                )
            }
        }
        if (s == null) {
            item { Md3SkeletonList(4) }
        } else {
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Md3StatTile("总播放", "${s.totalPlays}", Modifier.weight(1f))
                    Md3StatTile("连续", "${s.streakDays} 天", Modifier.weight(1f))
                    Md3StatTile("本周", "${s.weekMinutes} 分", Modifier.weight(1f))
                }
            }
            item {
                Surface(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "今日收听",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${s.todayMinutes} 分钟",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Md3Bars(s.hourly)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("0", "6", "12", "18", "24").forEach {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) { Md3SectionTitle("最常听") }
            }
            itemsIndexed(s.topSongs) { i, songStat ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(24.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(songStat.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            songStat.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${songStat.plays} 次",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) { Md3SectionTitle("最常听歌手") }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(s.topArtists.size) { i ->
                        val a = s.topArtists[i]
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .5f),
                            modifier = Modifier.width(132.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(
                                    "${a.plays} 次",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Md3StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(82.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// 搜索
// ---------------------------------------------------------------------------

@Composable
fun Md3SearchScreen(
    config: Md3Config,
    onOpenSong: (Long) -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenArtist: (Long, String) -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    scroll: LazyListState = rememberLazyListState()
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val tokens = LocalMd3Tokens.current
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var loading by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(0) }
    var showEmpty by remember { mutableStateOf(false) }
    // 分页：results 只保留第一页（含歌手/专辑），歌曲列表额外累加
    var songs by remember { mutableStateOf<List<com.kyant.backdrop.catalog.appstore.music.Song>>(emptyList()) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }

    val hot = remember { listOf("周杰伦", "陈奕迅", "林俊杰", "民谣", "摇滚", "电子", "钢琴", "深夜") }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        kotlinx.coroutines.delay(320)
        val r = runCatching { bridge.search(query) }.getOrNull()
        results = r
        songs = r?.songs.orEmpty()
        endReached = false
        showEmpty = r == null ||
            (r.songs.isEmpty() && r.artists.isEmpty() && r.albums.isEmpty())
        loading = false
    }

    // 滚到接近底部时补一页。只对「歌曲」生效：歌手/专辑本来就只取前 6 条。
    val nearEnd by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = scroll.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    // 注意：网络请求不能直接放在 LaunchedEffect(nearEnd) 里。
    // 滚动时 nearEnd 会来回翻转，effect 重启就把在飞的请求取消掉
    // （表现为「翻页请求报 composition 已退出、然后错误地显示没有更多了」）。
    // 所以用独立 scope 发请求，effect 只负责触发。
    val scope = rememberCoroutineScope()
    LaunchedEffect(nearEnd) {
        if (!nearEnd || query.isBlank() || filter != 0) return@LaunchedEffect
        if (loading || loadingMore || endReached || songs.isEmpty()) return@LaunchedEffect
        loadingMore = true
        val q = query
        val from = songs.size
        scope.launch {
            val more = runCatching { bridge.searchPage(q, from) }.getOrNull()
            if (query == q) {
                val add = more?.songs.orEmpty().filterNot { c -> songs.any { it.id == c.id } }
                if (add.isEmpty()) endReached = true else songs = songs + add
            }
            loadingMore = false
        }
    }

    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = md3TopInset(), bottom = md3ListBottom()),
        verticalArrangement = Arrangement.spacedBy(tokens.gap(10))
    ) {
        item {
            // 不要在外面再包一层 16dp：Md3PageHeader 自己已经有 16dp 内边距，
            // 再包一层会让标题比其它页面多缩进 16dp（之前就是这样）。
            Md3PageHeader(title = "搜索", subtitle = "歌曲、歌手、专辑")
        }
        item {
            // 自绘搜索框：之前用 SearchBar 套 TextField，expanded 恒为 false，
            // 结果点击会被 SearchBar 自己吞掉，这是「搜了没反应」的原因之一。
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.isBlank()) {
                            results = null
                            showEmpty = false
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focus.clearFocus()
                        config.pushSearchHistory(query)
                    }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "搜索歌曲、歌手、专辑",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                )
                if (query.isNotEmpty()) {
                    Md3IconButton(
                        Icons.Rounded.Close,
                        contentDescription = "清空",
                        onClick = {
                            query = ""
                            results = null
                            showEmpty = false
                        },
                        size = 40.dp,
                        iconSize = 18.dp
                    )
                }
            }
        }

        if (query.isNotBlank()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Md3ConnectedButtonGroup(
                        options = listOf("歌曲", "歌手", "专辑"),
                        selected = filter,
                        onSelect = { filter = it },
                        modifier = Modifier.weight(1f),
                        fullWidth = true
                    )
                    if (loading) {
                        Spacer(Modifier.width(10.dp))
                        Md3LoadingIndicator()
                    }
                }
            }
        }

        if (showEmpty) {
            item {
                Md3EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "没有找到「$query」",
                    desc = "换个关键词，或检查音源设置"
                )
            }
        }

        if (results == null) {
            if (config.searchHistory.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Md3SectionTitle("搜索历史", Modifier.weight(1f))
                        Md3Button(
                            onClick = { config.clearSearchHistory() },
                            text = "清空",
                            style = Md3ButtonStyle.Text,
                            size = Md3Size.XS
                        )
                    }
                }
                item {
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        config.searchHistory.forEach { word ->
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .md3SurfaceClick(onClick = { query = word }, shape = CircleShape)
                                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(word, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(2.dp))
                                Md3IconButton(
                                    Icons.Rounded.Close,
                                    contentDescription = "删除这条历史",
                                    onClick = { config.removeSearchHistory(word) },
                                    size = 28.dp,
                                    iconSize = 14.dp
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) { Md3SectionTitle("大家都在搜") }
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    hot.forEach { word ->
                        Md3ToggleButton(
                            selected = false,
                            onToggle = {
                                query = word
                                config.pushSearchHistory(word)
                            },
                            text = word,
                            size = Md3Size.XS
                        )
                    }
                }
            }
            if (state.recentSongs.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) { Md3SectionTitle("最近播放") }
                }
                itemsIndexed(state.recentSongs.take(8)) { i, song ->
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        Md3SongRow(
                            song = song,
                            index = i + 1,
                            isCurrent = state.currentSong?.id == song.id,
                            playing = state.playing,
                            onClick = {
                                bridge.playFrom(state.recentSongs, song)
                                onOpenSong(song.id)
                            }
                        )
                    }
                }
            }
        } else {
            val r = results!!
            when (filter) {
                0 -> {
                    if (songs.isEmpty()) item { Md3EmptyState(title = "没有找到歌曲") }
                    itemsIndexed(songs) { i, song ->
                        Box(Modifier.padding(horizontal = 8.dp)) {
                            Md3SongRow(
                                song = song,
                                index = i + 1,
                                isCurrent = state.currentSong?.id == song.id,
                                playing = state.playing,
                                onClick = {
                                    config.pushSearchHistory(query)
                                    bridge.playFrom(songs, song)
                                    onOpenSong(song.id)
                                }
                            )
                        }
                    }
                    if (loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Md3LoadingIndicator()
                            }
                        }
                    } else if (endReached && songs.isNotEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "没有更多了",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (r.artists.isEmpty()) item { Md3EmptyState(title = "没有找到歌手") }
                    items(r.artists.size) { i ->
                        val a = r.artists[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .md3SurfaceClick(
                                    onClick = { onOpenArtist(a.id, a.name) },
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Md3Cover(a.coverUrl, Modifier.size(52.dp), shape = CircleShape)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "歌手",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (r.albums.isEmpty()) item { Md3EmptyState(title = "没有找到专辑") }
                    items(r.albums) { album ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .md3SurfaceClick(
                                    onClick = { onOpenAlbum(album.id, album.name) },
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Md3Cover(album.coverUrl, Modifier.size(52.dp), corner = 12.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    album.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    album.artist,
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
