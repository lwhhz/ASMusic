package com.kyant.backdrop.catalog.md3

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import com.kyant.backdrop.catalog.appstore.music.Playlist
import com.kyant.backdrop.catalog.appstore.music.Song
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ---------------------------------------------------------------------------
// 通用小工具
// ---------------------------------------------------------------------------

/** 12800 → 1.3万；用于播放量/评论数这类大数字。 */
fun md3Count(n: Int): String = when {
    n < 0 -> "-"
    n < 10_000 -> n.toString()
    n < 100_000_000 -> {
        val v = n / 10_000.0
        if (v >= 100) "${v.toInt()}万" else String.format("%.1f万", v)
    }
    else -> String.format("%.1f亿", n / 100_000_000.0)
}

/**
 * 去掉 LRC 行首的时间戳。
 *
 * 有些音源（脚本回退到纯 LRC 时）返回的 text 里带着 [00:15.20]，
 * 直接渲染会把时间戳显示进歌词。这里统一清洗；纯时间轴行返回空串，
 * 由调用方退化成「♪」。
 */
private val LrcTag = Regex("^\\s*(\\[\\d{1,3}:\\d{1,2}([.:]\\d{1,3})?\\])+")

fun cleanLyric(text: String): String {
    val stripped = text.replace(LrcTag, "").trim()
    return if (stripped.length < text.trim().length) stripped else text.trim()
}

fun md3Time(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return if (m >= 60) "${m / 60}:${(m % 60).toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

/**
 * 按下时轻微收缩 + 点击，M3 Expressive 的「形状回应」。
 *
 * 刻意把点击和形变放在同一个 modifier 里：如果拆成 pressScale().clickable{}，
 * 两层 pointerInput 会互相抢事件，表现为「点了没反应」。
 */
@Composable
fun Modifier.md3Clickable(
    enabled: Boolean = true,
    scaleTo: Float = .965f,
    onClick: () -> Unit
): Modifier = md3SurfaceClick(
    onClick = onClick,
    shape = androidx.compose.ui.graphics.RectangleShape,
    enabled = enabled,
    scaleTo = scaleTo
)

// ---------------------------------------------------------------------------
// 封面
// ---------------------------------------------------------------------------

@Composable
fun Md3Cover(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp? = null,
    shape: androidx.compose.ui.graphics.Shape? = null,
    alpha: Float = 1f
) {
    val bridge = MusicServices.bridge
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url) {
        value = if (url.isNullOrBlank()) null else runCatching {
            bridge.image(url, 512)
        }.getOrNull()
    }
    val coverCorner = LocalMd3Tokens.current.config?.coverCorner ?: true
    val defaultCorner = if (coverCorner) 12.dp else 0.dp
    val resolvedShape = shape ?: RoundedCornerShape(corner ?: defaultCorner)
    Box(
        modifier
            .clip(resolvedShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp,
                contentDescription = null,
                alpha = alpha,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f),
                modifier = Modifier.fillMaxSize(.34f)
            )
        }
    }
}

/** 封面做背景时的模糊+压暗层，用于 hero 卡片与播放页。 */
@Composable
fun Md3CoverBackdrop(url: String?, modifier: Modifier = Modifier, darken: Float = .55f) {
    Box(modifier.background(MaterialTheme.colorScheme.surface)) {
        Md3Cover(url, Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = darken),
                            MaterialTheme.colorScheme.surface.copy(alpha = .82f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )
    }
}

// ---------------------------------------------------------------------------
// 媒体卡片 / 歌曲行
// ---------------------------------------------------------------------------

