package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.Txt
import com.kyant.backdrop.catalog.appstore.tap

@Composable
fun PlayPauseButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    filled: Boolean = false,
    tint: Color = Color.White
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) AS.Blue else AS.CardElevated)
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            imageVector = if (playing) Iconoir.pause else Iconoir.play,
            contentDescription = null,
            modifier = Modifier.size(size * .52f),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)
        )
    }
}

/**
 * Measuring text is one of the more expensive parts of a list row; the same titles are
 * measured again every time a row scrolls back into view, so keep a tiny LRU by text.
 */
private val textMeasureCache =
    object : LinkedHashMap<String, androidx.compose.ui.text.TextLayoutResult>(64, .75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, androidx.compose.ui.text.TextLayoutResult>?
        ) = size > 96
    }

private fun measureCached(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: androidx.compose.ui.text.TextStyle
): androidx.compose.ui.text.TextLayoutResult {
    val key = text + "|" + style.fontSize.value + "|" + (style.fontWeight?.weight ?: -1)
    textMeasureCache[key]?.let { return it }
    return measurer.measure(AnnotatedString(text), style).also { textMeasureCache[key] = it }
}

/**
 * 单行文字：放得下就静态显示，放不下才从左到右循环滚动。
 * 用于歌曲名等可能超长的文本，替代直接截断。
 */
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    speedDpPerSecond: Float = 36f
) {
    val measurer = rememberTextMeasurer()
    val layout = remember(text, style) { measureCached(measurer, text, style) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val density = LocalDensity.current
        val containerPx = constraints.maxWidth.toFloat()
        val textPx = layout.size.width.toFloat()
        if (textPx <= containerPx || containerPx <= 0f) {
            BasicText(text = text, style = style, maxLines = 1)
        } else {
            // Continuous modulo scroll: the second copy slides in exactly where the
            // first one left, so the loop never pauses or jumps back.
            val gapPx = with(density) { 32.dp.toPx() }
            val cycle = textPx + gapPx
            val speedPx = with(density) { speedDpPerSecond.dp.toPx() }.coerceAtLeast(1f)
            var offset by remember(text, textPx) { mutableFloatStateOf(0f) }
            LaunchedEffect(text, textPx, containerPx) {
                var last = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now ->
                        val dt = (now - last) / 1_000_000_000f
                        last = now
                        offset = (offset + dt * speedPx) % cycle
                    }
                }
            }
            // requiredWidth: without it the parent constraint squeezes the second copy
            // to zero width, which leaves a blank gap and reads as a restart.
            val rowWidth = with(density) { (textPx * 2f + gapPx).toDp() }
            Row(
                Modifier
                    .requiredWidth(rowWidth)
                    .offset { IntOffset(-offset.roundToInt(), 0) }
            ) {
                BasicText(text = text, style = style, maxLines = 1)
                Spacer(Modifier.size(32.dp))
                BasicText(text = text, style = style, maxLines = 1)
            }
        }
    }
}
@Composable
fun SongRow(
    song: Song,
    playing: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .tap(onOpen),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                CoverImage(song.coverUrl, song.id, Modifier.fillMaxSize(), requestEdge = 256)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                MarqueeText(song.name, AS.rowTitle())
                Txt("${song.artist} · ${song.album}", AS.subtitle().copy(fontSize = 13.sp), maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            PlayPauseButton(playing, onPlay, size = 32.dp)
        }
        if (divider) {
            Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
        }
    }
}


