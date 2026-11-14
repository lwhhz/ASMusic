package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val CoverPalettes: List<List<Color>> = listOf(
    listOf(Color(0xFF6D5BFF), Color(0xFF241452)),
    listOf(Color(0xFF38BDF8), Color(0xFF123C6B)),
    listOf(Color(0xFFFB7185), Color(0xFF5B1638)),
    listOf(Color(0xFFFBBF24), Color(0xFF7A3A0B)),
    listOf(Color(0xFF34D399), Color(0xFF0C4A3C)),
    listOf(Color(0xFFF472B6), Color(0xFF4A1D65)),
    listOf(Color(0xFF60A5FA), Color(0xFF312E81)),
    listOf(Color(0xFFA3E635), Color(0xFF365314))
)

/** Network cover with an original gradient placeholder while loading / offline. */
@Composable
fun CoverImage(
    url: String?,
    seed: Long,
    modifier: Modifier = Modifier,
    requestEdge: Int = 512
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = if (url.isNullOrBlank()) null else {
            runCatching { MusicServices.bridge.image(url, requestEdge) }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp, null, modifier, contentScale = ContentScale.Crop)
    } else {
        OriginalCover(seed, modifier)
    }
}

@Composable
fun OriginalCover(seed: Long, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawCover(seed) }
}

/**
 * Circular avatar fetched from the network (account avatar, comment avatars).
 * Falls back to an original gradient portrait while loading / offline.
 */
@Composable
fun RemoteAvatar(url: String?, seed: Long, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = if (url.isNullOrBlank()) null else {
            runCatching { MusicServices.bridge.image(url) }.getOrNull()
        }
    }
    Box(modifier.clip(CircleShape)) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Canvas(Modifier.fillMaxSize()) { drawAvatarPlaceholder(seed) }
        }
    }
}

private fun DrawScope.drawAvatarPlaceholder(seed: Long) {
    val w = size.width
    val h = size.height
    val palette = CoverPalettes[(kotlin.math.abs(seed) % CoverPalettes.size).toInt()]
    drawCircle(
        brush = Brush.linearGradient(palette, Offset.Zero, Offset(w, h)),
        radius = w * .5f,
        center = Offset(w * .5f, h * .5f)
    )
    val glyph = Color.White.copy(.86f)
    drawCircle(glyph, w * .16f, Offset(w * .5f, h * .37f))
    drawRoundRect(
        glyph,
        topLeft = Offset(w * .22f, h * .62f),
        size = Size(w * .56f, h * .44f),
        cornerRadius = CornerRadius(w * .28f, w * .28f)
    )
}

private fun DrawScope.drawCover(seed: Long) {
    val w = size.width
    val h = size.height
    val s = kotlin.math.abs(seed)
    val palette = CoverPalettes[(s % CoverPalettes.size).toInt()]
    val radius = w * .18f
    drawRoundRect(
        brush = Brush.linearGradient(palette, Offset.Zero, Offset(w, h)),
        cornerRadius = CornerRadius(radius, radius)
    )
    when ((s / CoverPalettes.size % 3).toInt()) {
        0 -> { // vinyl
            drawCircle(Color.White.copy(.13f), w * .33f, Offset(w * .5f, h * .5f))
            drawCircle(Color.Black.copy(.24f), w * .25f, Offset(w * .5f, h * .5f))
            drawCircle(palette.last().copy(.92f), w * .11f, Offset(w * .5f, h * .5f))
            drawCircle(Color.White.copy(.82f), w * .028f, Offset(w * .5f, h * .5f))
        }
        1 -> { // waveform
            for (i in 0..6) {
                val bh = h * (.20f + ((s.toInt() + i) % 5) * .085f)
                drawRoundRect(
                    Color.White.copy(.38f),
                    topLeft = Offset(w * (.16f + i * .105f), (h - bh) / 2f),
                    size = Size(w * .055f, bh),
                    cornerRadius = CornerRadius(w * .027f, w * .027f)
                )
            }
        }
        else -> { // note
            drawCircle(Color.White.copy(.88f), w * .11f, Offset(w * .37f, h * .67f))
            drawRoundRect(
                Color.White.copy(.88f),
                topLeft = Offset(w * .45f, h * .18f),
                size = Size(w * .065f, h * .52f),
                cornerRadius = CornerRadius(w * .03f, w * .03f)
            )
            val flag = Path().apply {
                moveTo(w * .50f, h * .18f)
                lineTo(w * .80f, h * .28f)
                lineTo(w * .80f, h * .42f)
                lineTo(w * .50f, h * .32f)
                close()
            }
            drawPath(flag, Color.White.copy(.88f))
        }
    }
}

