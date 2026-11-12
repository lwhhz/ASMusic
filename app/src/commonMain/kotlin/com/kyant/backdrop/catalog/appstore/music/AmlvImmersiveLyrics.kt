package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.catalog.utils.BackHandler

/** Typography / interaction options of the immersive (AMLL) lyric view. */
@Immutable
data class LyricTextStyle(
    val fontSize: Float = 34f,
    val fontWeight: Float = 700f,
    /** 0 = system, 1 = sans, 2 = serif, 3 = monospace, 4 = rounded. */
    val fontFamily: Int = 0,
    val maskProfanity: Boolean = true,
    /** Seconds the view keeps the user's scroll before snapping back to the player. */
    val resumeDelaySec: Float = 3f
)

/**
 * Full-screen immersive lyric view. The Android implementation is an AMLL WebView
 * (Apple Music-like Lyrics, AGPL-3.0) with a Pixi background.
 */
@Composable
expect fun AmlvImmersiveLyrics(
    songId: Long,
    coverUrl: String?,
    lines: List<LyricLine>,
    positionMs: Long,
    playing: Boolean,
    style: LyricTextStyle,
    onSeek: (Long) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
)

@Composable
fun ImmersiveLyricsPage(
    song: Song,
    lines: List<LyricLine>,
    positionMs: Long,
    playing: Boolean,
    style: LyricTextStyle,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)
    // The AMLL surface itself is hosted at the app root (so its Pixi background is
    // already warm); this page only provides the backdrop and the back handling.
    Box(modifier.fillMaxSize().background(Color.Black))
}
