package com.dokar.amlv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.packInts
import androidx.compose.ui.util.unpackInt1
import androidx.compose.ui.util.unpackInt2
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@JvmInline
@Immutable
private value class ItemInfo(val packedValue: Long) {
    val offsetY: Int get() = unpackInt1(packedValue)

    val height: Int get() = unpackInt2(packedValue)
}

private fun ItemInfo(offsetY: Int, height: Int): ItemInfo {
    return ItemInfo(packInts(offsetY, height))
}

/**
 * Apple Music style lyric list, adapted from amlv-main (com.dokar.amlv).
 *
 * This copy only removes the original tap/ripple interaction so the immersive
 * screen remains display-only; layout, spring scale, alpha and scroll animation
 * are the original AMLV implementation.
 */
@Composable
fun LyricsView(
    state: LyricsViewState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    darkTheme: Boolean = false,
    fadingEdges: FadingEdges = FadingEdges.None,
    fontSize: TextUnit = 32.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    lineHeight: TextUnit = 1.2.em,
) {
    val scope = rememberCoroutineScope()

    val scrollState = rememberScrollState()

    var lyricsHeight by remember { mutableStateOf(0) }

    val itemsInfo = remember { mutableMapOf<Int, ItemInfo>() }

    var initialItemsOffsetY by remember { mutableStateOf(0) }

    var currItemsOffsetY by remember { mutableStateOf(0) }

    var animationItemsRange by remember { mutableStateOf(-1..-1) }

    var itemsAnimationJob by remember { mutableStateOf<Job?>(null) }

    fun updateItemInfo(index: Int, itemCoordinates: LayoutCoordinates) {
        itemsInfo[index] = ItemInfo(
            offsetY = itemCoordinates.positionInParent().y.toInt(),
            height = itemCoordinates.size.height,
        )
    }

    fun getAnimationItemsRange(currentIndex: Int): IntRange {
        val lines = state.lyrics?.lines ?: return -1..-1
        val currItemInfo = itemsInfo[currentIndex] ?: return -1..-1
        val scrollY = scrollState.value
        var start = -1
        var end = -1
        for (i in lines.indices) {
            val itemInfo = itemsInfo[i] ?: continue

            val itemTop = itemInfo.offsetY
            val itemHeight = itemInfo.height
            val itemBottom = itemTop + itemHeight

            if (itemBottom < scrollY) {
                continue
            } else if (start == -1) {
                start = i
            }

            if (itemTop > currItemInfo.offsetY + lyricsHeight) {
                break
            } else {
                end = i
            }
        }
        return start..end
    }

    fun getItemOffsetY(index: Int): Int {
        return if (index in animationItemsRange) {
            val value = currItemsOffsetY
            if (index > state.currentLineIndex) {
                val factor = (1f + (index - state.currentLineIndex) * 0.08f)
                val progress = currItemsOffsetY.toFloat() / initialItemsOffsetY
                val finalProgress = (progress * factor).coerceAtMost(1f)
                (initialItemsOffsetY * finalProgress).toInt()
            } else {
                value
            }
        } else {
            0
        }
    }

    fun startItemsAnimation(targetItemIndex: Int) {
        val targetItemTop = itemsInfo[targetItemIndex]?.offsetY ?: return
        itemsAnimationJob?.cancel()
        itemsAnimationJob = scope.launch {
            val targetScrollY = targetItemTop.coerceAtMost(scrollState.maxValue)
            val diff = targetScrollY - scrollState.value
            animationItemsRange = getAnimationItemsRange(targetItemIndex)

            scrollState.scrollTo(targetScrollY)

            Snapshot.withoutReadObservation { initialItemsOffsetY = diff }
            currItemsOffsetY = diff

            animate(
                initialValue = diff.toFloat(),
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1000),
            ) { value, _ ->
                currItemsOffsetY = value.toInt()
            }
        }
    }

    LaunchedEffect(scrollState, state) {
        snapshotFlow { state.currentLineIndex }
            .filter { it >= 0 }
            .collect(::startItemsAnimation)
    }

    Box(
        modifier = modifier
            .fadingEdges(edges = fadingEdges)
            .onSizeChanged { lyricsHeight = it.height },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = scrollState)
                .padding(contentPadding),
        ) {
            val lines = state.lyrics?.lines ?: emptyList()
            for ((index, line) in lines.withIndex()) {
                LyricsViewLine(
                    isActive = index == state.currentLineIndex,
                    content = line.content,
                    contentColor = if (darkTheme) Color.White else Color.Black,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    lineHeight = lineHeight,
                    offsetYProvider = { getItemOffsetY(index) },
                    modifier = Modifier.onGloballyPositioned { updateItemInfo(index, it) },
                )
            }
        }
    }
}

@Composable
private fun LyricsViewLine(
    isActive: Boolean,
    content: String,
    contentColor: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    lineHeight: TextUnit,
    offsetYProvider: () -> Int,
    modifier: Modifier = Modifier,
    activeScale: Float = 1.1f,
    inactiveScale: Float = 1f,
    activeAlpha: Float = 1f,
    inactiveAlpha: Float = 0.35f,
) {
    var scale by remember { mutableStateOf(if (isActive) activeScale else inactiveScale) }
    var alpha by remember { mutableStateOf(if (isActive) activeAlpha else inactiveAlpha) }

    LaunchedEffect(isActive) {
        launch {
            animate(
                initialValue = scale,
                targetValue = if (isActive) activeScale else inactiveScale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                )
            ) { value, _ ->
                scale = value
            }
        }
        launch {
            // Composable could suddenly go invisible for one frame (or few frame?) when
            // isActive changes to false and the alpha animation starts. Delay may help
            // to reduce these glitches.
            repeat(10) { withFrameNanos { } }
            animate(
                initialValue = alpha,
                targetValue = if (isActive) activeAlpha else inactiveAlpha,
            ) { value, _ ->
                alpha = value
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, offsetYProvider()) }
            .clip(RoundedCornerShape(8.dp))
            .padding(
                start = 16.dp,
                top = 8.dp,
                end = 32.dp,
                bottom = 16.dp,
            ),
    ) {
        BasicText(
            text = content,
            modifier = Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0f, 1f)
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
            style = TextStyle(
                color = contentColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight,
            ),
        )
    }
}