@Composable
fun MusicTabIcon(
    index: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val icon = when (index) {
        0 -> Iconoir.tabToday
        1 -> Iconoir.tabNew
        2 -> Iconoir.tabMine
        3 -> Iconoir.tabStats
        else -> Iconoir.tabSearch
    }
    androidx.compose.foundation.Image(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.then(Modifier.size(size * .86f)),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color)
    )
}



/** Fallback when the cover cannot be sampled / has fewer than four usable colors. */
private val LyricFallbackPalette: List<Color> = listOf(
    Color(0xFF2A3A6B),
    Color(0xFF5B2A55),
    Color(0xFF1F5A5A),
    Color(0xFF5A3A22)
)

/** Convert a Compose color to HSV (h degrees, s/v 0..1). */
private fun Color.toHsv(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d <= 0.00001f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    val s = if (max <= 0.00001f) 0f else d / max
    return floatArrayOf((h + 360f) % 360f, s, max)
}

/** Hue-shift and saturate an extracted color so cards are vivid, not muddy. */
private fun vividColor(color: Color, hueShift: Float, satBoost: Float, valueScale: Float): Color {
    val hsv = color.toHsv()
    return Color.hsv(
        ((hsv[0] + hueShift) % 360f + 360f) % 360f,
        (hsv[1] * satBoost + .08f).coerceIn(.55f, .95f),
        (hsv[2] * valueScale + .08f).coerceIn(.38f, .92f)
    )
}

/**
 * Four colors for one lyric card. The cover palette supplies the source hues, but
 * every color is saturation-boosted and the card gets a large golden-angle hue
 * offset. Different cards therefore read as different colors instead of four
 * near-identical copies of the same cover.
 */
fun lyricCardColors(palette: List<Color>, songSeed: Long, index: Int): List<Color> {
    val source = if (palette.isNotEmpty()) palette else LyricFallbackPalette
    val base = source[0]
    // 封面主色 + 受控随机：不仅换色相，还改变「色相跨度 / 取色组合 / 明暗主导」，
    // 让相邻卡片的渐变色结构本身不同，而不只是同一个渐变换个颜色。
    val seed = songSeed * 31L + index.toLong() * 0x9E3779B1L
    fun rnd(salt: Long): Float {
        var x = seed xor (salt * 0x85EBCA6BL)
        x = (x xor (x ushr 33)) * -0x6B2FB0B8AA2B4DB9L
        x = (x xor (x ushr 29)) * 0x2545F4914F6CDD1DL
        x = x xor (x ushr 32)
        return ((x ushr 11).toDouble() / (1L shl 53).toDouble()).toFloat().coerceIn(0f, 1f)
    }
    val hueShift = (rnd(1L) - .5f) * 40f          // ±20°
    val spread = 18f + rnd(2L) * 92f              // 18°~110°：相邻 / 三角 / 互补
    val satScale = 0.85f + rnd(3L) * 0.35f
    val valueShift = -0.28f + rnd(4L) * 0.38f
    val pickA = source[(1 + (rnd(5L) * (source.size - 1)).toInt()) % source.size]
    val pickB = source[(1 + (rnd(6L) * (source.size - 1)).toInt()) % source.size]
    val darkFirst = rnd(7L) > 0.5f
    val l1 = if (darkFirst) 1.06f else 0.9f
    val l2 = if (darkFirst) 0.9f else 1.06f
    return listOf(
        vividColor(base, hueShift, satScale * 1.12f, l1 + valueShift * .6f),
        vividColor(pickA, hueShift + spread, satScale, l2 + valueShift * .5f),
        vividColor(base, hueShift - spread * .65f, satScale * .9f, .92f + valueShift * .4f),
        vividColor(pickB, hueShift + spread * 1.45f, satScale * 1.06f, 1.02f + valueShift * .3f)
    )
}

/** Background for one lyric card — cover-derived gradient, no third-party artwork. */
@Composable
fun LyricCardArt(
    colors: List<Color>,
    seed: Long,
    flowing: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!flowing) {
        Canvas(modifier) { drawLyricCard(colors, seed, 0f, animated = false) }
        return
    }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                time.floatValue = (now - start) / 1_000_000_000f
            }
        }
    }
    Canvas(modifier) {
        drawLyricCard(colors, seed, time.floatValue, animated = true)
    }
}

