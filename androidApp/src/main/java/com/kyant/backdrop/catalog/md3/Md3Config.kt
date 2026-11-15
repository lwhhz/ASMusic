package com.kyant.backdrop.catalog.md3

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/** 界面风格：两套 UI 各自独立，切换后重启生效。 */
enum class UiStyle(val label: String, val desc: String) {
    Ios("经典（iOS）", "毛玻璃、胶囊、大字标题的原有界面"),
    Md3("Material 3", "Material 3 Expressive：语义色、动态取色、弹簧动效");

    companion object {
        fun from(key: String?): UiStyle = entries.firstOrNull { it.name.equals(key, true) } ?: Ios
    }
}

/** 配色风格，对应 M3 的 scheme variant。 */
enum class SchemeStyle(val label: String, val hint: String) {
    TonalSpot("柔和", "低彩度、层次平稳，默认"),
    Vibrant("鲜艳", "高彩度强调色"),
    Expressive("表现力", "色相偏移更大，色块更跳"),
    Neutral("中性", "接近灰阶，只留一点色"),
    Monochrome("单色", "完全无色相"),
    Rainbow("多彩", "primary/secondary/tertiary 各走一个色相");

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

enum class MotionLevel(val label: String, val hint: String) {
    Standard("标准", "阻尼 0.78，收敛快"),
    Expressive("表达力", "阻尼 0.52，按下与展开带回弹"),
    Off("关闭", "只做必要的淡入淡出");

    companion object {
        fun from(i: Int): MotionLevel = entries.getOrElse(i) { Standard }
    }
}

enum class NavLayout(val label: String) {
    Bar("底部导航栏"), Rail("侧边导航轨"), Auto("自动（按宽度）");

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

/** 标题栏形态：大标题随滚动收起 / 一直紧凑。 */
enum class HeaderStyle(val label: String) {
    Large("大标题"), Compact("紧凑");

    companion object {
        fun from(i: Int): HeaderStyle = entries.getOrElse(i) { Large }
    }
}

/** 播放页进度条：波形（Expressive）/ 直条。 */
enum class SeekStyle(val label: String) {
    Wavy("波形"), Linear("直线");

    companion object {
        fun from(i: Int): SeekStyle = entries.getOrElse(i) { Wavy }
    }
}

/**
 * MD3 界面专属设置。
 *
 * 两个要点：
 * 1. **每一项都是 Compose 快照状态**。之前用 prefs 直接读写，改值不会触发重组，
 *    表现就是「调了滑杆界面没反应、要重启才生效」。现在 get 读快照、set 同时落盘。
 * 2. 独立成一个 SharedPreferences 文件：两套 UI 的设置互不影响，
 *    切回 iOS 风格时这里的值原样保留。
 */
class Md3Config(context: Context) {

    private val prefs = context.getSharedPreferences("md3_app", Context.MODE_PRIVATE)

    private inner class IntSetting(private val key: String, def: Int, private val valid: IntRange) {
        private val state = mutableStateOf(prefs.getInt(key, def).coerceIn(valid))
        fun read(): Int = state.value
        fun write(v: Int) {
            val c = v.coerceIn(valid)
            if (state.value == c) return
            state.value = c
            prefs.edit().putInt(key, c).apply()
        }
    }

    private inner class FloatSetting(private val key: String, def: Float, private val valid: ClosedFloatingPointRange<Float>) {
        private val state = mutableStateOf(prefs.getFloat(key, def).coerceIn(valid))
        fun read(): Float = state.value
        fun write(v: Float) {
            val c = v.coerceIn(valid)
            if (state.value == c) return
            state.value = c
            prefs.edit().putFloat(key, c).apply()
        }
    }

    private inner class BoolSetting(private val key: String, def: Boolean) {
        private val state = mutableStateOf(prefs.getBoolean(key, def))
        fun read(): Boolean = state.value
        fun write(v: Boolean) {
            if (state.value == v) return
            state.value = v
            prefs.edit().putBoolean(key, v).apply()
        }
    }

    // ---- 主题 ----
    private val schemeStyleS = IntSetting("scheme_style", SchemeStyle.TonalSpot.ordinal, 0..5)
    private val seedSourceS = IntSetting("seed_source", SeedSource.Album.ordinal, 0..2)
    private val seedColorS = IntSetting("seed_color", 0, Int.MIN_VALUE..Int.MAX_VALUE)
    private val contrastS = FloatSetting("contrast", 0f, 0f..1f)
    private val darkModeS = IntSetting("dark_mode", DarkMode.System.ordinal, 0..2)
    private val pureBlackS = BoolSetting("pure_black", false)

    // ---- 形状 · 排版 · 动效 ----
    private val roundnessS = IntSetting("roundness", 70, 0..100)
    private val densityS = FloatSetting("density", 1f, .85f..1.2f)
    private val fontScaleS = FloatSetting("font_scale", 1f, .85f..1.25f)
    private val motionS = IntSetting("motion", MotionLevel.Expressive.ordinal, 0..2)
    private val hapticsS = BoolSetting("haptics", true)