@Composable
fun Md3MediaCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 156.dp
) {
    val tokens = LocalMd3Tokens.current
    Column(
        modifier
            .width(width)
            .clip(MaterialTheme.shapes.large)
            .md3Clickable(onClick = onClick)
            .padding(bottom = tokens.gap(6))
    ) {
        Box {
            Md3Cover(
                playlist.coverUrl,
                Modifier.fillMaxWidth().aspectRatio(1f),
                shape = MaterialTheme.shapes.large
            )
            if (playlist.songCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = .55f),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(
                        "${md3Count(playlist.songCount)} 首",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(tokens.gap(8)))
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        if (!playlist.subtitle.isNullOrBlank()) {
            Text(
                playlist.subtitle!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 列表型歌曲行：序号 / 波形 / 标题 / 歌手 / 时长 / 更多。 */
@Composable
fun Md3SongRow(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    showIndex: Boolean = true,
    modifier: Modifier = Modifier
) {
    val tokens = LocalMd3Tokens.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .md3Clickable(scaleTo = .98f, onClick = onClick)
            .padding(horizontal = tokens.gap(8), vertical = tokens.gap(8)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIndex) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    Icon(
                        if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.Equalizer,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        index.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(tokens.gap(8)))
        }
        Md3Cover(song.coverUrl, Modifier.size(48.dp), corner = 10.dp)
        Spacer(Modifier.width(tokens.gap(12)))
        Column(Modifier.weight(1f)) {
            Text(
                song.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (song.durationMs > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                md3Time(song.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
        if (onMore != null) {
            IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "更多",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun Md3SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    val tokens = LocalMd3Tokens.current
    Row(
        modifier.fillMaxWidth().padding(bottom = tokens.gap(6)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun Md3EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.MusicNote,
    title: String,
    desc: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = .6f),
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (desc != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 加载占位：三条呼吸的骨架条。 */
@Composable
fun Md3SkeletonList(rows: Int = 6, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = .28f,
        targetValue = .6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-alpha"
    )
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(.55f)
                            .height(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(.32f)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Expressive 波形进度条
// ---------------------------------------------------------------------------

/**
 * 波浪进度：M3 Expressive 的进度表达。播放时波形缓慢流动，
 * 暂停时退化成一条直线——动效只在「有东西在动」时存在。
 */
@Composable
fun Md3WaveProgress(
    progress: Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
    waveHeight: Dp = 5.dp,
    trackAlpha: Float = .22f
) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2200, easing = androidx.compose.animation.core.LinearEasing)),
        label = "wave-phase"
    )
    val tokens = LocalMd3Tokens.current
    val amp = if (playing && tokens.motion != MotionLevel.Off) 1f else 0f
    Canvas(modifier.fillMaxWidth().height(waveHeight * 2.4f)) {
        val cy = size.height / 2f
        val w = size.width
        val p = progress.coerceIn(0f, 1f)

        // 轨道
        drawLine(
            color = colors.onSurface.copy(alpha = trackAlpha),
            start = Offset(0f, cy),
            end = Offset(w, cy),
            strokeWidth = size.height * .26f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        if (p <= 0f) return@Canvas

        val path = Path()
        val steps = 64
        val amplitude = size.height * .30f * amp
        for (i in 0..steps) {
            val x = w * p * i / steps
            val y = cy + amplitude * sin(i.toFloat() / steps * 6f * PI.toFloat() + phase) *
                sin(i.toFloat() / steps * PI.toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color = colors.primary,
            style = Stroke(width = size.height * .26f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

// ---------------------------------------------------------------------------
// 迷你播放器
// ---------------------------------------------------------------------------

@Composable
fun Md3MiniPlayer(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    val song = state.currentSong ?: return
    val colors = MaterialTheme.colorScheme
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .md3SurfaceClick(onClick = onOpen, shape = MaterialTheme.shapes.large, scaleTo = .99f),
        color = colors.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Md3Cover(song.coverUrl, Modifier.size(44.dp), corner = 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { bridge.toggle() }) {
                    Icon(
                        if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.playing) "暂停" else "播放",
                        tint = colors.onSurface
                    )
                }
                IconButton(onClick = { bridge.next() }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", tint = colors.onSurface)
                }
            }
            if (LocalMd3Tokens.current.config?.showWaveform != false) {
                Md3WaveProgress(
                    progress = progress,
                    playing = state.playing,
                    modifier = Modifier.padding(horizontal = 10.dp).padding(bottom = 6.dp),
                    waveHeight = 2.dp
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(colors.onSurface.copy(alpha = .18f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxSize()
                            .background(colors.primary)
                    )
                }
            }
        }
    }
}

/** 可横向拖动的播放进度条（播放页用）。自绘是为了让进度形状跟随圆角/波形风格。 */
@Composable
fun Md3SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val value = if (dragging) dragValue else {
        if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    }
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        dragValue = f
                        dragging = false
                        onSeek((f * durationMs).toLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            onSeek((dragValue * durationMs).toLong())
                        }
                    ) { change, _ ->
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragValue = f
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val cy = size.height / 2f
                val sw = 6.dp.toPx()
                drawLine(
                    color = colors.onSurface.copy(alpha = .18f),
                    start = Offset(0f, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = sw,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                if (value > 0f) {
                    drawLine(
                        color = colors.primary,
                        start = Offset(0f, cy),
                        end = Offset(size.width * value, cy),
                        strokeWidth = sw,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    drawCircle(
                        color = colors.primary,
                        radius = 7.dp.toPx(),
                        center = Offset(size.width * value, cy)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(md3Time(positionMs), style = MaterialTheme.typography.labelSmall)
            Text(md3Time(durationMs), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** 灰度图标着色，用于「禁用/次级」语义场景。 */
@Composable
fun Md3DimIcon(
    vector: androidx.compose.ui.graphics.vector.ImageVector,
    alpha: Float = .55f,
    size: Dp = 20.dp
) {
    Icon(
        vector,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        modifier = Modifier.size(size)
    )
}

/** 顶部渐变遮罩，让内容滚动到状态栏下面时依然可读。 */
@Composable
fun Md3TopFade(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface.copy(alpha = .0f)
                    )
                )
            )
    )
}

/** 给字符串做灰度处理的 ColorFilter（用于禁用态封面）。 */
val Md3GrayFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(0.15f) }
)

@Composable
fun Md3Badge(text: String, color: Color = MaterialTheme.colorScheme.primaryContainer) {
    Surface(color = color, shape = CircleShape) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun Md3Pill(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (selected) colors.secondaryContainer else Color.Transparent,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        shape = CircleShape,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(
            1.dp, colors.outlineVariant
        ),
        modifier = Modifier
            .clip(CircleShape)
            .md3Clickable(scaleTo = .94f, onClick = onClick)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/** 横向内容轨道，统一内边距与间距。 */
@Composable
fun Md3Rail(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    spacing: Dp = 12.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/** 长标题滚动（播放页用）。 */
@Composable
fun Md3MarqueeText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    Text(
        text,
        style = style,
        maxLines = 1,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            repeatDelayMillis = 1800,
            initialDelayMillis = 1200,
            velocity = 34.dp
        )
    )
}

/** 进度环（统计页用）。 */
@Composable
fun Md3Ring(
    progress: Float,
    size: Dp,
    stroke: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            drawArc(
                color = colors.onSurface.copy(alpha = .10f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                topLeft = Offset(sw / 2, sw / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw)
            )
            drawArc(
                color = colors.primary,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                topLeft = Offset(sw / 2, sw / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw)
            )
        }
        content()
    }
}

/** 均分柱状图（统计页）。 */
@Composable
fun Md3Bars(values: List<Int>, modifier: Modifier = Modifier, height: Dp = 96.dp) {
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            val frac = v.toFloat() / max
            val highlight = i == values.indices.maxByOrNull { values[it] }
            Box(
                Modifier
                    .weight(1f)
                    .height((height * (0.08f + 0.92f * frac)))
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(
                        if (highlight) colors.primary
                        else colors.primary.copy(alpha = .28f + .28f * frac)
                    )
            )
        }
    }
}

/** 卡片表面的「色调抬升」+ 细边框，M3 里用 tonal elevation 而不是阴影。 */
@Composable
fun Md3SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = if (onClick != null) {
            modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick)
        } else modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        content = content
    )
}

@Composable
fun Md3Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
    )
}

/** 让文本宽度不超过给定比例时不被裁切的辅助：用于歌名+歌手两行。 */
@Composable
fun Md3TitleSubtitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 空格占位（避免到处 import Spacer + height）。 */
@Composable
fun Md3Gap(dp: Int) = Spacer(Modifier.height((dp * LocalMd3Tokens.current.density).dp))

@Composable
fun Md3GapW(dp: Int) = Spacer(Modifier.width((dp * LocalMd3Tokens.current.density).dp))

@Composable
fun Md3IconTile(
    vector: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .md3Clickable(scaleTo = .94f, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = tint.copy(alpha = .12f),
            shape = CircleShape,
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(vector, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** 避免未使用告警的小工具：判断两个浮点是否近似（动画化简用）。 */
fun near(a: Float, b: Float, eps: Float = 0.001f) = abs(a - b) < eps

@Composable
internal fun Modifier.md3Border(color: Color, width: Dp = 1.dp) =
    this.border(width, color, MaterialTheme.shapes.medium)

@Composable
fun Md3SectionNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        lineHeight = 18.sp
    )
}
