package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun AmlvImmersiveLyrics(
    songId: Long,
    coverUrl: String?,
    lines: List<LyricLine>,
    positionMs: Long,
    playing: Boolean,
    style: LyricTextStyle,
    onSeek: (Long) -> Unit,
    visible: Boolean,
    modifier: Modifier
) {
    Box(modifier.fillMaxSize().background(Color.Black))
}
