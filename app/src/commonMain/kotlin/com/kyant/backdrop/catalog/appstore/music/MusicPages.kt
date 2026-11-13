package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.AvatarArt
import com.kyant.backdrop.catalog.appstore.ChipRow
import com.kyant.backdrop.catalog.appstore.LargeHeader
import com.kyant.backdrop.catalog.appstore.SectionHeader
import com.kyant.backdrop.catalog.appstore.Txt
import com.kyant.backdrop.catalog.appstore.tap

private val BottomSpace = 180.dp
private val HotTerms = listOf("周杰伦", "陈奕迅", "民谣", "深夜", "华语流行")

// ---------------------------------------------------------------------------
// Today — first card shows the cover of the first daily-recommendation song.
// Card count is data driven and configurable through the picker sheet.
// ---------------------------------------------------------------------------

@Composable
fun TodayPage(
    scrollState: ScrollState,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSong: (Long) -> Unit,
    onAvatar: () -> Unit,
    onEditCards: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    var cards by remember { mutableStateOf(Fixtures.todayCards(state.todayKinds)) }

    LaunchedEffect(state.todayKinds, state.loggedIn) {
        cards = runCatching { bridge.todayCards() }.getOrElse { Fixtures.todayCards(state.todayKinds) }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = BottomSpace)
    ) {
        LargeHeader("今天", onAvatar)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AS.PagePad),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(AS.Card)
                    .tap(onEditCards)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Txt("自定义卡片", AS.caption().copy(color = AS.Blue)) }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.padding(horizontal = AS.PagePad),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            cards.forEachIndexed { index, playlist ->
                PlaylistCard(
                    playlist,
                    isFirst = index == 0,
                    onClick = { onOpenPlaylist(playlist.id) },
                    onPlay = {
                        playlist.songs.firstOrNull()?.let { song ->
                            bridge.playFrom(playlist.songs, song)
                            bridge.showToast("正在播放 ${song.name}")
                        }
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// New releases (formerly Games)
// ---------------------------------------------------------------------------

@Composable
fun NewReleasesPage(
    scrollState: LazyListState,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenSong: (List<Song>, Song) -> Unit,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    var releases by remember { mutableStateOf(Fixtures.newReleases) }
    var albums by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var charts by remember { mutableStateOf<List<Playlist>>(emptyList()) }

    LaunchedEffect(state.loggedIn) {
        releases = runCatching { bridge.newReleases() }.getOrDefault(Fixtures.newReleases)
        albums = runCatching { bridge.newAlbums() }.getOrDefault(emptyList())
        charts = runCatching { bridge.toplists() }.getOrDefault(emptyList())
    }
    // 大卡展示最新专辑；专辑没有预载歌曲，点击直接进专辑页。
    val showcase = albums.ifEmpty { releases }

    val allSongs = releases.flatMap { it.songs }.distinctBy { it.id }

    // Lazy: only the visible rows compose, so chart covers no longer all decode at once.
    LazyColumn(
        state = scrollState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = BottomSpace)
    ) {
        item(key = "header") {
            Column {
                LargeHeader("最新专辑", onAvatar)
                Spacer(Modifier.height(6.dp))
            }
        }
        item(key = "slides") {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val slideWidth = maxWidth - 40.dp
                Row(
                    Modifier
                        .padding(start = AS.PagePad)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    showcase.forEach { playlist ->
                        Column(Modifier.width(slideWidth)) {
                            ReleaseSlide(
                                playlist,
                                onClick = {
                                    if (playlist.songs.isEmpty()) onOpenAlbum(playlist.id)
                                    else onOpenPlaylist(playlist.id)
                                },
                                onPlay = {
                                    val song = playlist.songs.firstOrNull()
                                    if (song != null) {
                                        bridge.playFrom(playlist.songs, song)
                                        bridge.showToast("正在播放 ${song.name}")
                                    } else {
                                        onOpenAlbum(playlist.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(AS.SectionGap))
        }
        item(key = "latest-header") {
            Column {
                SectionHeader(
                    "最新单曲",
                    "刚刚上架的新声音",
                    Modifier.padding(horizontal = AS.PagePad)
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        items(allSongs.take(10), key = { it.id }, contentType = { "song-row" }) { song ->
            Box(Modifier.padding(horizontal = AS.PagePad)) {
                SongRow(
                    song,
                    playing = state.currentSong?.id == song.id && state.playing,
                    onOpen = { onOpenSong(allSongs, song) },
                    onPlay = { bridge.toggleSong(allSongs, song) }
                )
            }
        }
        if (charts.isNotEmpty()) {
            item(key = "charts-header") {
                Column {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader(
                        "排行榜",
                        "网易云全部榜单",
                        Modifier.padding(horizontal = AS.PagePad)
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
            items(charts.chunked(3), key = { row -> row.firstOrNull()?.id ?: 0L }, contentType = { "chart-row" }) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = AS.PagePad, end = AS.PagePad, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { chart ->
                        Column(
                            Modifier
                                .weight(1f)
                                .tap { onOpenPlaylist(chart.id) }
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                CoverImage(chart.coverUrl, chart.id, Modifier.fillMaxSize(), requestEdge = 256)
                            }
                            Spacer(Modifier.height(8.dp))
                            Txt(chart.name, AS.rowTitle().copy(fontSize = 13.sp), maxLines = 2)
                            Spacer(Modifier.height(2.dp))
                            Txt(chart.subtitle, AS.caption().copy(fontSize = 11.sp), maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// My playlists (formerly Apps)
// ---------------------------------------------------------------------------

@Composable
fun MyPlaylistsPage(
    scrollState: ScrollState,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSong: (List<Song>, Song) -> Unit,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    var lists by remember { mutableStateOf(Fixtures.personalPlaylists) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }

    LaunchedEffect(state.loggedIn) {
        lists = runCatching { bridge.personalPlaylists() }.getOrDefault(Fixtures.personalPlaylists)
        profile = runCatching { bridge.profile() }.getOrNull()
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = BottomSpace)
    ) {
        LargeHeader("我的", onAvatar)

        Row(
            Modifier
                .padding(horizontal = AS.PagePad)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(AS.Card)
                .tap(onAvatar)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarArt(size = 54.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt(profile?.nickname ?: "未链接账号", AS.rowTitle().copy(fontSize = 18.sp))
                Txt(
                    if (state.loggedIn) "已链接网易云音乐 · 点击头像管理" else "点击头像链接网易云账号以同步歌单",
                    AS.subtitle().copy(fontSize = 13.sp)
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionHeader("我的歌单", null, Modifier.padding(horizontal = AS.PagePad))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = AS.PagePad)) {
            lists.forEach { playlist ->
                PlaylistTile(playlist, onClick = { onOpenPlaylist(playlist.id) })
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionHeader("最近播放", null, Modifier.padding(horizontal = AS.PagePad))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = AS.PagePad)) {
            val recent = state.recentSongs
            if (recent.isEmpty()) {
                Txt("在本应用播放过的歌曲会记录在这里", AS.subtitle())
            } else {
                recent.take(8).forEach { song ->
                    SongRow(
                        song,
                        playing = state.currentSong?.id == song.id && state.playing,
                        onOpen = { onOpenSong(recent, song) },
                        onPlay = { bridge.toggleSong(recent, song) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Listening stats (formerly Arcade) — iOS-style grouped layout, rebuilt.
// ---------------------------------------------------------------------------

@Composable
fun StatsPage(
    scrollState: ScrollState,
    onAvatar: () -> Unit,
    onEditGoal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    // Start from an honest empty local profile instead of demo numbers.
    var stats by remember {
        mutableStateOf(
            PlayStats(0, 0, 0, 0, emptyList(), emptyList(), List(24) { 0 }, local = true)
        )
    }

    LaunchedEffect(state.loggedIn) {
        stats = runCatching { bridge.stats() }.getOrElse {
            PlayStats(0, 0, 0, 0, emptyList(), emptyList(), List(24) { 0 }, local = true)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = BottomSpace)
    ) {
        // LargeHeader already applies the page padding, so the body is inset on its own
        // to keep the title aligned with the other tabs.
        LargeHeader("听歌统计", onAvatar)

        Column(
            Modifier.padding(horizontal = AS.PagePad),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Txt(
                stats.note.ifBlank {
                    if (stats.local) "本机统计 · 仅统计在本应用内的播放" else "数据来自网易云账号"
                },
                AS.caption().copy(fontSize = 12.sp),
                Modifier.padding(top = 2.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    if (stats.local) "总播放" else "本周播放",
                    formatCount(stats.totalPlays), "次", Modifier.weight(1f)
                )
                StatCard("今日", "${stats.todayMinutes}", "分钟", Modifier.weight(1f))
                StatCard("连续", "${stats.streakDays}", "天", Modifier.weight(1f))
            }

            GroupCard("本周概览") {
                val target = state.weekGoalMinutes.coerceAtLeast(1)
                val pct = (stats.weekMinutes.toFloat() / target).coerceIn(0f, 1f)
                val animatedPct by animateFloatAsState(pct, tween(820), label = "week-progress")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Txt("${stats.weekMinutes}", AS.largeTitle().copy(fontSize = 40.sp))
                    Spacer(Modifier.width(8.dp))
                    Txt("分钟 · 本机播放时长", AS.subtitle(), Modifier.padding(bottom = 8.dp))
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AS.CardElevated)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(animatedPct)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AS.Blue)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().tap(onEditGoal),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Txt(
                        "目标 $target 分钟 · 已完成 ${(pct * 100).toInt()}%",
                        AS.caption(),
                        Modifier.weight(1f)
                    )
                    Txt("修改目标", AS.caption().copy(color = AS.Blue))
                }
            }

            GroupCard("听歌时段") {
                BarChart(stats.hourly, AS.Blue, Modifier.fillMaxWidth().height(120.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach {
                        Txt(it, AS.caption().copy(fontSize = 10.sp))
                    }
                }
            }

            GroupCard("最常听的歌曲") {
                if (stats.topSongs.isEmpty()) {
                    Txt("播放几首歌后这里会生成你的榜单", AS.subtitle())
                } else {
                    val max = stats.topSongs.maxOfOrNull { it.plays } ?: 1
                    stats.topSongs.forEachIndexed { i, s ->
                        RankRow(i + 1, s.name, s.artist, s.plays, max)
                    }
                }
            }

            GroupCard("最常听的艺术家") {
                if (stats.topArtists.isEmpty()) {
                    Txt("还没有足够的播放记录", AS.subtitle())
                } else {
                    val max = stats.topArtists.maxOfOrNull { it.plays } ?: 1
                    stats.topArtists.forEachIndexed { i, a ->
                        RankRow(i + 1, a.name, "累计播放", a.plays, max)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Txt(title, AS.caption().copy(fontSize = 12.sp, color = AS.Text3), Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(AS.Card)
                .padding(16.dp),
            content = content
        )
    }
}

// ---------------------------------------------------------------------------
// Search — songs / artists / albums
// ---------------------------------------------------------------------------

@Composable
fun MusicSearchPage(
    scrollState: ScrollState,
    onOpenSong: (List<Song>, Song) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    var recommended by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(query) {
        results = if (query.isBlank()) SearchResults()
        else runCatching { bridge.search(query) }.getOrElse { Fixtures.search(query) }
    }

    LaunchedEffect(state.loggedIn) {
        recommended = runCatching { bridge.dailySongs() }.getOrDefault(emptyList())
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = BottomSpace)
    ) {
        LargeHeader("搜索", onAvatar)
        MusicSearchField(query, { query = it }, Modifier.padding(horizontal = AS.PagePad))
        Spacer(Modifier.height(24.dp))

        if (query.isBlank()) {
            SectionHeader("热门搜索", null, Modifier.padding(horizontal = AS.PagePad))
            Spacer(Modifier.height(12.dp))
            ChipRow(
                HotTerms, null, { index -> if (index != null) query = HotTerms[index] },
                Modifier.padding(horizontal = AS.PagePad)
            )
            Spacer(Modifier.height(26.dp))
            SectionHeader("推荐歌曲", null, Modifier.padding(horizontal = AS.PagePad))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = AS.PagePad)) {
                val picks = recommended.take(6)
                if (picks.isEmpty()) {
                    Txt("登录网易云后这里会出现为你推荐的歌曲", AS.subtitle())
                } else {
                    picks.forEach { song ->
                        SongRow(
                            song,
                            playing = state.currentSong?.id == song.id && state.playing,
                            onOpen = { onOpenSong(picks, song) },
                            onPlay = { bridge.toggleSong(picks, song) }
                        )
                    }
                }
            }
        } else if (results.isEmpty) {
            Column(
                Modifier.fillMaxWidth().padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Txt("没有找到结果", AS.sectionTitle())
                Spacer(Modifier.height(8.dp))
                Txt("试试其他歌曲、艺术家或专辑名", AS.subtitle())
            }
        } else {
            if (results.songs.isNotEmpty()) {
                SectionHeader("歌曲", null, Modifier.padding(horizontal = AS.PagePad))
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = AS.PagePad)) {
                    results.songs.forEach { song ->
                        SongRow(
                            song,
                            playing = state.currentSong?.id == song.id && state.playing,
                            onOpen = { onOpenSong(results.songs, song) },
                            onPlay = { bridge.toggleSong(results.songs, song) }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            if (results.artists.isNotEmpty()) {
                SectionHeader("艺术家", null, Modifier.padding(horizontal = AS.PagePad))
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = AS.PagePad)) {
                    results.artists.forEach { artist ->
                        Row(
                            Modifier.fillMaxWidth().height(56.dp).tap { query = artist.name },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))) {
                                CoverImage(artist.coverUrl, artist.id, Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(12.dp))
                            Txt(artist.name, AS.rowTitle(), Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            if (results.albums.isNotEmpty()) {
                SectionHeader("专辑", null, Modifier.padding(horizontal = AS.PagePad))
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = AS.PagePad)) {
                    results.albums.forEach { album ->
                        Row(
                            Modifier.fillMaxWidth().height(64.dp).tap { onOpenPlaylist(album.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))) {
                                CoverImage(album.coverUrl, album.id, Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(album.name, AS.rowTitle(), maxLines = 1)
                                Txt(album.artist, AS.subtitle().copy(fontSize = 13.sp), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AS.Card)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.Image(
            imageVector = Iconoir.tabSearch,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(AS.Text2)
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = AS.body().copy(fontSize = 16.sp),
            cursorBrush = SolidColor(AS.Blue),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Txt("歌曲、艺术家、专辑", AS.body().copy(color = AS.Text3, fontSize = 16.sp), maxLines = 1)
                    }
                    inner()
                }
            }
        )
    }
}
