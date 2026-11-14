package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.AS
import kotlinx.coroutines.delay
import com.kyant.backdrop.catalog.appstore.Txt

/**
 * One gradient card per lyric line. The card colors come from the current cover's
 * dominant palette (sampled on Android) and are rotated per card; the active card
 * is sharp, the others are dimmed / slightly blurred.
 *
 * Long-pressing a card opens the separate AMLV immersive lyric view.
 * This carousel stays native and stable for the detail page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricCards(
    lines: List<LyricLine>,
    positionMs: Long,
    wordByWord: Boolean = false,
    playing: Boolean = false,
    synthWords: Boolean = true,
    keepSung: Boolean = true,
    showTranslation: Boolean = true,
    glow: Float = 0.55f,
    fontSp: Float = 23f,
    translationSp: Float = 15f,
    palette: List<Color> = emptyList(),
    songSeed: Long = 0L,
    flow: Boolean = false,
    onSeek: (Long) -> Unit = {},
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Stable reference: a fresh list here would invalidate the LazyRow on every tick.
    val safeLines = remember(lines) {
        if (lines.isEmpty()) listOf(LyricLine(0, "暂无歌词")) else lines
    }
    val size = safeLines.size
    // Enough cycles to keep the carousel effectively infinite without creating a
    // 10k+ item index space; LazyRow only composes visible pages anyway.
    val loops = 40
    val startPage = size * (loops / 2)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)

    // Only recompute the active line when the position or the line list changes.
    val currentIndex = remember(positionMs, safeLines) {
        var idx = 0
        for (i in safeLines.indices) {
            if (safeLines[i].timeMs <= positionMs) idx = i
        }
        idx
    }
    var manualHold by remember { mutableStateOf(false) }
    var userMoved by remember { mutableStateOf(false) }
    // Block auto-follow for the first few frames after a song change. Otherwise the
    // previous song's positionMs can immediately pull the brand-new list away from
    // the first card before playback resets to 0.
    var followSuppressed by remember { mutableStateOf(true) }
    LaunchedEffect(songSeed) {
        manualHold = false
        userMoved = false
        followSuppressed = true
        listState.scrollToItem(startPage)
        delay(350)
        followSuppressed = false
    }

    // A manual swipe keeps the row where the user left it, without seeking. Playback
    // follow is suspended until the user taps a card (the new explicit seek action).
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) {
        if (dragging) {
            userMoved = true
            manualHold = true
        }
    }

    LaunchedEffect(currentIndex, userMoved, manualHold, followSuppressed) {
        if (userMoved || manualHold || followSuppressed) return@LaunchedEffect
        // 声明式跟随：算出最近的目标页，交给 LazyRow 自己吸附。
        // 不再判断 isScrollInProgress，避免动画被取消后卡在两页之间。
        val cur = listState.firstVisibleItemIndex
        val base = cur - (cur % size)
        var target = base + currentIndex
        // 取距离最近的那一个循环副本，避免突然翻半圈。
        if (target - cur > size / 2) target -= size
        if (cur - target > size / 2) target += size
        if (cur != target || listState.firstVisibleItemScrollOffset != 0) {
            runCatching { listState.animateScrollToItem(target) }
        }
    }

    LaunchedEffect(listState.isScrollInProgress, userMoved) {
        if (userMoved && !listState.isScrollInProgress) {
            // Swiping only browses cards. After a short pause, release the manual
            // hold so the carousel follows the currently playing line again. Tapping
            // a card still seeks immediately.
            delay(2_500)
            userMoved = false
            manualHold = false
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        flingBehavior = rememberSnapFlingBehavior(
            lazyListState = listState,
            snapPosition = SnapPosition.Start
        )
    ) {
        items(size * loops, key = { page -> page },
            contentType = { _ -> "lyric-card" }) { page ->
            val index = page % size
            val line = safeLines[index]
            val nextTime = if (index + 1 < size) safeLines[index + 1].timeMs else Long.MAX_VALUE
            val active = positionMs >= line.timeMs && positionMs < nextTime
            LyricCard(
                line = line,
                index = index,
                total = size,
                active = active,
                positionMs = positionMs,
                playing = playing,
                nextTimeMs = nextTime,
                wordByWord = wordByWord,
                synthWords = synthWords,
                keepSung = keepSung,
                showTranslation = showTranslation,
                glow = glow,
                fontSp = fontSp,
                translationSp = translationSp,
                // Only the active card animates its gradient: every extra running
                // frame loop is expensive on low-end devices.
                flow = flow && active,
                onClick = {
                    manualHold = false
                    userMoved = false
                    onSeek(line.timeMs.coerceAtLeast(0L))
                },
                onLongClick = onLongPress,
                colors = remember(palette, songSeed, index) {
                    lyricCardColors(palette, songSeed, index)
                }
            )
        }
    }
}

@Composable
private fun LyricCard(
    line: LyricLine,
    index: Int,
    total: Int,
    active: Boolean,
    positionMs: Long,
    playing: Boolean,
    nextTimeMs: Long,
    wordByWord: Boolean,
    synthWords: Boolean,
    keepSung: Boolean,
    showTranslation: Boolean,
    glow: Float,
    fontSp: Float,
    translationSp: Float,
    flow: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    colors: List<Color>
) {
    val haptics = LocalHapticFeedback.current
    val textStyle = AS.cardTitle().copy(
        fontSize = fontSp.sp,
        lineHeight = (fontSp * 1.38f).sp,
        color = if (active) Color.White else Color.White.copy(alpha = .63f)
    )
    val maxRows = if (showTranslation && !line.translation.isNullOrBlank()) 4 else 5
    val inactiveBlur = if (!active) Modifier.blur((1.0f + glow * 2.6f).dp) else Modifier

    Box(
        Modifier
            .width(240.dp)
            .height(310.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AS.Card)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        LyricCardArt(colors, index.toLong(), flow, Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // AMLL-style per-character fill: each word lights up across its own
            // duration, so the highlight sweeps through the line instead of jumping.
            // The player reports position a few times per second; interpolating on
            // every frame keeps the sweep continuous instead of jumping per tick.
            var smoothPos by remember { mutableLongStateOf(positionMs) }
            LaunchedEffect(active, playing, positionMs) {
                if (!active || !wordByWord) {
                    smoothPos = positionMs
                    return@LaunchedEffect
                }
                if (kotlin.math.abs(smoothPos - positionMs) > 350L) smoothPos = positionMs
                if (!playing) return@LaunchedEffect
                var last = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now ->
                        smoothPos += (now - last) / 1_000_000L
                        last = now
                    }
                }
            }
            val karaoke = remember(line, smoothPos, wordByWord, active, synthWords, keepSung) {
                if (!active || !wordByWord) {
                    null
                } else {
                    buildAnnotatedString {
                        val dim = Color.White.copy(alpha = .63f)
                        val bright = Color.White
                        line.timedWords(nextTimeMs, synthWords).forEach { word ->
                            val duration = word.durationMs.coerceAtLeast(1L)
                            val wordProgress =
                                ((smoothPos - word.timeMs).toFloat() / duration).coerceIn(0f, 1f)
                            val text = word.text
                            val count = text.length.coerceAtLeast(1)
                            text.forEachIndexed { i, ch ->
                                val raw = wordProgress * count - i
                                // keepSung: characters stay lit once sung;
                                // otherwise the highlight is a short moving window.
                                val charFill = if (keepSung) {
                                    raw.coerceIn(0f, 1f)
                                } else {
                                    (raw.coerceIn(-2.2f, 1f) + 2.2f) / 3.2f
                                }.coerceIn(0f, 1f)
                                withStyle(SpanStyle(color = lerp(dim, bright, charFill))) {
                                    append(ch)
                                }
                            }
                        }
                    }
                }
            }
            BasicText(
                text = karaoke ?: AnnotatedString(line.text),
                style = textStyle,
                maxLines = maxRows,
                modifier = inactiveBlur
            )
            if (showTranslation && !line.translation.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                BasicText(
                    text = line.translation.orEmpty(),
                    style = AS.body().copy(
                        fontSize = translationSp.sp,
                        lineHeight = (translationSp * 1.4f).sp,
                        color = Color.White.copy(alpha = if (active) .78f else .52f)
                    ),
                    maxLines = 3,
                    modifier = if (!active) Modifier.blur((glow * 3f).dp) else Modifier
                )
            }
        }
        Txt(
            "${index + 1} / $total",
            AS.caption().copy(color = Color.White.copy(.65f)),
            Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
        Txt(
            formatLyricTime(line.timeMs),
            AS.caption().copy(color = Color.White.copy(.5f)),
            Modifier.align(Alignment.BottomStart).padding(16.dp)
        )
    }
}

private fun formatLyricTime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return "$m:${if (sec < 10) "0" else ""}$sec"
}
