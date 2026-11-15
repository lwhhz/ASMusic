package com.kyant.backdrop.catalog.md3

import android.content.Context

/** 界面风格：两套 UI 各自独立，切换后重启生效。 */
enum class UiStyle(val label: String, val desc: String) {
    Ios("经典（iOS）", "毛玻璃、胶囊、大字标题的原有界面"),
    Md3("Material 3", "Material 3 Expressive：语义色、动态取色、弹簧动效");

    companion object {
        fun from(key: String?): UiStyle = entries.firstOrNull { it.name.equals(key, true) } ?: Ios
    }
}

/** 配色风格，对应 M3 的 scheme variant。 */
enum class SchemeStyle(val label: String) {
    TonalSpot("柔和（Tonal Spot）"),
    Vibrant("鲜艳（Vibrant）"),
    Expressive("表现力（Expressive）"),
    Neutral("中性（Neutral）"),
    Monochrome("单色（Monochrome）"),
    Rainbow("多彩（Rainbow）");

    companion object {
        fun from(i: Int): SchemeStyle = entries.getOrElse(i) { TonalSpot }
    }
}

/** 取色来源：跟随封面 / 手动指定 / 固定预设。 */
enum class SeedSource(val label: String) {
    Album("跟随封面"),
    Manual("手动选色"),
    Preset("色板预设");

    companion object {
        fun from(i: Int): SeedSource = entries.getOrElse(i) { Album }
    }
}

enum class DarkMode(val label: String) {
    System("跟随系统"), Light("常亮"), Dark("常暗");

    companion object {
        fun from(i: Int): DarkMode = entries.getOrElse(i) { System }
    }
}

enum class MotionLevel(val label: String) {
    Standard("标准"), Expressive("表达力"), Off("关闭");

    companion object {
        fun from(i: Int): MotionLevel = entries.getOrElse(i) { Standard }
    }
}

enum class NavLayout(val label: String) {
    Bar("底部导航栏"), Rail("侧边导航栏（宽屏）"), Auto("自动（按宽度）");

    companion object {
        fun from(i: Int): NavLayout = entries.getOrElse(i) { Auto }
    }
}

enum class HomeLayout(val label: String) {
    Cards("大卡片"), List("紧凑列表"), Grid("双列网格");

    companion object {
        fun from(i: Int): HomeLayout = entries.getOrElse(i) { Cards }
    }
}

enum class DetailLayout(val label: String) {
    LyricsFirst("歌词优先"), CoverFirst("封面优先");

    companion object {
        fun from(i: Int): DetailLayout = entries.getOrElse(i) { LyricsFirst }
    }
}

/**
 * MD3 界面专属设置。
 *
 * 刻意独立成一个 SharedPreferences 文件：两套 UI 的设置互不影响，
 * 切回 iOS 风格时这里的值原样保留。
 */
class Md3Config(context: Context) {

    private val prefs = context.getSharedPreferences("md3_app", Context.MODE_PRIVATE)

    // ---- 主题 ----
    var schemeStyle: SchemeStyle
        get() = SchemeStyle.from(prefs.getInt("scheme_style", 0))
        set(v) = prefs.edit().putInt("scheme_style", v.ordinal).apply()

    var seedSource: SeedSource
        get() = SeedSource.from(prefs.getInt("seed_source", 0))
        set(v) = prefs.edit().putInt("seed_source", v.ordinal).apply()

    /** 手动种子的 ARGB。0 表示未设置。 */
    var seedColor: Int
        get() = prefs.getInt("seed_color", 0)
        set(v) = prefs.edit().putInt("seed_color", v).apply()

    var contrast: Float
        get() = prefs.getFloat("contrast", 0f)
        set(v) = prefs.edit().putFloat("contrast", v.coerceIn(0f, 1f)).apply()

    var darkMode: DarkMode
        get() = DarkMode.from(prefs.getInt("dark_mode", 0))
        set(v) = prefs.edit().putInt("dark_mode", v.ordinal).apply()

    var pureBlack: Boolean
        get() = prefs.getBoolean("pure_black", false)
        set(v) = prefs.edit().putBoolean("pure_black", v).apply()

    var roundness: Int
        get() = prefs.getInt("roundness", 70)
        set(v) = prefs.edit().putInt("roundness", v.coerceIn(0, 100)).apply()

    var density: Float
        get() = prefs.getFloat("density", 1f)
        set(v) = prefs.edit().putFloat("density", v.coerceIn(.85f, 1.2f)).apply()

    var fontScale: Float
        get() = prefs.getFloat("font_scale", 1f)
        set(v) = prefs.edit().putFloat("font_scale", v.coerceIn(.85f, 1.25f)).apply()

    var motion: MotionLevel
        get() = MotionLevel.from(prefs.getInt("motion", 1))
        set(v) = prefs.edit().putInt("motion", v.ordinal).apply()

    // ---- 布局 ----
    var navLayout: NavLayout
        get() = NavLayout.from(prefs.getInt("nav_layout", 2))
        set(v) = prefs.edit().putInt("nav_layout", v.ordinal).apply()

    var homeLayout: HomeLayout
        get() = HomeLayout.from(prefs.getInt("home_layout", 0))
        set(v) = prefs.edit().putInt("home_layout", v.ordinal).apply()

    var detailLayout: DetailLayout
        get() = DetailLayout.from(prefs.getInt("detail_layout", 0))
        set(v) = prefs.edit().putInt("detail_layout", v.ordinal).apply()

    var showMiniPlayer: Boolean
        get() = prefs.getBoolean("mini_player", true)
        set(v) = prefs.edit().putBoolean("mini_player", v).apply()

    var showLyricTranslation: Boolean
        get() = prefs.getBoolean("lyric_translation", true)
        set(v) = prefs.edit().putBoolean("lyric_translation", v).apply()

    var coverCorner: Boolean
        get() = prefs.getBoolean("cover_corner", true)
        set(v) = prefs.edit().putBoolean("cover_corner", v).apply()

    /** 首页顶部问候语：用户名 / 时段问候 / 关闭。 */
    var greeting: Int
        get() = prefs.getInt("greeting", 1)
        set(v) = prefs.edit().putInt("greeting", v).apply()

    /** 记住上一次的页面（切回来时恢复）。 */
    var rememberTab: Boolean
        get() = prefs.getBoolean("remember_tab", true)
        set(v) = prefs.edit().putBoolean("remember_tab", v).apply()

    // ---- 播放页 ----
    var breathingCover: Boolean
        get() = prefs.getBoolean("breathing_cover", true)
        set(v) = prefs.edit().putBoolean("breathing_cover", v).apply()

    var showWaveform: Boolean
        get() = prefs.getBoolean("waveform", true)
        set(v) = prefs.edit().putBoolean("waveform", v).apply()

    companion object {
        const val STYLE_KEY = "ui_style"

        fun readStyle(context: Context): UiStyle {
            val prefs = context.getSharedPreferences("music_app", Context.MODE_PRIVATE)
            return UiStyle.from(prefs.getString(STYLE_KEY, UiStyle.Ios.name))
        }

        fun writeStyle(context: Context, style: UiStyle) {
            // commit 而不是 apply：写完之后进程就要被重启，异步落盘会丢。
            context.getSharedPreferences("music_app", Context.MODE_PRIVATE)
                .edit().putString(STYLE_KEY, style.name).commit()
        }
    }
}
