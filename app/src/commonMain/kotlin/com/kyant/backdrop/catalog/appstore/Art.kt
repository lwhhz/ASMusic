package com.kyant.backdrop.catalog.appstore

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import com.kyant.backdrop.catalog.appstore.music.RemoteAvatar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * All artwork is original, drawn with Compose Canvas primitives.
 * No Apple / third-party images or icons are reproduced (开发文档.md §8).
 */

@Composable
fun AppIcon(item: AppItem, modifier: Modifier = Modifier, size: Dp = 60.dp) {
    Canvas(sized(modifier, size)) { drawAppIcon(item.icon, item.accent) }
}

@Composable
fun AppIcon(key: IconKey, modifier: Modifier = Modifier, size: Dp = 60.dp) {
    Canvas(sized(modifier, size)) { drawAppIcon(key, iconColors(key)) }
}

private fun sized(modifier: Modifier, size: Dp): Modifier = modifier.then(Modifier.size(size))

private fun iconColors(key: IconKey): Pair<Color, Color> = when (key) {
    IconKey.TikTok -> Color(0xFF25F4EE) to Color(0xFFFE2C55)
    IconKey.YouTube -> Color(0xFFFF5B4C) to Color(0xFFCC0000)
    IconKey.Instagram -> Color(0xFFFEDA75) to Color(0xFFC13584)
    IconKey.SlapKings -> Color(0xFFA6E05B) to Color(0xFF2E7D32)
    IconKey.CrossyRoad -> Color(0xFF7BD7F0) to Color(0xFF2E8BC0)
    IconKey.SortIt -> Color(0xFF6FD3F5) to Color(0xFF1565C0)
    IconKey.SevenSins -> Color(0xFFFFC46B) to Color(0xFF7B1FA2)
    IconKey.Altos -> Color(0xFFFFC078) to Color(0xFF6442A6)
    IconKey.CutPaint -> Color(0xFF9AE6FA) to Color(0xFFE9C46A)
    IconKey.OverTake -> Color(0xFFFFE45C) to Color(0xFFF5A623)
    IconKey.Mowing -> Color(0xFFAED581) to Color(0xFF2E7D32)
    IconKey.SlapThat -> Color(0xFFFF6B6B) to Color(0xFFB71C1C)
    IconKey.Arcade -> Color(0xFF4FA9FF) to Color(0xFF5E5CE6)
}