    // ---- 布局 ----
    private val navLayoutS = IntSetting("nav_layout", NavLayout.Auto.ordinal, 0..2)
    private val homeLayoutS = IntSetting("home_layout", HomeLayout.Cards.ordinal, 0..2)
    private val detailLayoutS = IntSetting("detail_layout", DetailLayout.LyricsFirst.ordinal, 0..1)
    private val headerStyleS = IntSetting("header_style", HeaderStyle.Large.ordinal, 0..1)
    private val miniPlayerS = BoolSetting("mini_player", true)
    private val lyricTranslationS = BoolSetting("lyric_translation", true)
    private val coverCornerS = BoolSetting("cover_corner", true)
    private val greetingS = IntSetting("greeting", 1, 0..2)
    private val rememberTabS = BoolSetting("remember_tab", true)
    private val tabIndexS = IntSetting("last_tab", 0, 0..4)

    // ---- 播放页 ----
    private val breathingCoverS = BoolSetting("breathing_cover", true)
    private val seekStyleS = IntSetting("seek_style", SeekStyle.Wavy.ordinal, 0..1)
    private val startLyricsS = BoolSetting("player_start_lyrics", false)
    private val showWaveformS = BoolSetting("waveform", true)

    var schemeStyle: SchemeStyle
        get() = SchemeStyle.from(schemeStyleS.read())
        set(v) = schemeStyleS.write(v.ordinal)

    var seedSource: SeedSource
        get() = SeedSource.from(seedSourceS.read())
        set(v) = seedSourceS.write(v.ordinal)

    /** 手动种子的 ARGB。0 表示未设置。 */
    var seedColor: Int
        get() = seedColorS.read()
        set(v) = seedColorS.write(v)

    var contrast: Float
        get() = contrastS.read()
        set(v) = contrastS.write(v)

    var darkMode: DarkMode
        get() = DarkMode.from(darkModeS.read())
        set(v) = darkModeS.write(v.ordinal)

    var pureBlack: Boolean
        get() = pureBlackS.read()
        set(v) = pureBlackS.write(v)

    var roundness: Int
        get() = roundnessS.read()
        set(v) = roundnessS.write(v)

    var density: Float
        get() = densityS.read()
        set(v) = densityS.write(v)

    var fontScale: Float
        get() = fontScaleS.read()
        set(v) = fontScaleS.write(v)

    var motion: MotionLevel
        get() = MotionLevel.from(motionS.read())
        set(v) = motionS.write(v.ordinal)

    var haptics: Boolean
        get() = hapticsS.read()
        set(v) = hapticsS.write(v)

    var navLayout: NavLayout
        get() = NavLayout.from(navLayoutS.read())
        set(v) = navLayoutS.write(v.ordinal)

    var homeLayout: HomeLayout
        get() = HomeLayout.from(homeLayoutS.read())
        set(v) = homeLayoutS.write(v.ordinal)

    var detailLayout: DetailLayout
        get() = DetailLayout.from(detailLayoutS.read())
        set(v) = detailLayoutS.write(v.ordinal)

    var headerStyle: HeaderStyle
        get() = HeaderStyle.from(headerStyleS.read())
        set(v) = headerStyleS.write(v.ordinal)

    var showMiniPlayer: Boolean
        get() = miniPlayerS.read()
        set(v) = miniPlayerS.write(v)

    var showLyricTranslation: Boolean
        get() = lyricTranslationS.read()
        set(v) = lyricTranslationS.write(v)

    var coverCorner: Boolean
        get() = coverCornerS.read()
        set(v) = coverCornerS.write(v)

    /** 首页顶部问候语：用户名 / 时段问候 / 关闭。 */
    var greeting: Int
        get() = greetingS.read()
        set(v) = greetingS.write(v)

    /** 记住上一次的标签页（切回来时恢复）。 */
    var rememberTab: Boolean
        get() = rememberTabS.read()
        set(v) = rememberTabS.write(v)

    var lastTab: Int
        get() = tabIndexS.read()
        set(v) = tabIndexS.write(v)

    var breathingCover: Boolean
        get() = breathingCoverS.read()
        set(v) = breathingCoverS.write(v)

    var seekStyle: SeekStyle
        get() = SeekStyle.from(seekStyleS.read())
        set(v) = seekStyleS.write(v.ordinal)

    var playerStartLyrics: Boolean
        get() = startLyricsS.read()
        set(v) = startLyricsS.write(v)

    var showWaveform: Boolean
        get() = showWaveformS.read()
        set(v) = showWaveformS.write(v)

    // -----------------------------------------------------------------------
    // 分组重置
    // -----------------------------------------------------------------------

    /** 外观组：配色 / 形状 / 排版 / 动效。 */
    fun resetAppearance() {
        schemeStyle = SchemeStyle.TonalSpot
        seedSource = SeedSource.Album
        seedColor = 0
        contrast = 0f
        darkMode = DarkMode.System
        pureBlack = false
        roundness = 70
        density = 1f
        fontScale = 1f
        motion = MotionLevel.Expressive
        haptics = true
    }

    /** 布局组。 */
    fun resetLayout() {
        navLayout = NavLayout.Auto
        homeLayout = HomeLayout.Cards
        detailLayout = DetailLayout.LyricsFirst
        headerStyle = HeaderStyle.Large
        showMiniPlayer = true
        coverCorner = true
        rememberTab = true
    }

    /** 播放组。 */
    fun resetPlayer() {
        breathingCover = true
        seekStyle = SeekStyle.Wavy
        playerStartLyrics = false
        showWaveform = true
        showLyricTranslation = true
    }

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
