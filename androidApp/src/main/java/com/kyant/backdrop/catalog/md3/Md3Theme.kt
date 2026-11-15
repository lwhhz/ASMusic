package com.kyant.backdrop.catalog.md3

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// ---------------------------------------------------------------------------
// 色彩引擎
//
// M3 的配色来自「色调板（tonal palette）+ 语义角色」。这里不内联任何十六进制
// 品牌色：种子色 → OKLCH → 各角色取值，所以换种子、换风格、换对比度都能整体流动。
// 用 OKLCH 而不是 HSL：OKLCH 的 L 是感知亮度，等步长的 tone 看起来才均匀。
// ---------------------------------------------------------------------------

private data class Oklch(val l: Double, val c: Double, val h: Double)

private fun Color.toOklch(): Oklch {
    fun lin(v: Float): Double {
        val s = v.toDouble()
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    val r = lin(red)
    val g = lin(green)
    val b = lin(blue)
    val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    val l_ = Math.cbrt(l)
    val m_ = Math.cbrt(m)
    val s_ = Math.cbrt(s)
    val okL = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
    val okA = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
    val okB = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
    val chroma = kotlin.math.sqrt(okA * okA + okB * okB)
    var hue = Math.toDegrees(kotlin.math.atan2(okB, okA))
    if (hue < 0) hue += 360
    return Oklch(okL, chroma, hue)
}

/** 把 OKLCH 转回 sRGB；超出色域时逐步降 chroma（比直接 clamp 更接近 M3 的做法）。 */
private fun oklch(l: Double, c: Double, h: Double): Color {
    val lc = l.coerceIn(0.0, 1.0)
    var chroma = c.coerceAtLeast(0.0)
    fun enc(v: Double): Double = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055
    repeat(24) {
        val rad = Math.toRadians(h)
        val a = chroma * cos(rad)
        val b = chroma * sin(rad)
        val l_ = lc + 0.3963377774 * a + 0.2158037573 * b
        val m_ = lc - 0.1055613458 * a - 0.0638541728 * b
        val s_ = lc - 0.0894841775 * a - 1.2914855480 * b
        val ll = l_ * l_ * l_
        val mm = m_ * m_ * m_
        val ss = s_ * s_ * s_
        val r = 4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss
        val g = -1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss
        val bl = -0.0041960863 * ll - 0.7034186147 * mm + 1.7076147010 * ss
        val rr = enc(r)
        val gg = enc(g)
        val bb = enc(bl)
        if (rr in -0.001..1.001 && gg in -0.001..1.001 && bb in -0.001..1.001) {
            return Color(
                rr.coerceIn(0.0, 1.0).toFloat(),
                gg.coerceIn(0.0, 1.0).toFloat(),
                bb.coerceIn(0.0, 1.0).toFloat()
            )
        }
        chroma *= 0.92
    }
    val v = enc(lc).coerceIn(0.0, 1.0).toFloat()
    return Color(v, v, v)
}

/** 主色板：给定色相与饱和度上限，取任意 tone（0=黑, 100=白）。 */
private class Palette(val hue: Double, val chroma: Double) {
    fun tone(tone: Float): Color {
        // M3 的 tone 是 CIELAB L*；OKLCH 的 L 与之接近，直接线性映射够用。
        val l = (tone / 100.0).coerceIn(0.0, 1.0)
        return oklch(l, chroma, hue)
    }
}

private data class SeedSpec(
    val primaryChrom: Double,
    val secondaryHueShift: Double,
    val secondaryChrom: Double,
    val tertiaryHueShift: Double,
    val tertiaryChrom: Double,
    val neutralChrom: Double,
    val neutralVariantChrom: Double
)

private fun seedSpecOf(style: SchemeStyle): SeedSpec = when (style) {
    SchemeStyle.TonalSpot -> SeedSpec(.13, 12.0, .052, 60.0, .084, .006, .014)
    SchemeStyle.Vibrant -> SeedSpec(.21, 24.0, .105, 48.0, .155, .012, .026)
    SchemeStyle.Expressive -> SeedSpec(.17, -48.0, .082, 105.0, .13, .009, .02)
    SchemeStyle.Neutral -> SeedSpec(.055, 8.0, .03, 40.0, .045, .004, .01)
    SchemeStyle.Monochrome -> SeedSpec(.0, 0.0, .0, 0.0, .0, .0, .0)
    SchemeStyle.Rainbow -> SeedSpec(.185, 115.0, .125, 235.0, .14, .011, .024)
}

/**
 * 由种子色生成完整 ColorScheme。contrast 0→标准，1→高对比。
 * 明暗两套共用同一套 tone 表，只是取值区间不同（M3 的做法）。
 */
fun schemeFromSeed(
    seed: Color,
    dark: Boolean,
    style: SchemeStyle,
    contrast: Float,
    pureBlack: Boolean
): ColorScheme {
    val base = seed.toOklch()
    // 种子几乎没有彩度时（纯灰）退回一个中性色相，避免色相抖动导致串色。
    val hue = if (base.c < 0.01) 264.0 else base.h
    val spec = seedSpecOf(style)
    val c = contrast.coerceIn(0f, 1f)

    val primary = Palette(hue, spec.primaryChrom)
    val secondary = Palette(hue + spec.secondaryHueShift, spec.secondaryChrom)
    val tertiary = Palette(hue + spec.tertiaryHueShift, spec.tertiaryChrom)
    val neutral = Palette(hue, spec.neutralChrom)
    val neutralVariant = Palette(hue, spec.neutralVariantChrom)
    val errorPal = Palette(28.0, .17)

    return if (!dark) {
        val surfaceTone = 99f - 3f * c
        lightColorScheme(
            primary = primary.tone(40f - 12f * c),
            onPrimary = primary.tone(100f),
            primaryContainer = primary.tone(90f + 5f * c),
            onPrimaryContainer = primary.tone(12f - 6f * c),
            inversePrimary = primary.tone(80f),
            secondary = secondary.tone(40f - 10f * c),
            onSecondary = secondary.tone(100f),
            secondaryContainer = secondary.tone(92f + 4f * c),
            onSecondaryContainer = secondary.tone(12f - 6f * c),
            tertiary = tertiary.tone(38f - 10f * c),
            onTertiary = tertiary.tone(100f),
            tertiaryContainer = tertiary.tone(90f + 5f * c),
            onTertiaryContainer = tertiary.tone(12f - 6f * c),
            error = errorPal.tone(38f - 8f * c),
            onError = Color.White,
            errorContainer = errorPal.tone(90f + 4f * c),
            onErrorContainer = errorPal.tone(12f - 4f * c),
            background = neutral.tone(surfaceTone),
            onBackground = neutral.tone(12f - 6f * c),
            surface = neutral.tone(surfaceTone),
            onSurface = neutral.tone(12f - 6f * c),
            surfaceVariant = neutralVariant.tone(90f),
            onSurfaceVariant = neutralVariant.tone(32f - 8f * c),
            surfaceTint = primary.tone(40f),
            inverseSurface = neutral.tone(20f),
            inverseOnSurface = neutral.tone(95f),
            outline = neutralVariant.tone(50f - 8f * c),
            outlineVariant = neutralVariant.tone(82f - 6f * c),
            scrim = Color.Black,
            surfaceBright = neutral.tone(99f),
            surfaceDim = neutral.tone(87f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = neutral.tone(96f),
            surfaceContainer = neutral.tone(94f),
            surfaceContainerHigh = neutral.tone(92f),
            surfaceContainerHighest = neutral.tone(90f)
        )
    } else {
        val surfaceTone = if (pureBlack) 0f else 6f
        val cLow = if (pureBlack) 3f else 10f
        darkColorScheme(
            primary = primary.tone(80f + 8f * c),
            onPrimary = primary.tone(24f - 8f * c),
            primaryContainer = primary.tone(32f - 6f * c),
            onPrimaryContainer = primary.tone(90f + 6f * c),
            inversePrimary = primary.tone(40f),
            secondary = secondary.tone(80f + 6f * c),
            onSecondary = secondary.tone(24f - 6f * c),
            secondaryContainer = secondary.tone(30f - 4f * c),
            onSecondaryContainer = secondary.tone(90f + 6f * c),
            tertiary = tertiary.tone(80f + 6f * c),
            onTertiary = tertiary.tone(24f - 6f * c),
            tertiaryContainer = tertiary.tone(30f - 4f * c),
            onTertiaryContainer = tertiary.tone(90f + 6f * c),
            error = errorPal.tone(80f + 4f * c),
            onError = errorPal.tone(20f),
            errorContainer = errorPal.tone(30f),
            onErrorContainer = errorPal.tone(90f),
            background = neutral.tone(surfaceTone),
            onBackground = neutral.tone(90f + 6f * c),
            surface = neutral.tone(surfaceTone),
            onSurface = neutral.tone(90f + 6f * c),
            surfaceVariant = neutralVariant.tone(28f),
            onSurfaceVariant = neutralVariant.tone(78f + 8f * c),
            surfaceTint = primary.tone(80f),
            inverseSurface = neutral.tone(90f),
            inverseOnSurface = neutral.tone(20f),
            outline = neutralVariant.tone(62f + 8f * c),
            outlineVariant = neutralVariant.tone(28f - 4f * c),
            scrim = Color.Black,
            surfaceBright = neutral.tone(if (pureBlack) 30f else 24f),
            surfaceDim = neutral.tone(surfaceTone),
            surfaceContainerLowest = neutral.tone(if (pureBlack) 0f else 4f),
            surfaceContainerLow = neutral.tone(cLow),
            surfaceContainer = neutral.tone(cLow + 4f),
            surfaceContainerHigh = neutral.tone(cLow + 8f),
            surfaceContainerHighest = neutral.tone(cLow + 12f)
        )
    }
}

// ---------------------------------------------------------------------------
// 排版 / 形状 / 品牌令牌
// ---------------------------------------------------------------------------

/** M3 15 档字阶；Expressive 风格整体加大 display/headline 并收紧字距。 */
fun md3Typography(scale: Float, expressive: Boolean): Typography {
    fun s(size: Float, line: Float, weight: FontWeight, tracking: Float = 0f) = TextStyle(
        fontSize = (size * scale).sp,
        lineHeight = (line * scale).sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
        fontFamily = FontFamily.SansSerif
    )
    val bump = if (expressive) 1.12f else 1f
    return Typography(
        displayLarge = s(57f * bump, 64f, FontWeight.Normal, -0.25f),
        displayMedium = s(45f * bump, 52f, FontWeight.Normal),
        displaySmall = s(36f * bump, 44f, FontWeight.Normal),
        headlineLarge = s(32f * bump, 40f, FontWeight.SemiBold, -0.2f),
        headlineMedium = s(28f * bump, 36f, FontWeight.SemiBold),
        headlineSmall = s(24f * bump, 32f, FontWeight.SemiBold),
        titleLarge = s(22f, 28f, FontWeight.SemiBold),
        titleMedium = s(16f, 24f, FontWeight.Medium, 0.1f),
        titleSmall = s(14f, 20f, FontWeight.Medium),
        bodyLarge = s(16f, 24f, FontWeight.Normal, 0.15f),
        bodyMedium = s(14f, 20f, FontWeight.Normal, 0.2f),
        bodySmall = s(12f, 16f, FontWeight.Normal, 0.3f),
        labelLarge = s(14f, 20f, FontWeight.Medium, 0.1f),
        labelMedium = s(12f, 16f, FontWeight.Medium, 0.4f),
        labelSmall = s(11f, 16f, FontWeight.Medium, 0.5f)
    )
}

/** 圆角档位由「圆润度」滑块驱动：0 → 近乎直角，100 → 非常圆（Expressive）。 */
fun md3Shapes(roundness: Int): Shapes {
    val k = 0.25f + (roundness.coerceIn(0, 100) / 100f) * 1.85f
    return Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape((4 * k).dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape((8 * k).dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape((12 * k).dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape((16 * k).dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape((28 * k).dp)
    )
}

/** 布局密度与动效偏好，用 CompositionLocal 下发给组件，避免层层传参。 */
data class Md3Tokens(
    val density: Float,
    val motion: MotionLevel,
    val config: Md3Config?,
    val wide: Boolean
) {
    /** 默认间距乘以密度系数。 */
    fun gap(dp: Int) = (dp * density).dp
    val spring: androidx.compose.animation.core.FiniteAnimationSpec<Float>
        get() = when (motion) {
            MotionLevel.Expressive -> androidx.compose.animation.core.spring(
                dampingRatio = 0.52f, stiffness = 260f
            )
            MotionLevel.Standard -> androidx.compose.animation.core.spring(
                dampingRatio = 0.78f, stiffness = 420f
            )
            MotionLevel.Off -> androidx.compose.animation.core.tween(0)
        }
}

val LocalMd3Tokens = staticCompositionLocalOf {
    Md3Tokens(1f, MotionLevel.Expressive, null, false)
}

val LocalMd3Seed = staticCompositionLocalOf { Color(0xFF6750A4) }

/** 种子色的默认值与预设色板（都是「颜色」不是品牌色，可任意替换）。 */
val Md3PresetSeeds = listOf(
    "紫罗兰" to Color(0xFF6750A4),
    "海松" to Color(0xFF00696D),
    "湖泊" to Color(0xFF3A5F8A),
    "苔原" to Color(0xFF4C6A3C),
    "麦田" to Color(0xFF8A6D1F),
    "赤陶" to Color(0xFF9A4A2E),
    "玫红" to Color(0xFF9C3F5B),
    "暮紫" to Color(0xFF6E4A8E),
    "石墨" to Color(0xFF4A4A4F),
    "晴空" to Color(0xFF2F6FB0)
)

/** 把封面主色当作种子：取色板里饱和度最高的那一档，避免拿到接近灰的色。 */
fun pickSeedFromPalette(colors: List<Int>): Color? {
    if (colors.isEmpty()) return null
    val best = colors.maxByOrNull { argb ->
        val c = Color(argb.toLong() and 0xFFFFFFFFL)
        c.toOklch().c
    } ?: return null
    return Color(best.toLong() and 0xFFFFFFFFL)
}

// ---------------------------------------------------------------------------
// 主题
// ---------------------------------------------------------------------------

@Composable
fun Md3Theme(
    config: Md3Config,
    wide: Boolean,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val bridge = MusicServices.bridge
    val state = MusicServices.state

    var paletteSeed by remember { mutableStateOf<List<Int>>(emptyList()) }
    val coverUrl = state.currentSong?.coverUrl
    LaunchedEffect(coverUrl, config.seedSource) {
        if (config.seedSource == SeedSource.Album && !coverUrl.isNullOrBlank()) {
            paletteSeed = runCatching { bridge.coverPalette(coverUrl) }.getOrNull().orEmpty()
        }
    }

    val seed = when (config.seedSource) {
        SeedSource.Album -> pickSeedFromPalette(paletteSeed) ?: Md3PresetSeeds[0].second
        SeedSource.Manual -> if (config.seedColor != 0) Color(config.seedColor) else Md3PresetSeeds[0].second
        SeedSource.Preset -> if (config.seedColor != 0) Color(config.seedColor) else Md3PresetSeeds[0].second
    }

    val dark = when (config.darkMode) {
        DarkMode.System -> systemDark
        DarkMode.Light -> false
        DarkMode.Dark -> true
    }

    val scheme = remember(seed, dark, config.schemeStyle, config.contrast, config.pureBlack) {
        schemeFromSeed(seed, dark, config.schemeStyle, config.contrast, config.pureBlack)
    }
    val typography = remember(config.fontScale, config.motion) {
        md3Typography(config.fontScale, config.motion == MotionLevel.Expressive)
    }
    val shapes = remember(config.roundness) { md3Shapes(config.roundness) }
    val tokens = remember(config.density, config.motion, config, wide) {
        Md3Tokens(config.density, config.motion, config, wide)
    }

    CompositionLocalProvider(
        LocalMd3Tokens provides tokens,
        LocalMd3Seed provides seed
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
            content()
        }
    }
}