private fun DrawScope.drawAppIcon(key: IconKey, colors: Pair<Color, Color>) {
    val w = size.width
    val h = size.height
    val r = w * 0.22f
    drawRoundRect(
        brush = Brush.linearGradient(listOf(colors.first, colors.second), Offset.Zero, Offset(w, h)),
        cornerRadius = CornerRadius(r, r)
    )
    val white = Color.White
    when (key) {
        IconKey.TikTok -> {
            drawRoundRect(Color(0xFF111114), cornerRadius = CornerRadius(r, r))
            drawNote(w * 0.44f, h * 0.60f, w * 0.085f, Color(0xFF25F4EE), -w * 0.035f, -h * 0.025f)
            drawNote(w * 0.44f, h * 0.60f, w * 0.085f, Color(0xFFFE2C55), w * 0.035f, h * 0.025f)
            drawNote(w * 0.44f, h * 0.60f, w * 0.085f, white, 0f, 0f)
        }
        IconKey.YouTube -> {
            drawRoundRect(Color(0xFFFF0033), topLeft = Offset(w * .18f, h * .30f), size = Size(w * .64f, h * .40f), cornerRadius = CornerRadius(w * .12f, w * .12f))
            val p = Path().apply {
                moveTo(w * .43f, h * .38f); lineTo(w * .43f, h * .62f); lineTo(w * .68f, h * .50f); close()
            }
            drawPath(p, white)
        }
        IconKey.Instagram -> {
            drawRoundRect(white.copy(.92f), topLeft = Offset(w * .23f, h * .23f), size = Size(w * .54f, h * .54f), cornerRadius = CornerRadius(w * .16f, w * .16f), style = Stroke(w * .045f))
            drawCircle(white.copy(.92f), w * .13f, Offset(w * .5f, h * .5f), style = Stroke(w * .045f))
            drawCircle(white, w * .035f, Offset(w * .66f, h * .34f))
        }
        IconKey.SlapKings -> {
            drawRoundRect(Color(0xFF4FC3F7), topLeft = Offset(w * .08f, h * .22f), size = Size(w * .84f, h * .13f), cornerRadius = CornerRadius(w * .05f, w * .05f))
            drawCircle(Color(0xFFD8A26A), w * .19f, Offset(w * .5f, h * .56f))
            drawRoundRect(Color(0xFF3E2723), topLeft = Offset(w * .39f, h * .62f), size = Size(w * .22f, h * .11f), cornerRadius = CornerRadius(w * .05f, w * .05f))
            drawCircle(Color.Black, w * .025f, Offset(w * .43f, h * .52f))
        }
        IconKey.CrossyRoad -> {
            drawRoundRect(white, topLeft = Offset(w * .30f, h * .38f), size = Size(w * .42f, h * .42f), cornerRadius = CornerRadius(w * .08f, w * .08f))
            drawRoundRect(white, topLeft = Offset(w * .40f, h * .22f), size = Size(w * .34f, h * .26f), cornerRadius = CornerRadius(w * .08f, w * .08f))
            val beak = Path().apply { moveTo(w * .74f, h * .32f); lineTo(w * .88f, h * .38f); lineTo(w * .74f, h * .44f); close() }
            drawPath(beak, Color(0xFFFFA726))
            drawCircle(Color(0xFF212121), w * .03f, Offset(w * .66f, h * .31f))
        }
        IconKey.SortIt -> {
            val cols = listOf(Color(0xFFFFCA28), Color(0xFFEF5350), Color(0xFF42A5F5))
            cols.forEachIndexed { i, c ->
                drawRoundRect(c, topLeft = Offset(w * (.18f + i * .23f), h * (.58f - i * .12f)), size = Size(w * .18f, h * .26f), cornerRadius = CornerRadius(w * .04f, w * .04f))
            }
        }
        IconKey.SevenSins -> drawStar(w * .5f, h * .5f, w * .26f, w * .11f, Color(0xFFFFE082))
        IconKey.Altos -> {
            drawCircle(Color(0xFFFFE0A0), w * .13f, Offset(w * .70f, h * .30f))
            val dune = Path().apply {
                moveTo(0f, h * .74f); quadraticTo(w * .35f, h * .48f, w * .68f, h * .70f); quadraticTo(w * .85f, h * .82f, w, h * .66f); lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(dune, Color(0xFF3E2A63))
            drawPath(dune, Color(0xFF7A4FA3).copy(.55f), style = Stroke(w * .03f))
        }
        IconKey.CutPaint -> {
            val cols = listOf(Color(0xFFFF7043), Color(0xFF66BB6A), Color(0xFF42A5F5))
            cols.forEachIndexed { i, c ->
                val p = Path().apply {
                    moveTo(w * (.20f + i * .22f), h * .22f); lineTo(w * (.34f + i * .22f), h * .22f)
                    lineTo(w * (.28f + i * .22f), h * .78f); lineTo(w * (.14f + i * .22f), h * .78f); close()
                }
                drawPath(p, c)
            }
        }
        IconKey.OverTake -> {
            val tri = Path().apply { moveTo(w * .5f, h * .18f); lineTo(w * .86f, h * .78f); lineTo(w * .14f, h * .78f); close() }
            drawPath(tri, Color(0xFF212121))
            val inner = Path().apply { moveTo(w * .5f, h * .28f); lineTo(w * .77f, h * .71f); lineTo(w * .23f, h * .71f); close() }
            drawPath(inner, Color(0xFFFFE45C))
            drawRoundRect(Color(0xFF212121), topLeft = Offset(w * .40f, h * .48f), size = Size(w * .20f, h * .18f), cornerRadius = CornerRadius(w * .03f, w * .03f))
            drawCircle(Color(0xFF212121), w * .035f, Offset(w * .43f, h * .68f))
            drawCircle(Color(0xFF212121), w * .035f, Offset(w * .57f, h * .68f))
        }
        IconKey.Mowing -> {
            for (i in 0..6) drawLine(Color(0xFF1B5E20), Offset(w * (.12f + i * .13f), h * .78f), Offset(w * (.10f + i * .13f), h * .62f), w * .022f, StrokeCap.Round)
            drawRoundRect(Color(0xFFECEFF1), topLeft = Offset(w * .30f, h * .40f), size = Size(w * .44f, h * .28f), cornerRadius = CornerRadius(w * .05f, w * .05f))
            drawCircle(Color(0xFF37474F), w * .09f, Offset(w * .38f, h * .72f))
            drawCircle(Color(0xFF37474F), w * .09f, Offset(w * .68f, h * .72f))
        }
        IconKey.SlapThat -> {
            drawCircle(Color(0xFFFFE0E0), w * .21f, Offset(w * .5f, h * .52f))
            for (i in 0..3) drawRoundRect(Color(0xFFFFE0E0), topLeft = Offset(w * (.33f + i * .09f), h * .22f), size = Size(w * .065f, h * .28f), cornerRadius = CornerRadius(w * .03f, w * .03f))
        }
        IconKey.Arcade -> {
            drawRoundRect(white, topLeft = Offset(w * .18f, h * .36f), size = Size(w * .64f, h * .30f), cornerRadius = CornerRadius(w * .14f, w * .14f))
            drawLine(Color(0xFF3A3A8C), Offset(w * .34f, h * .51f), Offset(w * .46f, h * .51f), w * .045f, StrokeCap.Round)
            drawLine(Color(0xFF3A3A8C), Offset(w * .40f, h * .45f), Offset(w * .40f, h * .57f), w * .045f, StrokeCap.Round)
            drawCircle(Color(0xFF3A3A8C), w * .035f, Offset(w * .62f, h * .47f))
            drawCircle(Color(0xFF3A3A8C), w * .035f, Offset(w * .70f, h * .55f))
        }
    }
}

private fun DrawScope.drawNote(x: Float, y: Float, r: Float, color: Color, dx: Float, dy: Float) {
    drawCircle(color, r, Offset(x + dx, y + dy))
    drawLine(color, Offset(x + r * .8f + dx, y + dy), Offset(x + r * .8f + dx, y - r * 3.4f + dy), r * .48f, StrokeCap.Round)
    drawLine(color, Offset(x + r * .8f + dx, y - r * 3.4f + dy), Offset(x + r * 3.1f + dx, y - r * 2.6f + dy), r * .48f, StrokeCap.Round)
    drawCircle(color, r * .55f, Offset(x + r * 3.1f + dx, y - r * 2.0f + dy))
}

private fun DrawScope.drawStar(cx: Float, cy: Float, outer: Float, inner: Float, color: Color) {
    val p = Path().apply {
        for (i in 0 until 10) {
            val a = -PI / 2 + i * PI / 5
            val r = if (i % 2 == 0) outer else inner
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(p, color)
}

private fun Path.quadraticTo(x1: Float, y1: Float, x2: Float, y2: Float) {
    quadraticBezierTo(x1, y1, x2, y2)
}

// ---------------------------------------------------------------------------
// Hero / story artwork
// ---------------------------------------------------------------------------

@Composable
fun HeroArt(art: ArtKey, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawHero(art) }
}

@Composable
fun AvatarArt(modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val state = MusicServices.state
    RemoteAvatar(
        url = state.avatarUrl,
        seed = (state.nickname ?: "guest").hashCode().toLong(),
        modifier = sized(modifier, size)
    )
}

@Composable
fun TabIcon(index: Int, color: Color, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Canvas(sized(modifier, size)) { drawTabIcon(index, color) }
}

private fun DrawScope.drawHero(art: ArtKey) {
    val w = size.width
    val h = size.height
    when (art) {
        ArtKey.Joystick -> {
            drawRoundRect(
                brush = Brush.verticalGradient(ASGradients.ArcadeLight),
                cornerRadius = CornerRadius(w * .06f, w * .06f)
            )
            val cx = w * .5f
            val cy = h * .60f
            // isometric base plate
            val s = w * .32f
            val d = s * .5f
            val hh = h * .10f
            isoCube(cx, cy, s, hh, Color(0xFFF0D9A8), Color(0xFFD8B778), Color(0xFFC9A45F))
            drawIsoTop(cx, cy - hh, s, Color(0xFFEAF4FF))
            // panel details
            drawLine(Color(0xFF9FB6CC), Offset(cx - s * .55f, cy - hh + d * .18f), Offset(cx + s * .10f, cy - hh - d * .32f), w * .012f)
            drawLine(Color(0xFF9FB6CC), Offset(cx + s * .20f, cy - hh + d * .40f), Offset(cx + s * .62f, cy - hh + d * .06f), w * .012f)
            // joystick stem + ball
            drawLine(Color(0xFFE7B77A), Offset(cx, cy - hh), Offset(cx, cy - hh - h * .22f), w * .045f, StrokeCap.Round)
            drawCircle(Color(0xFF2C2B31), w * .105f, Offset(cx, cy - hh - h * .25f))
            drawCircle(Color(0xFFFFFFFF).copy(.13f), w * .042f, Offset(cx - w * .032f, cy - hh - h * .28f))
            // red buttons
            drawCircle(Color(0xFFD0021B), w * .055f, Offset(cx - s * .52f, cy - hh + d * .16f))
            drawCircle(Color(0xFF7A0A12), w * .038f, Offset(cx - s * .44f, cy - hh + d * .05f))
        }
        ArtKey.VoxelRoad -> {
            drawRect(Color(0xFF2A2438))
            // isometric road bands
            val band = Path().apply {
                moveTo(-w * .2f, h * .12f); lineTo(w * 1.2f, h * -.28f)
                lineTo(w * 1.2f, h * .32f); lineTo(-w * .2f, h * .72f); close()
            }
            drawPath(band, Color(0xFF3A3548))
            drawLine(Color(0xFFE9A23B), Offset(-w * .1f, h * .40f), Offset(w * 1.1f, h * .0f), w * .018f)
            // voxel blocks
            isoCube(w * .22f, h * .30f, w * .13f, h * .13f, Color(0xFFB39DDB), Color(0xFF7E57C2), Color(0xFF5E35B1))
            isoCube(w * .40f, h * .17f, w * .10f, h * .10f, Color(0xFFFFB74D), Color(0xFFF57C00), Color(0xFFE65100))
            isoCube(w * .68f, h * .28f, w * .15f, h * .16f, Color(0xFF4FC3F7), Color(0xFF0288D1), Color(0xFF01579B))
            isoCube(w * .82f, h * .10f, w * .09f, h * .09f, Color(0xFFE57373), Color(0xFFC62828), Color(0xFF8E0000))
            isoCube(w * .12f, h * .62f, w * .14f, h * .14f, Color(0xFFAED581), Color(0xFF689F38), Color(0xFF33691E))
            isoCube(w * .55f, h * .60f, w * .12f, h * .12f, Color(0xFFF06292), Color(0xFFC2185B), Color(0xFF880E4F))
            // road markings
            for (i in 0..4) {
                val t = i / 5f
                drawLine(Color.White.copy(.22f), Offset(w * (.05f + t * .8f), h * (.82f - t * .45f)), Offset(w * (.11f + t * .8f), h * (.86f - t * .45f)), w * .012f)
            }
        }
        ArtKey.ColorMist -> {
            drawRect(Color(0xFF1B2A3A))
            val stops = listOf(
                Color(0xFF7B3FA0) to Offset(w * .18f, h * .22f),
                Color(0xFF2FA39A) to Offset(w * .82f, h * .30f),
                Color(0xFFE08A3C) to Offset(w * .24f, h * .82f),
                Color(0xFF2E5FA8) to Offset(w * .78f, h * .80f),
                Color(0xFFC9B98A) to Offset(w * .50f, h * .52f)
            )
            stops.forEach { (c, o) ->
                drawCircle(
                    brush = Brush.radialGradient(listOf(c.copy(.75f), Color.Transparent), o, w * .62f),
                    radius = w * .62f, center = o
                )
            }
        }
        ArtKey.Ring -> {
            drawRect(Color(0xFF20232E))
            // stadium ropes
            for (i in 0..2) drawLine(Color(0xFF6C7080), Offset(0f, h * (.42f + i * .11f)), Offset(w, h * (.36f + i * .11f)), w * .012f)
            // rainbow gauge
            val cols = listOf(Color(0xFFEF5350), Color(0xFFFFCA28), Color(0xFF66BB6A), Color(0xFF42A5F5))
            val arcSize = Size(w * .46f, w * .46f)
            cols.forEachIndexed { i, c ->
                drawArc(c, 200f + i * 20f, 20f, false, Offset(w * .27f, h * .10f), arcSize, style = Stroke(w * .035f))
            }
            // two simplified fighters
            drawRoundRect(Color(0xFFFF8A65), topLeft = Offset(w * .16f, h * .50f), size = Size(w * .14f, h * .30f), cornerRadius = CornerRadius(w * .05f, w * .05f))
            drawCircle(Color(0xFF8D5524), w * .075f, Offset(w * .23f, h * .46f))
            drawRoundRect(Color(0xFF42A5F5), topLeft = Offset(w * .62f, h * .54f), size = Size(w * .14f, h * .26f), cornerRadius = CornerRadius(w * .05f, w * .05f))
            drawCircle(Color(0xFF5D4037), w * .072f, Offset(w * .69f, h * .50f))
        }
        ArtKey.Phone -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFF1B3A6B), Color(0xFF8E2A6B), Color(0xFFE0642C)), Offset.Zero, Offset(w, h)))
            val pw = w * .46f
            val ph = h * .82f
            val px = (w - pw) / 2f
            val py = (h - ph) / 2f
            drawRoundRect(Color(0xFF101014), topLeft = Offset(px, py), size = Size(pw, ph), cornerRadius = CornerRadius(w * .06f, w * .06f))
            drawRoundRect(Color(0xFFF5F7FA), topLeft = Offset(px + w * .018f, py + h * .02f), size = Size(pw - w * .036f, ph - h * .04f), cornerRadius = CornerRadius(w * .045f, w * .045f))
            drawRoundRect(Color(0xFF101014), topLeft = Offset(px + pw * .32f, py + h * .012f), size = Size(pw * .36f, h * .022f), cornerRadius = CornerRadius(w * .02f, w * .02f))
            for (i in 0..5) {
                val yy = py + h * (.14f + i * .115f)
                drawRoundRect(Color(0xFFD9DEE8), topLeft = Offset(px + w * .05f, yy), size = Size(pw * .16f, h * .075f), cornerRadius = CornerRadius(w * .02f, w * .02f))
                drawRoundRect(Color(0xFFE8ECF3), topLeft = Offset(px + w * .05f + pw * .22f, yy), size = Size(pw * .58f, h * .022f), cornerRadius = CornerRadius(w * .01f, w * .01f))
                drawRoundRect(Color(0xFFEFF2F7), topLeft = Offset(px + w * .05f + pw * .22f, yy + h * .034f), size = Size(pw * .38f, h * .018f), cornerRadius = CornerRadius(w * .01f, w * .01f))
            }
        }
        ArtKey.Avatar -> drawAvatar()
    }
}

