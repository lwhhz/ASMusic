package com.kyant.backdrop.catalog.appstore.music

/** 分享海报的背景样式。 */
enum class PosterBg { Blur, Gradient, Solid }

/** 导出尺寸；默认 9:16。 */
enum class PosterRatio(val widthPx: Int, val heightPx: Int, val label: String) {
    Portrait3x4(1080, 1440, "3:4"),
    Story9x16(1080, 1920, "9:16"),
    Square1x1(1080, 1080, "1:1")
}

/** 分享海报的全部可调项；值来自设置页，持久化在 AppConfig。 */
data class PosterConfig(
    val bg: PosterBg = PosterBg.Blur,
    /** 1 = 轻微, 2 = 标准, 3 = 重度 */
    val blurLevel: Int = 2,
    val ratio: PosterRatio = PosterRatio.Story9x16,
    /** 1 / 2 / 4 行 */
    val lyricLines: Int = 4,
    /** 0.85 / 1.0 / 1.15 */
    val fontScale: Float = 1f,
    val showTranslation: Boolean = true,
    val showInfoCard: Boolean = true,
    val showAvatar: Boolean = true,
    val showAlbum: Boolean = true,
    val watermark: String = " By · ASMusic"
)