/**
 * iOS-style depth for caption areas: a blurred copy of the artwork sitting behind the
 * dark gradient, so the text melts into the cover instead of sitting on a flat scrim.
 *
 * 原来是"一整块固定半径的高斯模糊铺满矩形"：带内一个模糊强度、带外完全清晰，
 * 上沿留下一条水平硬边，看起来像在清晰图上贴了一块毛玻璃。
 *
 * 现在给模糊层加一条垂直 alpha 蒙版（`DstIn`），并把曲线做成缓入缓出：带的下部
 * 完全不透明，往上 alpha 缓慢衰减到 0，和上面的清晰封面连续衔接，不再有分界线。
 *
 * 这里 `blur` 故意放在 `drawWithContent` 外层：让高斯先作用在"已经带蒙版的图层"
 * 上，蒙版的硬台阶会被一起抹开，过渡比"先模糊再蒙版"更柔。
 * （试过叠三层不同半径做真·渐进模糊：DstIn 会让每层都变成半透明，摞起来会发灰、
 * 露出下面的暗色遮罩形成色块，所以保持单层。）
 */
@Composable
private fun ArtworkBlurBand(
    url: String?,
    seed: Long,
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().height(height)) {
        CoverImage(
            url,
            seed,
            Modifier
                .fillMaxWidth()
                // 向上溢出一段再交给 alpha 蒙版淡出：过渡区不局限在带高之内，
                // 起点被推到卡片更高的位置，清晰↔模糊的转折更不容易被看出来。
                .height(height * 1.35f)
                .align(Alignment.BottomCenter)
                .blur(42.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.00f to Color.Black.copy(alpha = 0.00f),
                            0.30f to Color.Black.copy(alpha = 0.14f),
                            0.58f to Color.Black.copy(alpha = 0.58f),
                            0.78f to Color.Black.copy(alpha = 0.94f),
                            1.00f to Color.Black
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            // 反正要糊，按小图解码：显存和模糊采样量都降一档。
            requestEdge = 256
        )
    }
}

private fun playlistEyebrow(kind: PlaylistKind): String = when (kind) {
    PlaylistKind.Daily -> "DAILY RECOMMEND"
    PlaylistKind.PrivateFm -> "PRIVATE FM"
    PlaylistKind.Liked -> "MY FAVORITES"
    PlaylistKind.Exclusive -> "FOR YOU"
    PlaylistKind.NewRelease -> "NEW RELEASES"
    else -> "PLAYLIST"
}

/** Today card: the first one uses the larger hero layout. */
@Composable
fun PlaylistCard(
    playlist: Playlist,
    isFirst: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val first = playlist.songs.firstOrNull()
    Box(
        modifier
            .fillMaxWidth()
            .height(if (isFirst) 360.dp else 220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AS.Card)
            .tap(onClick)
    ) {
        if (isFirst && first != null) {
            CoverImage(first.coverUrl, first.id, Modifier.fillMaxSize())
            ArtworkBlurBand(
                first.coverUrl,
                first.id,
                170.dp,
                Modifier.align(Alignment.BottomCenter)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    // leave room for the 46dp play button so long titles never sit under it
                    .padding(start = 20.dp, end = 84.dp, top = 20.dp, bottom = 20.dp)
            ) {
                Txt(playlistEyebrow(playlist.kind), AS.eyebrow().copy(color = Color.White.copy(.82f)))
                Spacer(Modifier.height(8.dp))
                MarqueeText(first.name, AS.cardTitle().copy(fontSize = 28.sp, color = Color.White))
                Txt("${first.artist} · 共 ${playlist.songCount} 首", AS.subtitle().copy(color = Color.White.copy(.8f)), maxLines = 1)
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                PlayPauseButton(false, onPlay, size = 46.dp, filled = true)
            }
        } else {
            // Prefer the real playlist cover; falls back to original art when empty.
            CoverImage(playlist.coverUrl ?: first?.coverUrl, playlist.id, Modifier.fillMaxSize())
            ArtworkBlurBand(
                playlist.coverUrl ?: first?.coverUrl,
                playlist.id,
                130.dp,
                Modifier.align(Alignment.BottomCenter)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            )
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Txt(
                    playlistEyebrow(playlist.kind),
                    AS.eyebrow().copy(color = Color.White.copy(.8f))
                )
                Spacer(Modifier.height(8.dp))
                Txt(playlist.name, AS.cardTitle().copy(color = Color.White), maxLines = 2)
                Txt(playlist.subtitle, AS.subtitle().copy(color = Color.White.copy(.78f)), maxLines = 1)
            }
            if (first != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) { CoverImage(first.coverUrl, first.id, Modifier.fillMaxSize()) }
            }
        }
    }
}

