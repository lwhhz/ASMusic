package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.Txt
import com.kyant.backdrop.catalog.appstore.tap

/**
 * Bottom sheet with the current up-next queue. The queue is replaced by whichever
 * list a song was picked from; tapping a row jumps to that song without changing
 * the queue itself. The active row is highlighted.
 */
@Composable
fun PlayQueueSheet() {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    val progress by animateFloatAsState(
        if (state.queueSheetOpen) 1f else 0f,
        animationSpec = spring(dampingRatio = .92f, stiffness = 380f)
    )
    if (progress < .001f) return
    LaunchedEffect(state.queueSheetOpen) {
        if (state.queueSheetOpen) bridge.refreshHeartQueue()
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .62f * progress))
                .tap { state.queueSheetOpen = false }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(.72f)
                .offset { IntOffset(0, ((1f - progress) * 900.dp.toPx()).toInt()) }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(AS.Card)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Txt("播放列表", AS.rowTitle().copy(fontSize = 17.sp))
                    Spacer(Modifier.height(2.dp))
                    Txt(
                        "共 ${state.playQueue.size} 首 · ${state.playMode.label}",
                        AS.caption().copy(fontSize = 11.sp)
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AS.CardElevated)
                        .tap { state.queueSheetOpen = false },
                    contentAlignment = Alignment.Center
                ) { Txt("✓", AS.body().copy(fontSize = 16.sp, color = AS.Blue)) }
            }
            if (state.playQueue.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Txt("从歌单里选一首歌，播放列表会显示在这里", AS.subtitle())
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.playQueue, key = { _, song -> song.id }) { index, song ->
                        val current = index == state.queueIndex &&
                            state.currentSong?.id == song.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .tap {
                                    bridge.playQueueIndex(index)
                                    state.queueSheetOpen = false
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))) {
                                CoverImage(song.coverUrl, song.id, Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(
                                    song.name,
                                    AS.rowTitle().copy(
                                        fontSize = 15.sp,
                                        color = if (current) AS.Blue else AS.Text
                                    ),
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(2.dp))
                                Txt(
                                    song.artist,
                                    AS.caption().copy(fontSize = 12.sp),
                                    maxLines = 1
                                )
                            }
                            if (song.id in state.heartRecommendedIds) {
                                Txt(
                                    "心动推荐",
                                    AS.caption().copy(fontSize = 10.sp, color = AS.Blue)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            if (current) {
                                Txt(
                                    if (state.playing) "播放中" else "当前歌曲",
                                    AS.caption().copy(fontSize = 11.sp, color = AS.Blue)
                                )
                            } else {
                                Txt(
                                    "${index + 1}",
                                    AS.caption().copy(fontSize = 11.sp, color = AS.Text3)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