private fun DrawScope.drawLyricCard(
    colors: List<Color>,
    seed: Long,
    phase: Float,
    animated: Boolean
) {
    val w = size.width
    val h = size.height
    val s = kotlin.math.abs(seed).toFloat()
    val c0 = colors.getOrElse(0) { LyricFallbackPalette[0] }
    val c1 = colors.getOrElse(1) { LyricFallbackPalette[1] }
    val c2 = colors.getOrElse(2) { LyricFallbackPalette[2] }
    val c3 = colors.getOrElse(3) { LyricFallbackPalette[3] }
    val t = if (animated) phase else 0f

    fun wave(freq: Float, shift: Float): Float =
        kotlin.math.sin(t * freq + s * .017f + shift) * .5f + .5f

    // Procedural, non-commensurate waves. There is no short frozen loop: the
    // colors keep drifting through different hues and mixes as time advances.
    val baseA = vividColor(
        lerp(c0, c1, wave(.071f, 0f)),
        12f * (wave(.113f, 1.4f) - .5f),
        1.12f,
        .96f
    )
    val baseB = vividColor(
        lerp(c2, c3, wave(.097f, 2.1f)),
        -16f * (wave(.151f, 3.3f) - .5f),
        .98f,
        .84f
    )
    val baseC = vividColor(
        lerp(c1, c2, wave(.053f, 4.7f)),
        22f * (wave(.083f, 2.8f) - .5f),
        1.05f,
        .72f
    )
    drawRect(
        Brush.linearGradient(
            listOf(baseA, baseB, baseC),
            Offset(0f, 0f),
            Offset(w, h)
        )
    )

    val g1x = w * (.20f + .22f * kotlin.math.sin(t * .127f + s * .021f))
    val g1y = h * (.22f + .16f * kotlin.math.cos(t * .101f + s * .013f))
    val glowA = vividColor(c3, 140f * wave(.061f, 1.9f), 1.5f, 1.0f)
    drawCircle(
        Brush.radialGradient(
            listOf(glowA.copy(alpha = .40f), Color.Transparent),
            Offset(g1x, g1y),
            w * .70f
        ),
        w * .70f,
        Offset(g1x, g1y)
    )

    val g2x = w * (.76f + .18f * kotlin.math.cos(t * .089f + s * .031f))
    val g2y = h * (.64f + .16f * kotlin.math.sin(t * .117f + s * .019f))
    val glowB = vividColor(c0, 70f * wave(.073f, 4.2f), 1.35f, .92f)
    drawCircle(
        Brush.radialGradient(
            listOf(glowB.copy(alpha = .34f), Color.Transparent),
            Offset(g2x, g2y),
            w * .62f
        ),
        w * .62f,
        Offset(g2x, g2y)
    )

    val g3x = w * (.48f + .26f * kotlin.math.sin(t * .043f + s * .011f))
    val g3y = h * (.42f + .20f * kotlin.math.cos(t * .059f + s * .027f))
    val glowC = vividColor(c2, 24f * wave(.037f, 2.5f), 1.25f, .82f)
    drawCircle(
        Brush.radialGradient(
            listOf(glowC.copy(alpha = .26f), Color.Transparent),
            Offset(g3x, g3y),
            w * .52f
        ),
        w * .52f,
        Offset(g3x, g3y)
    )

    // A slowly moving color veil, not a repeating white sheen.
    val veilShift = (wave(.029f, 1.1f) - .5f) * w * .7f
    drawRect(
        Brush.linearGradient(
            listOf(glowA.copy(alpha = .18f), Color.Transparent, glowC.copy(alpha = .14f)),
            Offset(veilShift, 0f),
            Offset(w - veilShift, h)
        )
    )

    for (i in 0..7) {
        drawLine(
            Color.White.copy(.045f),
            Offset(0f, h * (.12f + i * .11f)),
            Offset(w, h * (.12f + i * .11f)),
            1f
        )
    }
}
/** iOS-style bar chart used on the listening-stats page. */
@Composable
fun BarChart(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    val growth = remember(values) { Animatable(0f) }
    LaunchedEffect(values) {
        growth.snapTo(0f)
        growth.animateTo(1f, tween(780, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val max = (values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val step = if (values.isEmpty()) w else w / values.size
        values.forEachIndexed { i, v ->
            val bh = h * (v / max) * .92f * growth.value
            drawRoundRect(
                color.copy(alpha = .35f + .65f * (v / max)),
                topLeft = Offset(i * step + step * .18f, h - bh),
                size = Size(step * .64f, bh),
                cornerRadius = CornerRadius(step * .32f, step * .32f)
            )
        }
    }
}