private fun DrawScope.drawIsoTop(cx: Float, cy: Float, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(cx, cy - s * .5f); lineTo(cx + s, cy); lineTo(cx, cy + s * .5f); lineTo(cx - s, cy); close()
    }
    drawPath(p, color)
}

private fun DrawScope.isoCube(
    cx: Float, cy: Float, s: Float, height: Float,
    top: Color, left: Color, right: Color
) {
    val d = s * .5f
    val leftFace = Path().apply {
        moveTo(cx - s, cy); lineTo(cx, cy + d); lineTo(cx, cy + d + height); lineTo(cx - s, cy + height); close()
    }
    val rightFace = Path().apply {
        moveTo(cx, cy + d); lineTo(cx + s, cy); lineTo(cx + s, cy + height); lineTo(cx, cy + d + height); close()
    }
    drawPath(leftFace, left)
    drawPath(rightFace, right)
    drawIsoTop(cx, cy, s, top)
}

private fun DrawScope.drawAvatar() {
    val w = size.width
    val h = size.height
    drawCircle(Color(0xFF3F6B3A), w * .5f, Offset(w * .5f, h * .5f))
    // foliage suggestion
    repeat(7) { i ->
        val a = i * 0.9f
        drawCircle(Color(0xFF5C8F4A).copy(.65f), w * (.10f + (i % 3) * .03f), Offset(w * (.5f + .34f * cos(a).toFloat()), h * (.5f + .34f * sin(a).toFloat())))
    }
    // shoulders / shirt
    val body = Path().apply {
        moveTo(w * .10f, h); lineTo(w * .16f, h * .68f); quadraticTo(w * .5f, h * .56f, w * .84f, h * .68f); lineTo(w * .90f, h); close()
    }
    drawPath(body, Color(0xFF252A2E))
    // neck + face
    drawRoundRect(Color(0xFFB07A56), topLeft = Offset(w * .41f, h * .52f), size = Size(w * .18f, h * .14f), cornerRadius = CornerRadius(w * .04f, w * .04f))
    drawCircle(Color(0xFFC68E62), w * .20f, Offset(w * .5f, h * .43f))
    // hair cap
    drawArc(Color(0xFF3A2A22), 180f, 180f, true, Offset(w * .30f, h * .23f), Size(w * .40f, h * .32f))
    // eyes
    drawCircle(Color(0xFF2A1F18), w * .017f, Offset(w * .435f, h * .42f))
    drawCircle(Color(0xFF2A1F18), w * .017f, Offset(w * .565f, h * .42f))
    // beard
    drawRoundRect(Color(0xFF3A2A22), topLeft = Offset(w * .37f, h * .50f), size = Size(w * .26f, h * .10f), cornerRadius = CornerRadius(w * .06f, w * .06f))
}

