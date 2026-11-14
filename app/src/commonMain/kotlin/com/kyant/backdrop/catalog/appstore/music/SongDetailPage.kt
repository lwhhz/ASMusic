package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.ScrollState
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.CircleButton
import com.kyant.backdrop.catalog.appstore.CircleKind
import com.kyant.backdrop.catalog.appstore.ChevronRight
import com.kyant.backdrop.catalog.appstore.InfoRow
import com.kyant.backdrop.catalog.appstore.Txt
import com.kyant.backdrop.catalog.appstore.tap
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

@Composable
fun SongDetailPage(
    songId: Long,
    scrollState: ScrollState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenAlbum: (Long) -> Unit = {},
    onOpenArtist: (Long, String) -> Unit = { _, _ -> },
    onOpenComments: (Int) -> Unit = {},
    onOpenComment: (CommentItem) -> Unit = {},
    onOpenImmersive: (Song, List<LyricLine>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    // Keyed on songId: replacing the page's id (next/prev) resets all content
    // immediately instead of briefly showing the previous song.
    var song by remember(songId) {
        mutableStateOf(state.currentSong?.takeIf { it.id == songId } ?: Fixtures.song(songId))
    }
    var lines by remember(songId) { mutableStateOf(Fixtures.lyric(songId)) }
    var comments by remember(songId) { mutableStateOf(Fixtures.comments(songId)) }
    // Netease-side 红心 / comment counts for the stats row.
    var social by remember(songId) { mutableStateOf<SongSocial?>(null) }

    LaunchedEffect(songId) {
        // 并行请求：五个接口原本串行，首屏要等 5 个 RTT 依次完成。相互独立，
        // 并发后总耗时约等于最慢的那个；任何一个失败都回落到本地 fixture。
        coroutineScope {
            val detailTask = async {
                runCatching { bridge.songDetail(songId) }.getOrNull()
            }
            val lyricTask = async {
                runCatching { bridge.lyric(songId) }.getOrDefault(Fixtures.lyric(songId))
            }
            val commentsTask = async {
                runCatching { bridge.comments(songId) }.getOrDefault(Fixtures.comments(songId))
            }
            val socialTask = async {
                runCatching { bridge.songSocial(songId) }.getOrNull()
            }
            val qualitiesTask = async {
                runCatching { bridge.songQualities(songId) }
            }
            song = detailTask.await() ?: Fixtures.song(songId)
            lines = lyricTask.await()
            comments = commentsTask.await()
            social = socialTask.await()
            qualitiesTask.await()
        }
    }

    val current = song
    if (current == null) {
        Box(modifier.fillMaxSize().background(AS.Bg)) {
            CircleButton(CircleKind.Back, onBack, Modifier.padding(16.dp))
            Txt("歌曲不存在", AS.sectionTitle(), Modifier.align(Alignment.Center))
        }
        return
    }

    var storageInfo by remember(current.id) { mutableStateOf(SongStorageInfo()) }
    LaunchedEffect(current.id, state.playing) {
        while (true) {
            storageInfo = runCatching { bridge.songStorageInfo(current.id) }
                .getOrDefault(SongStorageInfo())
            if (!state.playing) break
            delay(5_000)
        }
    }

    val isCurrent = state.currentSong?.id == current.id
    val playing = isCurrent && state.playing
    // 封面优先用播放器里正在播的那一份：songDetail 接口偶尔不带 picUrl，
    // 落到 Fixtures 兜底后 coverUrl 会变 null，这一页的封面就会掉成占位图。
    val coverUrl = state.currentSong?.takeIf { it.id == current.id }?.coverUrl
        ?: current.coverUrl
    // One source of truth for the stats row and the comment header.
    val commentTotal = when {
        social?.hasComments == true -> social?.commentCount ?: 0
        current.commentCount > 0 -> current.commentCount
        else -> comments.size
    }
    // Four cover colors for the lyric-card gradients; refreshed when the song/cover changes.
    var cardPalette by remember(current.id) { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(current.id, current.coverUrl) {
        cardPalette = runCatching {
            bridge.coverPalette(current.coverUrl.orEmpty())
        }.getOrNull()?.map { Color(it) }.orEmpty()
    }
    // Album name links to the album page; singles without an album id stay inert.
    val albumLink = current.albumId > 0L && current.album.isNotBlank() && current.album != "单曲"
    // Netease joins featured artists with " / "; the artist page belongs to the first.
    val artistName = current.artist.split("/").firstOrNull()?.trim().orEmpty()
        .ifBlank { current.artist }
    val artistOptions = remember(current.artists, current.artistId, artistName) {
        current.artists.ifEmpty {
            listOfNotNull(ArtistBrief(current.artistId, artistName))
        }.filter { it.id > 0L && it.name.isNotBlank() }
    }
    var artistPickerOpen by remember(current.id) { mutableStateOf(false) }
    fun openArtistOptions() {
        when {
            artistOptions.isEmpty() -> Unit
            artistOptions.size == 1 -> onOpenArtist(artistOptions[0].id, artistOptions[0].name)
            else -> artistPickerOpen = true
        }
    }
    val openAlbum = { if (albumLink) onOpenAlbum(current.albumId) }

    fun togglePlay() {
        if (isCurrent) bridge.toggle() else bridge.play(current)
    }

    val showSticky by remember { derivedStateOf { scrollState.value > 150 } }
    val stickyAlpha by animateFloatAsState(if (showSticky) 1f else 0f)
    val hazeState = rememberHazeState()
    // The status bar strip always sits at peak blur and is excluded from the height /
    // transition sliders: they only describe the part below it. The 64dp control bar
    // is likewise outside the band, so its controls stay crisp.
    val density = LocalDensity.current
    val statusBarDp = with(density) {
        WindowInsets.statusBars.getTop(this).toDp().value
    }.coerceIn(0f, 80f)
    val hazeHeight = (statusBarDp + state.hazeHeightDp.coerceAtLeast(HazeBarDp)).dp
    val statusBarFraction = (statusBarDp / hazeHeight.value).coerceIn(0f, .5f)
    // Full opacity runs through the status bar and the configured transition band.
    val hazeOpaqueFraction = (
        statusBarFraction + state.hazeOpaqueDp / hazeHeight.value
    ).coerceIn(statusBarFraction, .98f)
    val hazeStyle = remember(state.hazeBlurDp, hazeOpaqueFraction) {
        HazeBlurStyle {
            blurEnabled(state.hazeBlurDp > 0f)
            blurRadius(state.hazeBlurDp.dp)
            progressive(
                HazeProgressive.verticalGradient(
                    startIntensity = 1f,
                    endIntensity = 0f
                )
            )
            // Also fade the effect's own alpha, so the last pixel is fully
            // transparent instead of a blurred rectangle.
            // The panel must stay fully opaque through the readable band: any
            // transparency there lets the original sharp text show through the
            // blurred pixels, which reads as "clear text with a glow".
            mask(
                Brush.verticalGradient(
                    0f to Color.Black,
                    statusBarFraction to Color.Black,
                    hazeOpaqueFraction to Color.Black,
                    (hazeOpaqueFraction + .11f).coerceAtMost(.98f) to Color.Black.copy(alpha = .70f),
                    1f to Color.Transparent
                )
            )
            // Must match the page background exactly: the previous 0xFF0A0B10 tint
            // showed up as a faint color cast over the blurred band.
            backgroundColor(AS.Bg)
        }
    }

    Box(modifier.fillMaxSize().background(AS.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (state.detailTopStyle == DetailTopStyle.GradientBlur) {
                        Modifier.hazeSource(hazeState)
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(scrollState)
                .padding(bottom = 250.dp)
        ) {
            // The page now paints under the status bar (so Haze can blur it there);
            // this spacer keeps the floating buttons below the system clock.
            Spacer(Modifier.height(statusBarDp.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer { alpha = 1f - stickyAlpha },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CircleButton(CircleKind.Back, onBack)
                CircleButton(CircleKind.Share, onShare)
            }
            Spacer(Modifier.height(18.dp))

            // Cover-first header: large artwork, then title / artist / album, centred.
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(224.dp)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    CoverImage(coverUrl, current.id, Modifier.fillMaxSize())
                    if (current.id in state.heartRecommendedIds) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = .45f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Txt("心动推荐", AS.caption().copy(fontSize = 10.sp, color = Color.White))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Txt(
                    current.name,
                    AS.largeTitle().copy(
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 2
                )
                Spacer(Modifier.height(8.dp))
                Txt(
                    current.artist,
                    AS.subtitle(),
                    Modifier.then(
                        if (artistOptions.isNotEmpty()) Modifier.tap { openArtistOptions() }
                        else Modifier
                    ),
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.then(if (albumLink) Modifier.tap(openAlbum) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Txt(
                        current.album,
                        AS.subtitle().copy(
                            fontSize = 13.sp,
                            color = if (albumLink) AS.Blue else AS.Text3
                        ),
                        maxLines = 1
                    )
                    if (albumLink) {
                        Txt("  ›", AS.subtitle().copy(fontSize = 13.sp, color = AS.Blue))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SongStatsRow(current, social, commentTotal)
            Spacer(Modifier.height(14.dp))
            // Lyric controls: left = translation, right = word-by-word. Both write
            // through the same bridge setters as the Customize page, so the two
            // surfaces never disagree.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LyricToggleButton(
                    label = "译",
                    active = state.showTranslation,
                    onClick = { bridge.setShowTranslation(!state.showTranslation) }
                )
                LyricToggleButton(
                    label = "逐",
                    active = state.wordByWord,
                    onClick = { bridge.setWordByWord(!state.wordByWord) }
                )
            }
            Spacer(Modifier.height(12.dp))
            // Lyric cards keep the device's original density: they have their own
            // font sliders and must ignore the global UI/font scale.
            CompositionLocalProvider(LocalDensity provides DensityDefaults.original) {
            key(current.id) {
            DetailLyricCards(
                current = current,
                lines = lines,
                cardPalette = cardPalette,
                isCurrent = isCurrent,
                onSeek = { ms ->
                    // Jump the song to the lyric the user snapped to, and make sure it
                    // is actually the playing track.
                    if (!isCurrent) bridge.play(current) else if (!state.playing) bridge.toggle()
                    bridge.seek(ms)
                },
                onLongPress = { onOpenImmersive(current, lines) }
            )
            }
            }

            Spacer(Modifier.height(28.dp))
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
            Spacer(Modifier.height(22.dp))

            // Comments: the header opens the full glass sheet; each truncated card
            // opens the same style sheet with the complete comment.
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .tap { onOpenComments(commentTotal) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt("评论", AS.sectionTitle())
                Spacer(Modifier.width(6.dp))
                ChevronRight(size = 13.dp)
                Spacer(Modifier.weight(1f))
                Txt(
                    "共 ${formatCount(commentTotal)} 条",
                    AS.caption().copy(color = AS.Text3)
                )
            }
            Spacer(Modifier.height(6.dp))
            Txt(
                "查看全部评论",
                AS.subtitle(),
                Modifier
                    .padding(horizontal = 16.dp)
                    .tap { onOpenComments(commentTotal) }
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.padding(start = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                comments.forEach { CommentCard(it, onOpen = { onOpenComment(it) }) }
            }

            Spacer(Modifier.height(28.dp))
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
            Spacer(Modifier.height(22.dp))

            // Attributes (replaces What's New + Information; What's New removed entirely)
            Txt("歌曲信息", AS.sectionTitle(), Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                InfoRow("码率", "${current.bitrateKbps} kbps")
                InfoRow("时长", current.durationText)
                val localBytes = if (storageInfo.downloaded) {
                    storageInfo.downloadedBytes
                } else {
                    storageInfo.cachedBytes
                }
                InfoRow(
                    if (storageInfo.downloaded) "大小（已下载）" else "大小",
                    if (localBytes > 0L) formatBytes(localBytes) else "—"
                )
                InfoRow("格式", "MP3 / AAC")
                InfoRow("所属专辑", current.album, if (albumLink) openAlbum else null)
                InfoRow("专辑曲目", "第 ${current.trackNo} 首")
                InfoRow(
                    "艺术家",
                    current.artist,
                    if (artistOptions.isNotEmpty()) ({ openArtistOptions() }) else null
                )
                InfoRow("歌曲 ID", "${current.id}")
            }
        }

        if (stickyAlpha > 0.01f) {
            if (state.detailTopStyle == DetailTopStyle.GradientBlur) {
                // Haze directly blurs the page content captured by the scrolling
                // column above. There is no solid/gradient header background here.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(hazeHeight)
                        .graphicsLayer { alpha = stickyAlpha }
                ) {
                    // Keep the Haze effect as a background sibling. Putting the
                    // controls inside the hazeBlur node also blurs the controls.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .hazeBlur(
                                input = HazeInput.Sources(hazeState),
                                style = hazeStyle
                            )
                    )
                    // Darkening scrim: blurred bright glyphs stay shape-readable
                    // without it, which looked like "sharp text + halo".
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = (.55f * state.hazeScrim * 2f).coerceIn(0f, 1f)),
                                    .70f to Color.Black.copy(alpha = (.38f * state.hazeScrim * 2f).coerceIn(0f, 1f)),
                                    1f to Color.Transparent
                                )
                            )
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = statusBarDp.dp)
                            .height(64.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircleButton(CircleKind.Back, onBack, Modifier.size(38.dp))
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))) {
                            CoverImage(coverUrl, current.id, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(10.dp))
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Txt(
                                current.name,
                                AS.rowTitle().copy(fontSize = 14.sp),
                                Modifier.weight(1f, fill = false),
                                maxLines = 1
                            )
                            if (current.id in state.heartRecommendedIds) {
                                Spacer(Modifier.width(6.dp))
                                Txt(
                                    "心动推荐",
                                    AS.caption().copy(fontSize = 10.sp, color = AS.Blue),
                                    maxLines = 1
                                )
                            }
                        }
                        PlayPauseButton(playing, ::togglePlay, size = 32.dp, filled = true)
                    }
                }
            } else {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = stickyAlpha }
                        .background(AS.Card)
                        .height(64.dp + statusBarDp.dp)
                        .padding(top = statusBarDp.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleButton(CircleKind.Back, onBack, Modifier.size(38.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))) {
                        CoverImage(coverUrl, current.id, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Txt(
                            current.name,
                            AS.rowTitle().copy(fontSize = 14.sp),
                            Modifier.weight(1f, fill = false),
                            maxLines = 1
                        )
                        if (current.id in state.heartRecommendedIds) {
                            Spacer(Modifier.width(6.dp))
                            Txt(
                                "心动推荐",
                                AS.caption().copy(fontSize = 10.sp, color = AS.Blue),
                                maxLines = 1
                            )
                        }
                    }
                    PlayPauseButton(playing, ::togglePlay, size = 32.dp, filled = true)
                }
            }
        }

        if (artistPickerOpen) {
            ArtistChoiceDialog(
                artists = artistOptions,
                onDismiss = { artistPickerOpen = false },
                onPick = { artist ->
                    artistPickerOpen = false
                    onOpenArtist(artist.id, artist.name)
                }
            )
        }
    }
}