/**
 * Games-style feature slide for a new album / single: eyebrow + title + subtitle
 * above a large cover card with an overlay info bar, laid out horizontally.
 */
@Composable
fun ReleaseSlide(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val first = playlist.songs.firstOrNull()
    Column(modifier) {
        Txt(
            when {
                playlist.songs.isEmpty() -> "NEW ALBUM"
                playlist.songCount > 1 -> "NEW RELEASE"
                else -> "NEW SINGLE"
            },
            AS.eyebrow()
        )
        Spacer(Modifier.height(6.dp))
        MarqueeText(playlist.name, AS.cardTitle().copy(fontSize = 26.sp, lineHeight = 31.sp))
        Txt(playlist.subtitle, AS.subtitle().copy(fontSize = 18.sp), maxLines = 1)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(AS.CardRadius))
                .background(AS.Card)
                .tap(onClick)
        ) {
            CoverImage(playlist.coverUrl ?: first?.coverUrl, playlist.id, Modifier.fillMaxSize())
            ArtworkBlurBand(
                playlist.coverUrl ?: first?.coverUrl,
                playlist.id,
                96.dp,
                Modifier.align(Alignment.BottomCenter)
            )
            // Same treatment as the home big card, but the darkening only starts
            // below the midpoint so the artwork keeps its brightness.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.80f to Color(0x4D000000),
                            1.00f to Color(0xE6000000)
                        )
                    )
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Txt(first?.name ?: playlist.name, AS.rowTitle().copy(fontSize = 16.sp), maxLines = 1)
                    Txt(
                        if (playlist.songCount > 1) "${playlist.subtitle} · ${playlist.songCount} 首"
                        else playlist.subtitle,
                        AS.subtitle().copy(fontSize = 13.sp),
                        maxLines = 1
                    )
                }
                PlayPauseButton(false, onPlay, size = 34.dp, filled = true)
            }
        }
    }
}

@Composable
fun PlaylistTile(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .tap(onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))) {
            CoverImage(
                playlist.coverUrl ?: playlist.songs.firstOrNull()?.coverUrl,
                playlist.id,
                Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(playlist.name, AS.rowTitle(), maxLines = 1)
            Txt(playlist.subtitle, AS.subtitle().copy(fontSize = 13.sp), maxLines = 1)
        }
        Txt("${playlist.songCount} 首", AS.caption())
    }
    Box(Modifier.padding(start = 78.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
}

@Composable
fun StatCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AS.Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Txt(label, AS.caption())
        Txt(value, AS.largeTitle().copy(fontSize = 30.sp), maxLines = 1)
        Txt(unit, AS.caption())
    }
}

@Composable
fun RankRow(
    rank: Int,
    title: String,
    subtitle: String,
    count: Int,
    maxCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt("$rank", AS.rowTitle().copy(fontSize = 20.sp, color = AS.Blue), Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, AS.rowTitle().copy(fontSize = 15.sp), maxLines = 1)
            Spacer(Modifier.height(6.dp))
            val barProgress by animateFloatAsState(
                targetValue = (count.toFloat() / maxCount.coerceAtLeast(1)).coerceIn(0f, 1f),
                animationSpec = tween(700),
                label = "rank-bar"
            )
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(AS.CardElevated)) {
                Box(
                    Modifier
                        .fillMaxWidth(barProgress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AS.Blue)
                )
            }
            Txt(subtitle, AS.caption().copy(fontSize = 10.sp), maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Txt(formatCount(count), AS.caption())
    }
}
