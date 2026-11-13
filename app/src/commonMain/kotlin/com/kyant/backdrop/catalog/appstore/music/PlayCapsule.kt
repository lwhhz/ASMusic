package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.Txt
import com.kyant.backdrop.catalog.appstore.tap
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur as glassBlur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.shapes.Capsule

private val CompactWidth = 182.dp
private val CompactHeight = 60.dp
private val ExpandedHeight = 134.dp

/**
 * Playback capsule. Compact form floats above the tab bar with prev / play-pause / next
 * and a cover ring; when the song detail page is open it grows into a full-width control
 * bar with a draggable progress bar on top of the three transport buttons.
 *
 * [corner] customizes the corner radius of the **expanded** (detail page) form only —
 * the floating compact capsule always stays a full capsule. It is a lambda on purpose:
 * reading the state inside the shape / placement phases is what makes the glass clip
 * follow the slider live (a plain captured value would go stale until the next re-layout).
 *
 * While the capsule travels between the left and right edge its content fades out and
 * defocuses, the layout is swapped in the middle, then it fades back in — which reads as
 * a natural re-layout instead of a jump. This applies both to the settings switch and
 * to dragging the capsule by hand.
 */
@Composable
fun PlayCapsule(
    song: Song,
    backdrop: Backdrop,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    expanded: Boolean,
    onLeft: Boolean,
    fade: Float,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpen: () -> Unit,
    onQuality: () -> Unit = {},
    heartMode: Boolean = false,
    onHeart: () -> Unit = {},
    corner: () -> Float = { 1f },
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Same liquid-glass recipe as the bottom tab bar so both read as one material.
    val isLight = !isSystemInDarkTheme()
    val containerColor =
        if (isLight) Color(0xFFFAFAFA).copy(alpha = .4f) else Color(0xFF121212).copy(alpha = .4f)

    val height by animateDpAsState(
        targetValue = if (expanded) ExpandedHeight else CompactHeight,
        animationSpec = spring(dampingRatio = .86f, stiffness = 320f),
        label = "capsuleHeight"
    )

    Box(
        modifier
            .height(height)
            // Reading [corner] during placement forces a re-place when the slider
            // moves, so the glass layer re-reads its (state-backed) shape lambda
            // and the new corner shows up immediately instead of after the next
            // expand / collapse.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                    corner()
                }
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = {
                    val c = if (expanded) corner() else 1f
                    if (c >= .995f) Capsule() else RoundedCornerShape((c * 52f).dp)
                },
                effects = {
                    vibrancy()
                    glassBlur(10f.dp.toPx())
                    lens(18f.dp.toPx(), 18f.dp.toPx())
                },
                highlight = { Highlight.Default },
                innerShadow = { InnerShadow(radius = 6f.dp, alpha = .26f) },
                onDrawSurface = {
                    drawRect(containerColor)
                    drawRect(Color.White.copy(alpha = .04f))
                }
            )
            // Swallow taps that land on the capsule background.
            .tap { }
    ) {
        // The capsule <-> control-bar morph uses the same exit treatment as the
        // left/right capsule switch: fade + defocus while the height springs.
        val compactAlpha by animateFloatAsState(
            targetValue = if (expanded) 0f else 1f,
            animationSpec = tween(220),
            label = "capsuleCompactAlpha"
        )
        val expandedAlpha by animateFloatAsState(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = tween(220),
            label = "capsuleExpandedAlpha"
        )
        val transitionBlur = (1f - kotlin.math.abs(expandedAlpha - compactAlpha)) * 8f

        Box(Modifier.fillMaxSize()) {
            if (compactAlpha > .001f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = compactAlpha }
                        .blur(transitionBlur.dp)
                ) {
                    CompactControls(
                        song = song,
                        playing = playing,
                        progress = progress,
                        onLeft = onLeft,
                        fade = fade,
                        onToggle = onToggle,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onOpen = onOpen,
                        heartMode = heartMode,
                        onHeart = onHeart
                    )
                }
            }
            if (expandedAlpha > .001f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = expandedAlpha }
                        .blur(transitionBlur.dp)
                ) {
                    ExpandedControls(
                        playing = playing,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        progress = progress,
                        onToggle = onToggle,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onSeek = onSeek,
                        onQuality = onQuality,
                        heartMode = heartMode,
                        onHeart = onHeart
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactControls(
    song: Song,
    playing: Boolean,
    progress: Float,
    onLeft: Boolean,
    fade: Float,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    heartMode: Boolean,
    onHeart: () -> Unit
) {
    // Content fades / defocuses while travelling; the order flips at the midpoint.
    val contentAlpha = 1f - fade * 0.98f

    Row(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha }
            .blur((fade * 7f).dp)
            .padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onLeft) {
            CapsuleCover(song, progress, onOpen)
            Spacer(Modifier.width(10.dp))
            Transport(onToggle, onPrevious, onNext, playing)
        } else {
            Transport(onToggle, onPrevious, onNext, playing)
            Spacer(Modifier.width(10.dp))
            CapsuleCover(song, progress, onOpen)
        }
    }
}