/**
 * The lyric row is the only part of the detail page that changes with playback
 * progress, so it reads `state.positionMs` **inside** this composable: otherwise every
 * progress tick would recompose the whole page (hero art, header, comments…).
 */
@Composable
private fun DetailLyricCards(
    current: Song,
    lines: List<LyricLine>,
    cardPalette: List<Color>,
    isCurrent: Boolean,
    onSeek: (Long) -> Unit,
    onLongPress: () -> Unit
) {
    val state = MusicServices.state
    LyricCards(
        lines = lines,
        positionMs = state.positionMs,
        wordByWord = state.wordByWord,
        playing = state.playing && isCurrent,
        synthWords = state.lyricSynthWords,
        keepSung = state.lyricKeepSung,
        showTranslation = state.showTranslation,
        glow = state.lyricGlow,
        fontSp = state.lyricFont,
        translationSp = state.translationFont,
        palette = cardPalette,
        songSeed = current.id,
        flow = state.lyricFlow,
        onSeek = onSeek,
        onLongPress = onLongPress,
        modifier = Modifier.padding(start = 16.dp)
    )
}

@Composable
private fun ArtistChoiceDialog(
    artists: List<ArtistBrief>,
    onDismiss: () -> Unit,
    onPick: (ArtistBrief) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AS.CardElevated)
                .padding(vertical = 8.dp)
        ) {
            Txt(
                "选择艺术家",
                AS.rowTitle().copy(fontSize = 17.sp),
                Modifier.padding(start = 18.dp, top = 10.dp, bottom = 8.dp)
            )
            artists.forEach { artist ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .tap { onPick(artist) }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Txt(artist.name, AS.body(), Modifier.weight(1f), maxLines = 1)
                    ChevronRight(size = 12.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Fixed four-cell stats row under the cover: 风格 / 红心 / 评论 / 专辑曲目.
 * The heart and comment counts come from Netease when reachable; local metadata
 * is only a fallback. No scrolling — the four cells always divide the width evenly.
 */
@Composable
private fun SongStatsRow(song: Song, social: SongSocial?, commentTotal: Int) {
    val likeText = when {
        social?.hasLike == true -> formatCount(social.likeCount)
        song.favoriteCount > 0 -> formatCount(song.favoriteCount)
        else -> "—"
    }
    val commentText = formatCount(commentTotal)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatCell("风格", song.genre, Modifier.weight(1f))
        DividerV()
        StatCell("红心", likeText, Modifier.weight(1f))
        DividerV()
        StatCell("评论", commentText, Modifier.weight(1f))
        DividerV()
        StatCell("专辑曲目", "第 ${song.trackNo} 首", Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.height(88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Txt(label.uppercase(), AS.caption().copy(fontSize = 10.sp), maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Txt(value, AS.body().copy(fontSize = 19.sp), maxLines = 1)
    }
}

@Composable
private fun DividerV() {
    Box(Modifier.width(1.dp).height(52.dp).background(AS.Separator))
}

@Composable
private fun CommentCard(comment: CommentItem, onOpen: () -> Unit) {
    var truncated by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(300.dp)
            .height(178.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AS.Card)
            .tap { if (truncated) onOpen() }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteAvatar(
                url = comment.avatarUrl,
                seed = comment.user.hashCode().toLong(),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Txt(comment.user, AS.rowTitle().copy(fontSize = 15.sp), Modifier.weight(1f), maxLines = 1)
            Txt(comment.time.ifBlank { "热门评论" }, AS.caption())
        }
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = comment.content,
            style = AS.body().copy(fontSize = 15.sp, lineHeight = 21.sp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { truncated = it.hasVisualOverflow }
        )
        Spacer(Modifier.weight(1f))
        Txt("♡ ${formatCount(comment.liked)}", AS.caption().copy(color = AS.Text3))
    }
}
