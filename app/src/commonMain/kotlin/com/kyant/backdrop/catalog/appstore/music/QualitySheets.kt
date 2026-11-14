package com.kyant.backdrop.catalog.appstore.music

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/** Per-song quality chooser, opened from the right of the next button. */
@Composable
fun QualitySheet(visible: Boolean, onDismiss: () -> Unit) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    val progress by animateFloatAsState(
        if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = .92f, stiffness = 380f)
    )
    if (progress < .001f) return
    LaunchedEffect(state.currentSong?.id, visible) {
        if (visible) {
            state.currentSong?.id?.let { runCatching { bridge.songQualities(it) } }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .60f * progress))
                .tap(onDismiss)
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(.56f)
                .offset { IntOffset(0, ((1f - progress) * 900.dp.toPx()).toInt()) }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(AS.Card)
                .padding(horizontal = 16.dp)
        ) {
            SheetTitle("本曲音质", "当前歌曲可选音质", onDismiss)
            if (state.availableQualities.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Txt("正在获取这首歌的音质…", AS.subtitle())
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.availableQualities.sortedByDescending { it.rank }.forEach { q ->
                        val active = state.currentQuality?.level == q.level
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) AS.Blue.copy(alpha = .20f) else AS.CardElevated)
                                .tap {
                                    bridge.setPlaybackQuality(q.level)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Txt(q.label, AS.rowTitle().copy(fontSize = 15.sp))
                                Txt(
                                    if (q.bitrate > 0) "${q.bitrate / 1000} kbps" else "高品质",
                                    AS.caption().copy(fontSize = 11.sp)
                                )
                            }
                            if (!q.playable) {
                                Txt("VIP", AS.caption().copy(fontSize = 10.sp, color = AS.Orange))
                                Spacer(Modifier.width(8.dp))
                            }
                            Txt(
                                q.shortLabel,
                                AS.caption().copy(
                                    fontSize = 11.sp,
                                    color = if (active) AS.Blue else AS.Text3
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

/** WiFi / mobile default ceilings in the settings page. */
@Composable
fun QualitySettingsSheet(visible: Boolean, onDismiss: () -> Unit) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    val progress by animateFloatAsState(
        if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = .92f, stiffness = 380f)
    )
    if (progress < .001f) return
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .60f * progress))
                .tap(onDismiss)
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(.86f)
                .offset { IntOffset(0, ((1f - progress) * 1000.dp.toPx()).toInt()) }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(AS.Card)
                .padding(horizontal = 16.dp)
        ) {
            SheetTitle("默认音质", "分别设置 WiFi 与移动数据下的默认最高音质", onDismiss)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                QualityPrefSection(
                    title = "WiFi 下默认最高音质",
                    selected = state.wifiQuality,
                    onSelect = { bridge.setWifiQuality(it) }
                )
                Spacer(Modifier.height(22.dp))
                QualityPrefSection(
                    title = "移动数据默认最高音质",
                    selected = state.mobileQuality,
                    onSelect = { bridge.setMobileQuality(it) }
                )
                Spacer(Modifier.height(18.dp))
                Txt(
                    "实际播放会按歌曲提供的音质取不超过该档位的最高可播版本；账号无权限时自动回退并提示。",
                    AS.caption()
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun QualityPrefSection(
    title: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    Txt(title, AS.caption().copy(color = AS.Text3), Modifier.padding(start = 4.dp, bottom = 8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AS.CardElevated)
    ) {
        AudioQuality.entries.forEachIndexed { index, quality ->
            if (index > 0) {
                Box(Modifier.padding(start = 14.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .tap { onSelect(quality.level) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt(quality.label, AS.rowTitle().copy(fontSize = 15.sp), Modifier.weight(1f))
                Txt(quality.shortLabel, AS.caption().copy(fontSize = 11.sp, color = AS.Text3))
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(if (selected == quality.level) AS.Blue else Color.Transparent)
                ) {
                    if (selected == quality.level) {
                        Txt(
                            "✓",
                            AS.caption().copy(fontSize = 11.sp, color = Color.White),
                            Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, AS.rowTitle().copy(fontSize = 18.sp))
            Spacer(Modifier.height(2.dp))
            Txt(subtitle, AS.caption().copy(fontSize = 11.sp))
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .10f)).tap(onDismiss),
            contentAlignment = Alignment.Center
        ) { Txt("✕", AS.caption().copy(fontSize = 13.sp, color = Color.White.copy(.8f))) }
    }
}