@Composable
private fun Transport(
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    playing: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SkipButton(false, onPrevious)
        Spacer(Modifier.width(2.dp))
        PlayPauseButton(playing, onToggle, size = 40.dp, filled = true)
        Spacer(Modifier.width(2.dp))
        SkipButton(true, onNext)
    }
}

@Composable
private fun CapsuleCover(song: Song, progress: Float, onOpen: () -> Unit) {
    Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.5.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val topLeft = Offset(
                (size.width - radius * 2f) / 2f,
                (size.height - radius * 2f) / 2f
            )
            val arcSize = Size(radius * 2f, radius * 2f)
            drawCircle(
                color = Color.White.copy(alpha = .16f),
                radius = radius,
                center = center,
                style = Stroke(width = stroke)
            )
            if (progress > 0f) {
                drawArc(
                    color = AS.Blue,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Box(
            Modifier
                .padding(5.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .tap(onOpen)
        ) {
            CoverImage(song.coverUrl, song.id, Modifier.fillMaxSize())
        }
    }
}

// ---------------------------------------------------------------------------
// Expanded control bar (song detail page)
// ---------------------------------------------------------------------------

@Composable
private fun ExpandedControls(
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    progress: Float,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onQuality: () -> Unit,
    heartMode: Boolean,
    onHeart: () -> Unit
) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Both are icon-only circles now: queue on the left, play order on the right.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(onClick = { state.queueSheetOpen = true }) {
                QueueGlyph(Modifier.size(17.dp))
            }
            CircleIconButton(onClick = { bridge.setPlayMode(state.playMode.next()) }) {
                PlayModeGlyph(state.playMode, Modifier.size(17.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt(formatClock(positionMs), AS.caption().copy(fontSize = 11.sp))
            Spacer(Modifier.width(10.dp))
            SeekBar(
                progress = progress,
                onSeekFraction = { fraction -> onSeek((durationMs * fraction).toLong()) },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Txt(formatClock(durationMs), AS.caption().copy(fontSize = 11.sp))
        }
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 心动模式：仅详情页展开态显示，位于「上一首」左侧
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (heartMode) AS.Blue.copy(alpha = 0.18f) else Color.Transparent)
                        .tap(onHeart),
                    contentAlignment = Alignment.Center
                ) {
                    Txt(
                        "♥",
                        AS.body().copy(
                            fontSize = 19.sp,
                            color = if (heartMode) AS.Blue else AS.Text3
                        )
                    )
                }
                Spacer(Modifier.width(8.dp))
                SkipButton(false, onPrevious, size = 44.dp)
                Spacer(Modifier.width(26.dp))
                PlayPauseButton(playing, onToggle, size = 56.dp, filled = true)
                Spacer(Modifier.width(26.dp))
                SkipButton(true, onNext, size = 44.dp)
                // Mirror the heart so the play button stays visually centred.
                Spacer(Modifier.width(48.dp))
            }
            // Quality picker sits to the right of the next button.
            CircleIconButton(
                onClick = onQuality,
                modifier = Modifier.align(Alignment.CenterEnd),
                size = 38.dp
            ) {
                Txt(
                    MusicServices.state.currentQuality?.shortLabel ?: "音质",
                    AS.caption().copy(fontSize = 9.sp, color = Color.White.copy(.92f))
                )
            }
        }
    }
}