private fun DrawScope.drawTabIcon(index: Int, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * .085f
    when (index) {
        0 -> { // newspaper
            drawRoundRect(color, topLeft = Offset(w * .14f, h * .10f), size = Size(w * .72f, h * .80f), cornerRadius = CornerRadius(w * .09f, w * .09f), style = Stroke(stroke))
            drawLine(color, Offset(w * .28f, h * .34f), Offset(w * .72f, h * .34f), stroke * .8f, StrokeCap.Round)
            drawLine(color, Offset(w * .28f, h * .52f), Offset(w * .72f, h * .52f), stroke * .8f, StrokeCap.Round)
            drawLine(color, Offset(w * .28f, h * .70f), Offset(w * .56f, h * .70f), stroke * .8f, StrokeCap.Round)
        }
        1 -> { // rocket
            val p = Path().apply {
                moveTo(w * .5f, h * .08f); quadraticTo(w * .84f, h * .38f, w * .72f, h * .70f)
                lineTo(w * .28f, h * .70f); quadraticTo(w * .16f, h * .38f, w * .5f, h * .08f); close()
            }
            drawPath(p, color)
            drawCircle(Color.Black.copy(.55f), w * .10f, Offset(w * .5f, h * .38f))
            val finL = Path().apply { moveTo(w * .28f, h * .58f); lineTo(w * .12f, h * .84f); lineTo(w * .30f, h * .76f); close() }
            val finR = Path().apply { moveTo(w * .72f, h * .58f); lineTo(w * .88f, h * .84f); lineTo(w * .70f, h * .76f); close() }
            drawPath(finL, color); drawPath(finR, color)
            drawCircle(Color(0xFFFF9F0A), w * .07f, Offset(w * .5f, h * .86f))
        }
        2 -> { // stacked layers
            for (i in 0..2) {
                val yy = h * (.24f + i * .22f)
                val p = Path().apply {
                    moveTo(w * .5f, yy - h * .10f); lineTo(w * .86f, yy + h * .04f)
                    lineTo(w * .5f, yy + h * .18f); lineTo(w * .14f, yy + h * .04f); close()
                }
                drawPath(p, color.copy((1f - i * .18f)))
            }
        }
        3 -> { // game controller
            drawRoundRect(color, topLeft = Offset(w * .08f, h * .28f), size = Size(w * .84f, h * .46f), cornerRadius = CornerRadius(w * .20f, w * .20f))
            drawLine(Color.Black.copy(.6f), Offset(w * .30f, h * .51f), Offset(w * .44f, h * .51f), stroke, StrokeCap.Round)
            drawLine(Color.Black.copy(.6f), Offset(w * .37f, h * .44f), Offset(w * .37f, h * .58f), stroke, StrokeCap.Round)
            drawCircle(Color.Black.copy(.6f), w * .045f, Offset(w * .64f, h * .46f))
            drawCircle(Color.Black.copy(.6f), w * .045f, Offset(w * .74f, h * .56f))
        }
        4 -> { // magnifier
            drawCircle(color, w * .28f, Offset(w * .42f, h * .42f), style = Stroke(stroke))
            drawLine(color, Offset(w * .63f, h * .63f), Offset(w * .88f, h * .88f), stroke * 1.3f, StrokeCap.Round)
        }
    }
}
