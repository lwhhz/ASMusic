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
    /**
     * Index into the system font-family list (0..7):
     * 默认 / 细体 / 紧缩 / 中黑 / 衬线 / 等宽 / 手写 / 圆润。
     */
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
fun ImmersiveLyricsPage(modifier: Modifier = Modifier) {
    // The AMLL surface itself is hosted at the app root (so its Pixi background is
    // already warm); this page only provides the black backdrop.
    //
    // 返回键统一由 MusicApp 的导航栈处理（返回 = 出栈），这里不再单独注册 BackHandler：
    // 两个 BackHandler 同时 enabled 时，桌面端的 actual 实现会画出两个重合的返回按钮。
    Box(modifier.fillMaxSize().background(Color.Black))
}