/** iOS-style slim slider: drag anywhere on the track, or tap to jump. */
@Composable
private fun SeekBar(
    progress: Float,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragFraction else progress

    BoxWithConstraints(
        modifier
            .height(26.dp)
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    onSeekFraction((pos.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        dragging = true
                        dragFraction = (pos.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeekFraction(dragFraction)
                    },
                    onDragCancel = { dragging = false }
                ) { change, _ ->
                    dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = .22f))
        )
        Box(
            Modifier
                .fillMaxWidth(shown)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AS.Blue)
        )
        if (dragging) {
            Box(
                Modifier
                    .offset(x = (maxWidth - 14.dp) * shown)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:${if (s < 10) "0" else ""}$s"
}

@Composable
private fun SkipButton(forward: Boolean, onClick: () -> Unit, size: Dp = 34.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size * .45f)) {
            val w = this.size.width
            val h = this.size.height
            val tint = Color.White.copy(alpha = .92f)
            val path = Path()
            if (forward) {
                path.moveTo(w * .08f, h * .12f)
                path.lineTo(w * .58f, h * .5f)
                path.lineTo(w * .08f, h * .88f)
            } else {
                path.moveTo(w * .92f, h * .12f)
                path.lineTo(w * .42f, h * .5f)
                path.lineTo(w * .92f, h * .88f)
            }
            path.close()
            drawPath(path, tint)
            val barX = if (forward) w * .66f else w * .28f
            drawRoundRect(
                color = tint,
                topLeft = Offset(barX, h * .16f),
                size = Size(w * .1f, h * .68f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .05f, w * .05f)
            )
        }
    }
}


@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = .10f))
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun QueueGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = .9f)
        val rowH = size.height
        val sw = 1.6.dp.toPx()
        for (i in 0..2) {
            drawLine(
                c,
                Offset(0f, rowH * (.18f + i * .30f)),
                Offset(size.width * .62f, rowH * (.18f + i * .30f)),
                sw,
                StrokeCap.Round
            )
        }
        val p = Path().apply {
            moveTo(size.width * .70f, rowH * .32f)
            lineTo(size.width * .98f, rowH * .5f)
            lineTo(size.width * .70f, rowH * .68f)
            close()
        }
        drawPath(p, c)
    }
}

@Composable
private fun PlayModeGlyph(mode: PlayMode, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = .92f)
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()
        fun arrowHead(x: Float, y: Float, dx: Float, dy: Float) {
            val p = Path().apply {
                moveTo(x, y)
                lineTo(x - dx * .30f - dy * .22f, y - dy * .30f + dx * .22f)
                lineTo(x - dx * .30f + dy * .22f, y - dy * .30f - dx * .22f)
                close()
            }
            drawPath(p, c)
        }
        when (mode) {
            PlayMode.Sequential -> {
                drawLine(c, Offset(w * .10f, h * .5f), Offset(w * .70f, h * .5f), sw, StrokeCap.Round)
                arrowHead(w * .78f, h * .5f, 1f, 0f)
                drawLine(c, Offset(w * .90f, h * .26f), Offset(w * .90f, h * .74f), sw * 1.3f, StrokeCap.Round)
            }
            PlayMode.LoopAll, PlayMode.RepeatOne -> {
                drawArc(
                    c,
                    startAngle = -60f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(w * .12f, h * .12f),
                    size = Size(w * .76f, h * .76f),
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
                arrowHead(w * .84f, h * .24f, .7f, .7f)
                if (mode == PlayMode.RepeatOne) {
                    drawLine(c, Offset(w * .5f, h * .34f), Offset(w * .5f, h * .66f), sw, StrokeCap.Round)
                    drawLine(c, Offset(w * .40f, h * .42f), Offset(w * .5f, h * .34f), sw, StrokeCap.Round)
                }
            }
            PlayMode.Shuffle -> {
                drawLine(c, Offset(w * .12f, h * .30f), Offset(w * .86f, h * .70f), sw, StrokeCap.Round)
                drawLine(c, Offset(w * .12f, h * .70f), Offset(w * .86f, h * .30f), sw, StrokeCap.Round)
                arrowHead(w * .90f, h * .72f, .6f, .8f)
                arrowHead(w * .90f, h * .28f, .6f, -.8f)
            }
        }
    }
}

