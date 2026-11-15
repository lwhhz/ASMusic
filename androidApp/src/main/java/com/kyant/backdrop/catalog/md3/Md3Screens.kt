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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    scroll: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
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

    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 196.dp),
        verticalArrangement = Arrangement.spacedBy(tokens.gap(20))
    ) {
        // ---- 顶部问候 ----
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = tokens.gap(10)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        greeting,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        state.currentSong?.let { "正在听 · ${it.name}" }
                            ?: "从每日推荐开始，或搜索你想听的歌",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(44.dp).clip(CircleShape).md3Clickable(onClick = onOpenSettings)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val avatar = state.avatarUrl
                        if (!avatar.isNullOrBlank()) {
                            Md3Cover(avatar, Modifier.fillMaxSize(), shape = CircleShape)
                        } else {
                            Icon(
                                Icons.Rounded.LibraryMusic,
                                contentDescription = "设置",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---- Hero：每日推荐 ----
        item {
            val hero = cards.firstOrNull { it.songCount > 0 } ?: cards.firstOrNull()
            Md3HeroCard(
                playlist = hero,
                subtitle = if (daily.isNotEmpty()) "${daily.size} 首 · 每天 6:00 更新" else "为你挑选",
                onPlay = {
                    if (daily.isNotEmpty()) {
                        bridge.playFrom(daily, daily.first())
                    } else if (hero != null) onOpenPlaylist(hero.id, hero.name)
                },
                onOpen = { if (hero != null) onOpenPlaylist(hero.id, hero.name) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // ---- 快捷入口 ----
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Md3IconTile(Icons.Rounded.AutoAwesome, "每日推荐") {
                    if (daily.isNotEmpty()) bridge.playFrom(daily, daily.first())
                }
                Md3IconTile(Icons.Rounded.Favorite, "私人漫游") {
                    bridge.enterHeartMode(true)
                }
                Md3IconTile(Icons.Rounded.Shuffle, "随机播放") {
                    val pool = daily.ifEmpty { state.recentSongs }
                    if (pool.isNotEmpty()) bridge.playFrom(pool.shuffled(), pool.shuffled().first())
                }
                Md3IconTile(Icons.Rounded.History, "最近播放") {
                    if (state.recentSongs.isNotEmpty()) {
                        bridge.playFrom(state.recentSongs, state.recentSongs.first())
                    }
                }
            }
        }

        // ---- 为你推荐 ----
        if (cards.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Md3SectionHeader("为你推荐", action = "全部") { }
                }
            }
            item {
                Md3Rail {
                    items(cards.size) { i ->
                        val card = cards[i]
                        Md3MediaCard(card, onClick = { onOpenPlaylist(card.id, card.name) })
                    }
                }
            }
        }

        // ---- 每日推荐列表 ----
        if (daily.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Md3SectionHeader("今日推荐", action = "播放全部") {
                        bridge.playFrom(daily, daily.first())
                    }
                }
            }
            itemsIndexed(daily.take(8)) { index, song ->
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

        // ---- 最近播放 ----
        if (state.recentSongs.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Md3SectionHeader("最近播放", action = "清空") { }
                }
            }
            item {
                Md3Rail {
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
            Md3CoverBackdrop(playlist?.coverUrl, Modifier.fillMaxSize(), darken = .35f)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Surface(
                        color = colors.primary.copy(alpha = .18f),
                        shape = CircleShape
                    ) {
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
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).md3Clickable(scaleTo = .99f, onClick = onOpen)
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = onPlay) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("播放")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onOpen) { Text("查看歌单") }
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
            .md3Clickable(onClick = onClick)
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

// ---------------------------------------------------------------------------
// 发现（新歌 / 排行榜）
// ---------------------------------------------------------------------------

@Composable
fun Md3DiscoverScreen(
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    onOpenSong: (Long) -> Unit,
    scroll: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
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
        contentPadding = PaddingValues(bottom = 196.dp),
        verticalArrangement = Arrangement.spacedBy(tokens.gap(18))
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = tokens.gap(8))) {
                Text(
                    "发现",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 38.sp, fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    "新上架的专辑、最热的榜单",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 横向大图专辑
        if (albums.isNotEmpty()) {
            item { Md3SectionHeader("新碟上架", Modifier.padding(horizontal = 16.dp)) }
            item {
                Md3Rail {
                    items(albums.size) { i ->
                        val album = albums[i]
                        Column(
                            Modifier
                                .width(196.dp)
                                .clip(MaterialTheme.shapes.large)
                                .md3Clickable(onClick = { onOpenAlbum(album.id, album.name) })
                        ) {
                            Md3Cover(
                                album.coverUrl,
                                Modifier.fillMaxWidth().aspectRatio(1f),
                                shape = MaterialTheme.shapes.large
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                album.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                album.subtitle ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 榜单
        if (toplists.isNotEmpty()) {
            item { Md3SectionHeader("排行榜", Modifier.padding(horizontal = 16.dp)) }
            items(toplists) { list ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(MaterialTheme.shapes.large)
                        .md3Clickable { onOpenPlaylist(list.id, list.name) }
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

        // 最新单曲
        if (singles.isNotEmpty()) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Md3SectionHeader("最新单曲", Modifier.weight(1f))
                    TextButton(onClick = { bridge.playFrom(singles, singles.first()) }) {
                        Text("播放全部")
                    }
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
    scroll: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
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
        contentPadding = PaddingValues(bottom = 196.dp),
        verticalArrangement = Arrangement.spacedBy(tokens.gap(14))
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "资料库",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 38.sp, fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        state.nickname ?: "未登录 · 仅显示本地历史",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(44.dp).clip(CircleShape).md3Clickable(onClick = onOpenSettings)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val avatar = state.avatarUrl
                        if (!avatar.isNullOrBlank()) Md3Cover(avatar, Modifier.fillMaxSize(), shape = CircleShape)
                        else Icon(Icons.Rounded.LibraryMusic, null, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        // 我喜欢的音乐 / 本地
        item {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Md3CollectionCard(
                    title = "我喜欢的音乐",
                    subtitle = "${state.recentSongs.size} 首最近",
                    icon = Icons.Rounded.Favorite,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.recentSongs.isNotEmpty()) {
                        bridge.playFrom(state.recentSongs, state.recentSongs.first())
                    }
                }
                Md3CollectionCard(
                    title = "最近播放",
                    subtitle = "继续收听",
                    icon = Icons.Rounded.History,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.recentSongs.isNotEmpty()) {
                        bridge.playFrom(state.recentSongs, state.recentSongs.first())
                    }
                }
            }
        }

        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text("歌单") }
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text("最近播放") }
                )
            }
        }

        if (tab == 0) {
            if (playlists.isEmpty() && !loading) {
                item {
                    Md3EmptyState(
                        icon = Icons.Rounded.LibraryMusic,
                        title = "还没有歌单",
                        desc = if (state.loggedIn) "在网页版创建歌单后会同步到这里" else "登录网易云后可见你的歌单"
                    )
                }
            }
            items(playlists) { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .md3Clickable { onOpenPlaylist(p.id, p.name) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Md3Cover(p.coverUrl, Modifier.size(56.dp), corner = 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            if (state.recentSongs.isEmpty()) {
                item { Md3EmptyState(Icons.Rounded.History, "还没有播放记录", "听过的歌会出现在这里") }
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
        modifier = modifier.height(96.dp).clip(MaterialTheme.shapes.large).md3Clickable(onClick = onClick),
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
    scroll: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    val bridge = MusicServices.bridge
    val tokens = LocalMd3Tokens.current
    var stats by remember { mutableStateOf<PlayStats?>(null) }
    LaunchedEffect(Unit) { stats = runCatching { bridge.stats() }.getOrNull() }
    val s = stats

    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 196.dp),
        verticalArrangement = Arrangement.spacedBy(tokens.gap(16))
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = tokens.gap(8))) {
                Text(
                    "统计",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    "你的收听习惯",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (s == null) {
            item { Md3SkeletonList(4) }
        } else {
            item {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Text("今日收听", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Md3SectionHeader("最常听")
                }
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
            item { Md3SectionHeader("最常听歌手", Modifier.padding(horizontal = 16.dp)) }
            item {
                Md3Rail {
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
        color = MaterialTheme.colorScheme.surfaceContainerLow
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
    onOpenSong: (Long) -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenArtist: (Long, String) -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    scroll: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val tokens = LocalMd3Tokens.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var loading by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(0) }

    val hot = remember { listOf("周杰伦", "陈奕迅", "林俊杰", "民谣", "摇滚", "电子", "钢琴", "深夜") }

    fun runSearch(q: String) {
        query = q
        if (q.isBlank()) {
            results = null
            return
        }
        loading = true
    }

    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(320)
        results = runCatching { bridge.search(query) }.getOrNull()
        loading = false
    }

    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 196.dp),
        verticalArrangement = Arrangement.spacedBy(tokens.gap(10))
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = tokens.gap(8))) {
                Text(
                    "搜索",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.SearchBar(
                    inputField = {
                        androidx.compose.material3.TextField(
                            value = query,
                            onValueChange = ::runSearch,
                            placeholder = { Text("歌曲、歌手、歌单") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            singleLine = true,
                            shape = CircleShape,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                ) {}
            }
        }

        if (results == null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Md3SectionHeader("大家都在搜")
                    Spacer(Modifier.height(8.dp))
                }
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    hot.forEach { word -> Md3Pill(word) { runSearch(word) } }
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    Md3SectionHeader("最近播放")
                }
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
        } else {
            val r = results!!
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("歌曲", "歌手", "专辑").forEachIndexed { i, label ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
                    }
                }
            }
            when (filter) {
                0 -> {
                    if (r.songs.isEmpty()) item { Md3EmptyState(title = "没有找到歌曲") }
                    itemsIndexed(r.songs) { i, song ->
                        Box(Modifier.padding(horizontal = 8.dp)) {
                            Md3SongRow(
                                song = song,
                                index = i + 1,
                                isCurrent = state.currentSong?.id == song.id,
                                playing = state.playing,
                                onClick = {
                                    bridge.playFrom(r.songs, song)
                                    onOpenSong(song.id)
                                }
                            )
                        }
                    }
                }
                1 -> {
                    if (r.artists.isEmpty()) item { Md3EmptyState(title = "没有找到歌手") }
                    items(r.artists.size) { i ->
                        val a = r.artists[i]
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .md3Clickable { onOpenArtist(a.id, a.name) }
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
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .md3Clickable { onOpenAlbum(album.id, album.name) }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Md3Cover(album.coverUrl, Modifier.size(52.dp), corner = 12.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
